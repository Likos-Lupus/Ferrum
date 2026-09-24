package top.likoslupus.ferrum.runtime.config;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Per-module settings.
 *
 * @param enabled  whether the module's native fast path may be used
 * @param minBatch the minimum batch size for the native fast path; {@code 0} when the module has no
 *                 measured threshold yet
 * @param options  module-specific scalar options, projected to a typed record by the module itself
 */
public record ModuleSettings(
        boolean enabled,
        int minBatch,
        Map<String, JsonNode> options
) {

    /**
     * Creates settings with no batch threshold and no options.
     *
     * @param enabled whether the module's native fast path may be used
     */
    public ModuleSettings(boolean enabled) {
        this(enabled, 0, Map.of());
    }

    public ModuleSettings {
        if (minBatch < 0) {
            throw new IllegalArgumentException("minBatch must be >= 0");
        }
        options = Map.copyOf(options);
    }

    /**
     * Creates settings with no options.
     *
     * @param enabled  whether the module's native fast path may be used
     * @param minBatch the minimum batch size
     */
    public ModuleSettings(boolean enabled, int minBatch) {
        this(enabled, minBatch, Map.of());
    }

    /**
     * Returns a boolean option.
     *
     * @param name     the option name
     * @param fallback the value used when the option is absent
     *
     * @return the option value, or {@code fallback}
     */
    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    public boolean optionBoolean(String name, boolean fallback) {
        var node = options.get(name);
        return (node != null && node.isBoolean())
                ? node.asBoolean()
                : fallback;
    }

    /**
     * Returns an integer option.
     *
     * @param name     the option name
     * @param fallback the value used when the option is absent
     *
     * @return the option value, or {@code fallback}
     */
    public int optionInt(String name, int fallback) {
        var node = options.get(name);
        return (node != null && node.isIntegralNumber())
                ? node.asInt()
                : fallback;
    }

    /**
     * Returns a long option.
     *
     * @param name     the option name
     * @param fallback the value used when the option is absent
     *
     * @return the option value, or {@code fallback}
     */
    public long optionLong(String name, long fallback) {
        var node = options.get(name);
        return (node != null && node.isIntegralNumber())
                ? node.asLong()
                : fallback;
    }

    /**
     * Returns a double option.
     *
     * @param name     the option name
     * @param fallback the value used when the option is absent
     *
     * @return the option value, or {@code fallback}
     */
    public double optionDouble(String name, double fallback) {
        var node = options.get(name);
        return (node != null && node.isNumber())
                ? node.asDouble()
                : fallback;
    }

    /**
     * Returns a string option.
     *
     * @param name     the option name
     * @param fallback the value used when the option is absent
     *
     * @return the option value, or {@code fallback}
     */
    public String optionString(String name, String fallback) {
        var node = options.get(name);
        return (node != null && node.isString())
                ? node.asString()
                : fallback;
    }

}
