package com.ontotext.trree.plugin.autocomplete;

import com.ontotext.trree.sdk.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import static com.ontotext.trree.plugin.autocomplete.AutocompletePluginUtils.*;

/**
 * Created by desislava on 10/11/15.
 */
public class AutocompletePlugin extends PluginBase
        implements PatternInterpreter, UpdateInterpreter, PluginDependency, StatelessPlugin,
                    EntityListener, PluginTransactionListener, StatementListener, ClearInterpreter {
    private static final ValueFactory VF = SimpleValueFactory.getInstance();

    private static final int RESULTS_COUNT = 100;

    private static final String AUTOCOMPLETE_NAMESPACE = "http://www.ontotext.com/plugins/autocomplete#%s";
    private static final IRI AUTOCOMPLETE_CONTROL_CONTEXT = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "control"));
    private static final IRI AUTOCOMPLETE_QUERY_PREDICATE = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "query"));
    private static final IRI AUTOCOMPLETE_REINDEX_PREDICATE = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "reIndex"));
    private static final IRI AUTOCOMPLETE_ENABLED_PREDICATE = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "enabled"));
    private static final IRI AUTOCOMPLETE_INDEX_IRIS_PREDICATE = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "indexIRIs"));
    private static final IRI AUTOCOMPLETE_PRESENT_PREDICATE = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "present"));
    private static final IRI AUTOCOMPLETE_STATUS_PREDICATE = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "status"));
    private static final IRI AUTOCOMPLETE_INDEXING_INTERRUPT_PREDICATE = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "interruptIndexing"));
    private static final IRI AUTOCOMPLETE_LABEL_CONFIG = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "labelConfig"));
    private static final IRI AUTOCOMPLETE_ADD_LABEL_CONFIG = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "addLabelConfig"));
    private static final IRI AUTOCOMPLETE_REMOVE_LABEL_CONFIG = VF.createIRI(String.format(AUTOCOMPLETE_NAMESPACE, "removeLabelConfig"));

    private long controlContextId;
    private long queryPredicateId;
    private long reIndexPredicateId;
    private long enabledPredicateId;
    private long indexIRIsPredicateId;
    private long presentPredicateId;
    private long indexStatusPredicateId;
    private long interruptIndexingPredicateId;
    private long labelConfigId;
    private long addLabelConfigId;
    private long removeLabelConfigId;

    private Map<IRI, LabelConfig> configuredLabelConfigs;
    boolean configuredAndActualConfigsDiffer;
    LabelConfig[] labelConfigs;

    Properties pluginConfig;

    boolean isPluginEnabled;

    boolean configuredShouldIndexIRIs;

    boolean actualShouldIndexIRIs;

    AutocompleteIndex autocompleteIndex;

    private AutocompleteUpdateListener autocompleteUpdateLister;

    private PluginLocator pluginLocator;

    @Override
    public String getName() {
        return "autocomplete";
    }

    @Override
    public void initialize(InitReason initReason, PluginConnection pluginConnection) {
        AutocompletePluginUtils.migrateConfig(getDataDir(), getLogger());
        pluginConfig = loadConfig(getDataDir());

        isPluginEnabled = isPluginEnabledFromProperties(pluginConfig);
        configuredShouldIndexIRIs = configuredShouldIndexIRIsFromProperties(pluginConfig);
        actualShouldIndexIRIs = actualShouldIndexIRIsFromProperties(pluginConfig);
        configuredLabelConfigs = getConfiguredLabelsFromProperties(pluginConfig);
        labelConfigs = labelConfigMapToArray(getActualLabelsFromProperties(pluginConfig));
        
        updateConfiguredVsActualConfig();

        autocompleteUpdateLister = new AutocompleteUpdateListener(this);

        registerPredicates(pluginConnection.getEntities());

        if (isPluginEnabled) {
            initAutocompleteIndex();
        }
    }


    private void registerPredicates(Entities entities) {
        controlContextId = entities.put(AUTOCOMPLETE_CONTROL_CONTEXT, Entities.Scope.SYSTEM);
        queryPredicateId = entities.put(AUTOCOMPLETE_QUERY_PREDICATE, Entities.Scope.SYSTEM);
        reIndexPredicateId = entities.put(AUTOCOMPLETE_REINDEX_PREDICATE, Entities.Scope.SYSTEM);
        enabledPredicateId = entities.put(AUTOCOMPLETE_ENABLED_PREDICATE, Entities.Scope.SYSTEM);
        indexIRIsPredicateId = entities.put(AUTOCOMPLETE_INDEX_IRIS_PREDICATE, Entities.Scope.SYSTEM);
        presentPredicateId = entities.put(AUTOCOMPLETE_PRESENT_PREDICATE, Entities.Scope.SYSTEM);
        indexStatusPredicateId = entities.put(AUTOCOMPLETE_STATUS_PREDICATE, Entities.Scope.SYSTEM);
        interruptIndexingPredicateId = entities.put(AUTOCOMPLETE_INDEXING_INTERRUPT_PREDICATE, Entities.Scope.SYSTEM);
        labelConfigId = entities.put(AUTOCOMPLETE_LABEL_CONFIG, Entities.Scope.SYSTEM);
        addLabelConfigId = entities.put(AUTOCOMPLETE_ADD_LABEL_CONFIG, Entities.Scope.SYSTEM);
        removeLabelConfigId = entities.put(AUTOCOMPLETE_REMOVE_LABEL_CONFIG, Entities.Scope.SYSTEM);

        resolveActualLabelConfig(entities);
    }

    @Override
    public double estimate(long subject, long predicate, long object, long context, PluginConnection pluginConnection,
                           RequestContext requestContext) {
        if (predicate == queryPredicateId || context == controlContextId)  {
            return Double.POSITIVE_INFINITY;
        } else {
            return 0;
        }
    }

    @Override
    public StatementIterator interpret(long subject, long predicate, long object, long context, PluginConnection pluginConnection,
                                       RequestContext requestContext) {
        if (context == controlContextId) {
            processControlRequest(subject, predicate, object, pluginConnection);

            return StatementIterator.TRUE();
        }

        if (predicate == presentPredicateId) {
            return StatementIterator.TRUE();
        }

        if (predicate == enabledPredicateId) {
            return isPluginEnabled ? StatementIterator.TRUE() : StatementIterator.FALSE();
        }

        if (predicate == indexIRIsPredicateId) {
            return configuredShouldIndexIRIs ? StatementIterator.TRUE() : StatementIterator.FALSE();
        }

        if (predicate == indexStatusPredicateId) {
            long statusRequestEntity = pluginConnection.getEntities().put(VF.createLiteral(getIndexStatus()), Entities.Scope.REQUEST);
            return StatementIterator.create(subject, predicate, statusRequestEntity, context);
        }

        if (predicate == labelConfigId) {
            long[][] resultStatements = new long[configuredLabelConfigs.size()][];
            int i = 0;
            for (LabelConfig config : configuredLabelConfigs.values()) {
                long labelPredicateId = pluginConnection.getEntities().put(config.labelIRI, Entities.Scope.REQUEST);
                long labelLanguagesId = pluginConnection.getEntities().put(VF.createLiteral(String.join(", ", config.languages)), Entities.Scope.REQUEST);
                resultStatements[i] = new long[] {labelPredicateId, predicate, labelLanguagesId, 0};
                i++;
            }
            return StatementIterator.create(resultStatements);
        }

        if (!isPluginEnabled) {
            return null;
        }

        if (predicate != queryPredicateId) {
            return null;
        }
        String queryStr = pluginConnection.getEntities().get(object).stringValue();
        String namespace = null;
        String query = "";

        if (!queryStr.contains(";")) {
            // No prefix
            query = queryStr;
        } else if (queryStr.endsWith(";")) {
            // Only a prefix
            namespace = queryStr.substring(0, queryStr.length() - 1);
            query = "";
        } else {
            String[] split = queryStr.split(";");
            if (split.length > 1) {
                namespace = split[0];
                query = split[1];
            }
        }

        Collection<AutocompleteIndex.Result> foundEntities = autocompleteIndex.findEntities(namespace, query,
                pluginConnection.getEntities(), RESULTS_COUNT);
        long[][] resultStatements = new long[foundEntities.size()][];
        int i = 0;
        for (AutocompleteIndex.Result result : foundEntities) {
            resultStatements[i] = new long[] {result.id, predicate, object,
                    pluginConnection.getEntities().put(VF.createLiteral(result.highlight), Entities.Scope.REQUEST)};
            i++;
        }
        return StatementIterator.create(resultStatements);
    }

    @Override
    public long[] getPredicatesToListenFor() {
        return new long[]{ reIndexPredicateId, enabledPredicateId, indexIRIsPredicateId, interruptIndexingPredicateId, addLabelConfigId, removeLabelConfigId };
    }

    @Override
    public boolean interpretUpdate(long subject, long predicate, long object, long context,
                                   boolean isAddition, boolean isExplicit, PluginConnection pluginConnection) {
        processControlRequest(subject, predicate, object, pluginConnection);

        return true;
    }

    private void processControlRequest(long subject, long predicate, long object,
                                       PluginConnection pluginConnection) {
        if (predicate == reIndexPredicateId) {
            if (!isPluginEnabled) {
                throw new PluginException("Autocomplete is not enabled.");
            }
            try {
                autocompleteIndex.buildIndex(pluginConnection);
            } catch (Exception e) {
                throw new PluginException("Unable to start index rebuilding.", e);
            }
        }

        if (predicate == interruptIndexingPredicateId) {
            autocompleteIndex.interrupt();
        }

        if (predicate == enabledPredicateId) {
            //setEntities(entities);
            //setStatements(statements);
            String pluginEnabledStringLiteral = pluginConnection.getEntities().get(object).stringValue();
            boolean isPluginEnabledNew = XMLDatatypeUtil.parseBoolean(pluginEnabledStringLiteral);
            if (isPluginEnabledNew != isPluginEnabled) {
                this.isPluginEnabled = isPluginEnabledNew;
                savePluginConfig();

                if (this.isPluginEnabled) {
                    initAutocompleteIndex();
                    if (!autocompleteIndex.hasBuiltIndex) {
                        autocompleteIndex.buildIndex(pluginConnection);
                    }
                }
            }
        }

        if (predicate == indexIRIsPredicateId) {
            String pluginIndexIRIsStringLiteral = pluginConnection.getEntities().get(object).stringValue();
            boolean previousValue = configuredShouldIndexIRIs;
            configuredShouldIndexIRIs = XMLDatatypeUtil.parseBoolean(pluginIndexIRIsStringLiteral);
            if (configuredShouldIndexIRIs != previousValue) {
                savePluginConfig();
                updateConfiguredVsActualConfig();
            }
        }

        if (predicate == addLabelConfigId) {
            IRI labelPredicate = (IRI) pluginConnection.getEntities().get(subject);
            LabelConfig labelConfig = new LabelConfig(labelPredicate, subject, pluginConnection.getEntities().get(object).stringValue());
            LabelConfig previousLabelConfig = configuredLabelConfigs.put(labelPredicate, labelConfig);
            if (!labelConfig.equals(previousLabelConfig)) {
                autocompleteUpdateLister.notifyAboutLabelConfig(labelConfig);
                savePluginConfig();
                updateConfiguredVsActualConfig();
            }
        }

        if (predicate == removeLabelConfigId) {
            IRI labelPredicate = (IRI) pluginConnection.getEntities().get(subject);
            if (configuredLabelConfigs.remove(labelPredicate) != null) {
                savePluginConfig();
                updateConfiguredVsActualConfig();
            }
        }
    }

    private void initAutocompleteIndex() {
        if (autocompleteIndex == null) {
            autocompleteIndex = new AutocompleteIndex(this);
        }
    }

    public PluginLocator getPluginLocator() {
        return pluginLocator;
    }

    private String getIndexStatus() {
        if (isPluginEnabled) {
            IndexStatus status = autocompleteIndex.status();
            if (status == IndexStatus.ERROR) {
                return status.name() + ": " + autocompleteIndex.error();
            } else {
                return status.name();
            }
        }
        return IndexStatus.NONE.name();
    }

    @Override
    public void shutdown(ShutdownReason shutdownReason) {
        if (autocompleteIndex != null) {
            autocompleteIndex.shutDown();
            autocompleteIndex = null;
        }
    }

    @Override
    public void setLocator(PluginLocator pluginLocator) {
        this.pluginLocator = pluginLocator;
    }

    private void savePluginConfig() {
        setPluginEnabledInProperties(pluginConfig, isPluginEnabled);
        setConfiguredShouldIndexIRIsInProperties(pluginConfig, configuredShouldIndexIRIs);
        setActualShouldIndexIRIsInProperties(pluginConfig, configuredShouldIndexIRIs);
        setConfiguredLabelsInProperties(pluginConfig, configuredLabelConfigs);
        setActualLabelsInProperties(pluginConfig, labelConfigs);
        updatePluginConfiguration(AutocompletePluginUtils.resolveConfigDirectory(getDataDir(), false).toFile(), pluginConfig);
    }

    private LabelConfig[] labelConfigMapToArray(Map<IRI, LabelConfig> labels) {
        return labels.values().toArray(new LabelConfig[labels.size()]);
    }

    void updateActualConfig(Entities entities) {
        configuredAndActualConfigsDiffer = false;
        actualShouldIndexIRIs = configuredShouldIndexIRIs;
        labelConfigs = labelConfigMapToArray(configuredLabelConfigs);
        resolveActualLabelConfig(entities);
        savePluginConfig();
    }

    private void resolveActualLabelConfig(Entities entities) {
        // Resolves the ids for the actually configured labels
        for (LabelConfig labelConfig : labelConfigs) {
            labelConfig.labelId = entities.resolve(labelConfig.labelIRI);
            autocompleteUpdateLister.notifyAboutLabelConfig(labelConfig);
        }
    }

    private void updateConfiguredVsActualConfig() {
        configuredAndActualConfigsDiffer = configuredShouldIndexIRIs != actualShouldIndexIRIs
                || !Arrays.deepEquals(configuredLabelConfigs.values().toArray(), labelConfigs);
    }

    @Override
    public void entityAdded(long id, Value value, PluginConnection pluginConnection) {
        autocompleteUpdateLister.entityAdded(id, value, pluginConnection);
    }

    @Override
    public void transactionCommit(PluginConnection pluginConnection) {
        autocompleteUpdateLister.transactionCommit(pluginConnection);
    }

    @Override
    public boolean statementAdded(long subject, long predicate, long object, long context, boolean explicit,
                                  PluginConnection pluginConnection) {
        return autocompleteUpdateLister.statementAdded(subject, predicate, object, context, explicit, pluginConnection);
    }

    @Override
    public boolean statementRemoved(long subject, long predicate, long object, long context, boolean explicit,
                                    PluginConnection pluginConnection) {
        return autocompleteUpdateLister.statementRemoved(subject, predicate, object, context, explicit, pluginConnection);
    }

    @Override
    public void transactionStarted(PluginConnection pluginConnection) {
        autocompleteUpdateLister.transactionStarted(pluginConnection);
    }

    @Override
    public void transactionCompleted(PluginConnection pluginConnection) {
        autocompleteUpdateLister.transactionCompleted(pluginConnection);
    }

    @Override
    public void transactionAborted(PluginConnection pluginConnection) {
        autocompleteUpdateLister.transactionAborted(pluginConnection);
    }

    @Override
    public void transactionAbortedByUser(PluginConnection pluginConnection) {
        autocompleteUpdateLister.transactionAbortedByUser(pluginConnection);
    }

    @Override
    public void beforeClear(long context, PluginConnection pluginConnection) {
        // do nothing
    }

    @Override
    public void afterClear(long context, PluginConnection pluginConnection) {
        autocompleteIndex.setRepositoryCleared(context == 0);
    }
}
