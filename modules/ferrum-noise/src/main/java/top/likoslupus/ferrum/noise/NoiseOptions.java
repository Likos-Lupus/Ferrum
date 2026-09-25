package top.likoslupus.ferrum.noise;

import top.likoslupus.ferrum.runtime.config.ModuleSettings;

/**
 * The typed projection of the noise module's configuration options.
 *
 * @param minSamples the minimum grid size for which the native batch path is used
 * @param grid       whether the leaf grid batch path is enabled
 */
public record NoiseOptions(
        int minSamples,
        boolean grid
) {

    public NoiseOptions {
        if (minSamples < 0) {
            throw new IllegalArgumentException("minSamples must be >= 0");
        }
    }

    /**
     * Projects the module settings onto the typed noise options.
     *
     * @param settings the module settings
     *
     * @return the noise options
     */
    public static NoiseOptions from(ModuleSettings settings) {
        return new NoiseOptions(
                settings.optionInt("minSamples", 64),
                !"leaf".equals(settings.optionString("mode", "grid"))
        );
    }

    /**
     * Returns the default noise options.
     *
     * @return the default options
     */
    public static NoiseOptions defaults() {
        return new NoiseOptions(64, true);
    }

}
