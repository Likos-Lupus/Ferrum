package top.likoslupus.ferrum.runtime.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads {@code config/ferrum.json} through the Jackson 3 facade.
 *
 * <p>Loading never throws and never leaves a half-initialized configuration: a missing, malformed,
 * or unreadable file is reported once and falls back to {@link FerrumConfig#defaults()}. Fields
 * absent from the file inherit their default value, so a partial configuration is still valid.
 */
public final class FerrumConfigLoader {

    /** The default configuration file name. */
    public static final String FILE_NAME = "ferrum.json";
    private static final Logger LOGGER = LoggerFactory.getLogger(FerrumConfigLoader.class);

    private FerrumConfigLoader() {
    }

    /**
     * Loads the configuration file, falling back to defaults on any problem.
     *
     * @param configFile the configuration file
     *
     * @return the parsed configuration, or defaults
     */
    public static FerrumConfig load(Path configFile) {
        if (!Files.isRegularFile(configFile)) {
            LOGGER.info("Ferrum config not found; using defaults: {}", configFile);
            return FerrumConfig.defaults();
        }

        try (var input = Files.newInputStream(configFile)) {
            return parse(input);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn(
                    "Ferrum config could not be read; using defaults: {}",
                    configFile,
                    exception
            );
            return FerrumConfig.defaults();
        }
    }

    private static FerrumConfig parse(InputStream input) {
        var defaults = FerrumConfig.defaults();
        var root = DataFormats.json().readTree(input);

        var nativeNode = root.get("native");
        var defaultNative = defaults.nativeSettings();
        var nativeSettings = new NativeSettings(
                getBooleanValue(nativeNode, "enabled", defaultNative.enabled()),
                getBooleanValue(nativeNode, "strictAbi", defaultNative.strictAbi()),
                getBooleanValue(nativeNode, "verifyChecksums", defaultNative.verifyChecksums()),
                getBooleanValue(nativeNode, "diagnostics", defaultNative.diagnostics())
        );

        var modules = new LinkedHashMap<>(defaults.modules());
        var modulesNode = root.get("modules");
        if (modulesNode != null && modulesNode.isObject()) {
            modulesNode.properties()
                    .forEach(entry -> {
                        var name = entry.getKey();
                        var defaultsForModule = defaults.modules().get(name);
                        if (defaultsForModule == null) {
                            LOGGER.warn("Ferrum config module '{}' is unknown; ignored", name);
                            return;
                        }
                        var node = entry.getValue();
                        var enabled = node.path("enabled").asBoolean(defaultsForModule.enabled());
                        var minBatch = node.path("minBatch").asInt(defaultsForModule.minBatch());
                        modules.put(
                                name,
                                new ModuleSettings(
                                        enabled,
                                        minBatch,
                                        options(name, node, defaultsForModule)
                                )
                        );
                    });
        }

        return new FerrumConfig(nativeSettings, modules);
    }

    private static boolean getBooleanValue(
            JsonNode node,
            String field,
            boolean fallback
    ) {
        var value = node.get(field);
        return (value == null || value.isNull())
                ? fallback
                : value.asBoolean(fallback);
    }

    private static Map<String, JsonNode> options(
            String module,
            JsonNode moduleNode,
            ModuleSettings defaults
    ) {
        var options = new LinkedHashMap<>(defaults.options());
        var optionsNode = moduleNode.get("options");
        if (optionsNode != null && optionsNode.isObject()) {
            optionsNode.properties()
                    .forEach(entry -> {
                        var value = entry.getValue();
                        if (value.isBoolean() || value.isNumber() || value.isString()) {
                            options.put(entry.getKey(), value);
                        } else {
                            LOGGER.warn(
                                    "Ferrum config option '{}.{}' must be a JSON scalar; ignored",
                                    module,
                                    entry.getKey()
                            );
                        }
                    });
        }
        return options;
    }

}
