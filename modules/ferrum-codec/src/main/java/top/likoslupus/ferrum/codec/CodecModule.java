package top.likoslupus.ferrum.codec;

/**
 * FerrumCodec identity constants and capabilities.
 */
public final class CodecModule {

    public static final String MODULE_ID = "codec";
    public static final String NAME = "FerrumCodec";
    public static final String VERSION = "0.1.0";
    /** Capability marker reported by startup diagnostics. */
    public static final String CAPABILITY_LZ4 = "CODEC_LZ4";

    private CodecModule() {
    }

}
