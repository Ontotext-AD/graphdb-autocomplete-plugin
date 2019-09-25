package com.ontotext.trree.plugin.autocomplete;

import org.apache.commons.io.FileUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by desislava on 17/11/15.
 */
class AutocompletePluginUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AutocompletePluginUtils.class);

    private static final String VERSION = "2";

    private static final String LEGACY_VERSION = "";

    private static final ValueFactory VF = SimpleValueFactory.getInstance();

    private static final String PLUGIN_CONFIG_FILENAME = "config.properties";

    public static final String AUTOCOMPLETE_ENABLED_PROPERTY = "autocomplete.enabled";

    public static final String AUTOCOMPLETE_CONFIGURED_INDEX_IRIS_PROPERTY = "autocomplete.indexiris.configured";

    public static final String AUTOCOMPLETE_ACTUAL_INDEX_IRIS_PROPERTY = "autocomplete.indexiris.actual";

    public static final String AUTOCOMPLETE_CONFIGURED_LABELS_PROPERTY = "autocomplete.labels.configured";

    public static final String AUTOCOMPLETE_ACTUAL_LABELS_PROPERTY = "autocomplete.labels.actual";

    public static void migrateConfig(File pluginDataDir, Logger logger) {
        Path configPath = resolveConfigDirectory(pluginDataDir, false).resolve(PLUGIN_CONFIG_FILENAME);
        if (!Files.exists(configPath)) {
            File legacyConfigFile = resolveConfigDirectory(pluginDataDir, true).resolve(PLUGIN_CONFIG_FILENAME).toFile();
            if (Files.isReadable(legacyConfigFile.toPath())) {
                try {
                    Properties properties = new Properties();
                    loadPropertyFile(properties, legacyConfigFile);
                    if (isPluginEnabledFromProperties(properties)) {
                        logger.info("Detected incompatible index from a previous version. Please rebuild the index manually.");
                        setPluginEnabledInProperties(properties, false);
                    }
                    updatePluginConfiguration(resolveConfigDirectory(pluginDataDir, false).toFile(), properties);
                } catch (IOException e) {
                    LOG.error("Cannot load legacy configuration file!", e);
                }
            }
        }
    }

    /**
     * Load plugin configuration from file or defaults.
     *
     * @param pluginDataDir data directory of the plugin
     */
    public static Properties loadConfig(File pluginDataDir) {
        Properties properties = new Properties();
        File pluginConfigFile = resolveConfigDirectory(pluginDataDir, false).resolve(PLUGIN_CONFIG_FILENAME).toFile();

        boolean wasLoaded = false;
        if (pluginConfigFile.exists()) {
            try {
                loadPropertyFile(properties, pluginConfigFile);
                // If new properties (since label indexing was introduced) don't exist,
                // then we set defaults that correspond to pre-labels behaviour.
                if (properties.getProperty(AUTOCOMPLETE_CONFIGURED_INDEX_IRIS_PROPERTY) == null) {
                    properties.setProperty(AUTOCOMPLETE_CONFIGURED_INDEX_IRIS_PROPERTY, "true");
                }
                if (properties.getProperty(AUTOCOMPLETE_ACTUAL_INDEX_IRIS_PROPERTY) == null) {
                    properties.setProperty(AUTOCOMPLETE_ACTUAL_INDEX_IRIS_PROPERTY, "true");
                }
                if (properties.getProperty(AUTOCOMPLETE_CONFIGURED_LABELS_PROPERTY) == null) {
                    properties.setProperty(AUTOCOMPLETE_CONFIGURED_LABELS_PROPERTY, "");
                }
                if (properties.getProperty(AUTOCOMPLETE_ACTUAL_LABELS_PROPERTY) == null) {
                    properties.setProperty(AUTOCOMPLETE_ACTUAL_LABELS_PROPERTY, "");
                }
                wasLoaded = true;
            } catch (IOException e) {
                LOG.error("Cannot load plugin configuration file!", e);
            }
        }
        if (!wasLoaded) {
            LOG.info(">>>>>>>> AutocompletePlugin: No configuration file found at {}. " +
                    "Assuming default options for plugin.", pluginConfigFile);

            properties.setProperty(AUTOCOMPLETE_ENABLED_PROPERTY, "false");
        }

        return properties;
    }

    /**
     * Load property file.
     *
     * @param properties java.util.Properties object
     * @param file       file to be loaded
     */
    private static void loadPropertyFile(Properties properties, File file)
            throws IOException {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            properties.load(in);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Update plugin configuration with new values when state of the plugin changes.
     *
     * @param properties properties to save
     */
    public static void updatePluginConfiguration(File pluginDataDir, Properties properties) {
        try {
            File pluginConfigFile = new File(pluginDataDir, PLUGIN_CONFIG_FILENAME);
            if (!pluginConfigFile.exists()) {
                FileUtils.forceMkdir(pluginDataDir);
            }
            try (FileWriter writer = new FileWriter(pluginConfigFile)) {
                properties.store(writer, "Autocomplete plugin config");
            }
        } catch (IOException e) {
            LOG.error("Cannot update plugin configuration", e);
        }
    }

    public static boolean isPluginEnabledFromProperties(Properties properties) {
        return XMLDatatypeUtil.parseBoolean(properties.getProperty(AUTOCOMPLETE_ENABLED_PROPERTY, "false"));
    }

    public static void setPluginEnabledInProperties(Properties properties, boolean isPluginEnabled) {
        properties.setProperty(AUTOCOMPLETE_ENABLED_PROPERTY, Boolean.toString(isPluginEnabled));
    }

    public static boolean configuredShouldIndexIRIsFromProperties(Properties properties) {
        return XMLDatatypeUtil.parseBoolean(properties.getProperty(AUTOCOMPLETE_CONFIGURED_INDEX_IRIS_PROPERTY, "true"));
    }

    public static void setConfiguredShouldIndexIRIsInProperties(Properties properties, boolean shouldIndexIRIs) {
        properties.setProperty(AUTOCOMPLETE_CONFIGURED_INDEX_IRIS_PROPERTY, Boolean.toString(shouldIndexIRIs));
    }

    public static boolean actualShouldIndexIRIsFromProperties(Properties properties) {
        return XMLDatatypeUtil.parseBoolean(properties.getProperty(AUTOCOMPLETE_ACTUAL_INDEX_IRIS_PROPERTY, "true"));
    }

    public static void setActualShouldIndexIRIsInProperties(Properties properties, boolean shouldIndexIRIs) {
        properties.setProperty(AUTOCOMPLETE_ACTUAL_INDEX_IRIS_PROPERTY, Boolean.toString(shouldIndexIRIs));
    }

    public static Map<IRI, LabelConfig> getConfiguredLabelsFromProperties(Properties properties) {
        return getLabelsFromProperties(properties, AUTOCOMPLETE_CONFIGURED_LABELS_PROPERTY);
    }

    public static void setConfiguredLabelsInProperties(Properties properties, Map<IRI, LabelConfig> labelConfigs) {
        setLabelsInProperties(properties, AUTOCOMPLETE_CONFIGURED_LABELS_PROPERTY, labelConfigs.values());
    }

    public static void setActualLabelsInProperties(Properties properties, LabelConfig[] labelConfigs) {
        setLabelsInProperties(properties, AUTOCOMPLETE_ACTUAL_LABELS_PROPERTY, Arrays.asList(labelConfigs));
    }

    public static Map<IRI, LabelConfig> getActualLabelsFromProperties(Properties properties) {
        return getLabelsFromProperties(properties, AUTOCOMPLETE_ACTUAL_LABELS_PROPERTY);
    }

    private static Map<IRI, LabelConfig> getLabelsFromProperties(Properties properties, String propertyName) {
        Map<IRI, LabelConfig> labelConfigs = new HashMap<>();

        String propertyValue = properties.getProperty(propertyName, RDFS.LABEL.stringValue() + "@");
        if (!propertyValue.isEmpty()) {
            String[] labelStrings = propertyValue.split("\n");
            for (String labelString : labelStrings) {
                String[] labelData = labelString.split("@", 2);
                IRI labelIri = VF.createIRI(labelData[0]);
                labelConfigs.put(labelIri, new LabelConfig(labelIri, 0, labelData[1]));
            }
        }

        return labelConfigs;
    }

    private static void setLabelsInProperties(Properties properties, String propertyName, Collection<LabelConfig> labelConfigs) {
        String propertiesValue = labelConfigs.stream()
                .map(lc -> lc.labelIRI.stringValue() + "@" + String.join(",", lc.languages))
                .collect(Collectors.joining("\n"));

        properties.setProperty(propertyName, propertiesValue);
    }

    /**
     *  This method will return path to config.properties file, based on
     *  legacy parameter. On new version should be changed to "v" + (legacy ? LEGACY_VERSION : VERSION)
     * @param pluginDataDir
     * @param legacy
     * @return
     */
    static Path resolveConfigDirectory(File pluginDataDir, boolean legacy) {
        return pluginDataDir.toPath().resolve(legacy ? LEGACY_VERSION : "v" + VERSION);
    }

    /**
     *  Returns current version index directory
     * @param pluginDataDir
     * @return
     */
    static Path resolveIndexDirectory(File pluginDataDir) {
        return pluginDataDir.toPath().resolve("v" + VERSION).resolve("index");
    }
}
