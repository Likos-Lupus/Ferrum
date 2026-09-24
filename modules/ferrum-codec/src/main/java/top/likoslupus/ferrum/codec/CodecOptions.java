package top.likoslupus.ferrum.codec;

import top.likoslupus.ferrum.runtime.config.ModuleSettings;

/**
 * The typed projection of the codec module's configuration options.
 *
 * <p>The configuration file stores module options as generic JSON scalars; this record is where
 * the
 * codec module gives them meaning, so {@code JsonNode} never reaches codec business code.
 *
 * @param lz4                   whether the LZ4 block-stream fast path is enabled
 * @param accelerateExistingLz4 whether regions that already use LZ4 are accelerated
 * @param preferLz4ForNewWrites whether new region writes should use LZ4 (parsed; selecting the
 *                              Minecraft region format is a separate decision)
 */
public record CodecOptions(
        boolean lz4,
        boolean accelerateExistingLz4,
        boolean preferLz4ForNewWrites
) {

    /**
     * Projects the module settings onto the typed codec options.
     *
     * @param settings the module settings
     *
     * @return the codec options
     */
    public static CodecOptions from(ModuleSettings settings) {
        return new CodecOptions(
                settings.optionBoolean("lz4", true),
                settings.optionBoolean("accelerateExistingLz4", true),
                settings.optionBoolean("preferLz4ForNewWrites", false)
        );
    }

    /**
     * Returns the default codec options.
     *
     * @return the default options
     */
    public static CodecOptions defaults() {
        return new CodecOptions(
                true,
                true,
                false
        );
    }

}
