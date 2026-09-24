package top.likoslupus.ferrum.runtime.config;

/**
 * Per-module settings.
 *
 * @param enabled  whether the module's native fast path may be used
 * @param minBatch the minimum batch size for the native fast path; {@code 0} when the module has no
 *                 measured threshold yet
 */
public record ModuleSettings(
        boolean enabled,
        int minBatch
) {

    /**
     * Creates settings with no batch threshold.
     *
     * @param enabled whether the module's native fast path may be used
     */
    public ModuleSettings(boolean enabled) {
        this(enabled, 0);
    }

    public ModuleSettings {
        if (minBatch < 0) {
            throw new IllegalArgumentException("minBatch must be >= 0");
        }
    }

}
