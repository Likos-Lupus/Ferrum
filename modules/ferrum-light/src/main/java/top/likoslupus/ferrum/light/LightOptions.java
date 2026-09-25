package top.likoslupus.ferrum.light;

import top.likoslupus.ferrum.runtime.config.ModuleSettings;

/**
 * The typed projection of the light module's configuration options.
 *
 * @param maxSections the maximum number of sections in one native batch
 */
public record LightOptions(
        int maxSections
) {

    public LightOptions {
        if (maxSections < 1) {
            throw new IllegalArgumentException("maxSections must be >= 1");
        }
    }

    /**
     * Projects the module settings onto the typed light options.
     *
     * @param settings the module settings
     *
     * @return the light options
     */
    public static LightOptions from(ModuleSettings settings) {
        return new LightOptions(settings.optionInt("maxSections", 512));
    }

    /**
     * Returns the default light options.
     *
     * @return the default options
     */
    public static LightOptions defaults() {
        return new LightOptions(512);
    }

}
