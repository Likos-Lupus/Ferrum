package top.likoslupus.ferrum.palette;

import top.likoslupus.ferrum.runtime.config.ModuleSettings;

/**
 * The typed projection of the palette module's configuration options.
 *
 * <p>The configuration file stores module options as generic JSON scalars; this record is where
 * the
 * palette module gives them meaning, so {@code JsonNode} never reaches palette business code.
 *
 * @param minValues the minimum value count for which the native bulk path is used
 */
public record PaletteOptions(
        int minValues
) {

    public PaletteOptions {
        if (minValues < 0) {
            throw new IllegalArgumentException("minValues must be >= 0");
        }
    }

    /**
     * Projects the module settings onto the typed palette options.
     *
     * @param settings the module settings
     *
     * @return the palette options
     */
    public static PaletteOptions from(ModuleSettings settings) {
        return new PaletteOptions(settings.optionInt("minValues", 256));
    }

    /**
     * Returns the default palette options.
     *
     * @return the default options
     */
    public static PaletteOptions defaults() {
        return new PaletteOptions(256);
    }

}
