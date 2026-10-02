package com.ontotext.trree.plugin.autocomplete;

import com.ontotext.trree.sdk.*;
import gnu.trove.map.hash.TLongObjectHashMap;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by desislava on 11/11/15.
 */
class AutocompleteUpdateListener implements EntityListener, PluginTransactionListener, StatementListener {

    private boolean newEntitiesAdded = false;

    private static final Logger LOGGER = LoggerFactory.getLogger(AutocompleteUpdateListener.class);

    private final AutocompletePlugin plugin;

    // If entities are added while index rebuilding, we just save them and add them afterwards
    private long firstEntityAddedWhileBuilding;

    private TLongObjectHashMap<String> labelsAddedWhileBuilding;

    private final Map<IRI, LabelConfig> unresolvedLabelConfigs;

    private boolean shutDownInitiated = false;
    public AutocompleteUpdateListener(AutocompletePlugin plugin) {
        this.plugin = plugin;
        this.labelsAddedWhileBuilding = new TLongObjectHashMap<>();
        this.unresolvedLabelConfigs = new HashMap<>();
    }

    @Override
    public void entityAdded(long id, Value value, PluginConnection pluginConnection) {
        if (shutDownInitiated || !plugin.isPluginEnabled || plugin.autocompleteIndex == null) {
        	return;
        }
        if (value instanceof IRI) {
            if (!unresolvedLabelConfigs.isEmpty()) {
                LabelConfig labelConfig = unresolvedLabelConfigs.get(value);
                if (labelConfig != null) {
                    synchronized (unresolvedLabelConfigs) {
                        labelConfig.labelId = id;
                        unresolvedLabelConfigs.remove(value);
                    }
                }
            }

            if (shutDownInitiated || !plugin.isPluginEnabled || !plugin.actualShouldIndexIRIs) {
                // Plugin disabled or IRIs should not be indexed
                return;
            }

            if (plugin.autocompleteIndex == null) {
                LOGGER.warn("Plugin index is empty");
                return;
            }

            if (plugin.autocompleteIndex.status() == IndexStatus.BUILDING) {
                if (firstEntityAddedWhileBuilding == 0) {
                    firstEntityAddedWhileBuilding = id;
                }
                //System.out.println("During build " + value);
            } else {
                //System.out.println("After build " + value);
                newEntitiesAdded = true;
                plugin.autocompleteIndex.index(id, (IRI) value);
            }
        }
    }



    @Override
    public void transactionCommit(PluginConnection pluginConnection) {
        checkForEntitiesAddedWhileBuilding(pluginConnection.getEntities());
        if (!newEntitiesAdded) {
            return;
        }
        if (plugin.autocompleteIndex == null) {
            return;
        }
        try {
            plugin.autocompleteIndex.commitAndRefresh();
        } catch (IOException e) {
            throw new PluginException("Could not commit autocomplete index", e);
        } finally {
            newEntitiesAdded = false;
        }

    }

    @Override
    public boolean statementAdded(long subject, long predicate, long object, long context, boolean explicit,
                                  PluginConnection pluginConnection) {
        for (LabelConfig labelConfig : plugin.labelConfigs) {
            if (predicate == labelConfig.labelId) {
                if (shutDownInitiated || !plugin.isPluginEnabled || plugin.autocompleteIndex == null) {
                    return false;
                }

                updateLabel(subject, object, labelConfig, pluginConnection.getEntities());
            }
        }
        return false;
    }

    @Override
    public boolean statementRemoved(long subject, long predicate, long object, long context, boolean explicit,
                                    PluginConnection pluginConnection) {
        for (LabelConfig labelConfig : plugin.labelConfigs) {
            if (predicate == labelConfig.labelId) {
                if (shutDownInitiated || !plugin.isPluginEnabled || plugin.autocompleteIndex == null) {
                    return false;
                }

                updateLabel(0, object, labelConfig, pluginConnection.getEntities());
            }
        }
        return false;
    }

    private void updateLabel(long subject, long object, LabelConfig labelConfig, Entities entities) {
        String language = entities.getLanguage(object);
        if (labelConfig.languageMatches(language)) {
            String label = entities.get(object).stringValue();
            if (plugin.autocompleteIndex.status() == IndexStatus.BUILDING) {
                labelsAddedWhileBuilding.put(subject, label);
            } else {
                newEntitiesAdded = true;
                plugin.autocompleteIndex.index(subject, label);
            }
        }
    }

    @Override
    public void transactionStarted(PluginConnection pluginConnection) {
    }

    @Override
    public void transactionCompleted(PluginConnection pluginConnection) {

    }

    @Override
    public void transactionAborted(PluginConnection pluginConnection) {
        if (!newEntitiesAdded) {
            return;
        }
        if (plugin.autocompleteIndex == null) {
            return;
        }
        try {
            plugin.autocompleteIndex.rollback();
        } catch (IOException e) {
            LOGGER.debug("Could not rollback autocomplete index when transaction was aborted", e);
        } finally {
            newEntitiesAdded = false;
        }
    }

    private void checkForEntitiesAddedWhileBuilding(Entities entities) {
        // Add the entities that were added while we were building
        if (firstEntityAddedWhileBuilding > 0) {
            while (firstEntityAddedWhileBuilding <= entities.size()) {
                Value value = entities.get(firstEntityAddedWhileBuilding);
                if (value instanceof IRI) {
                    newEntitiesAdded = true;
                    plugin.autocompleteIndex.index(firstEntityAddedWhileBuilding, (IRI) value);
                    // System.out.println("Index entity saved while building " + value);
                }

                firstEntityAddedWhileBuilding++;
            }
            // reset the tracking variable
            firstEntityAddedWhileBuilding = 0;
        }

        if (!labelsAddedWhileBuilding.isEmpty()) {
            labelsAddedWhileBuilding.forEachEntry((id, label) -> {
                plugin.autocompleteIndex.index(id, label); return true;
            });
            labelsAddedWhileBuilding = new TLongObjectHashMap<>();
        }
    }

    void notifyAboutLabelConfig(LabelConfig labelConfig) {
        if (labelConfig.labelId <= 0) {
            synchronized (unresolvedLabelConfigs) {
                unresolvedLabelConfigs.put(labelConfig.labelIRI, labelConfig);
            }
        }
    }
    
    void beforeShutDown() {
    	shutDownInitiated = true;
    }
}
