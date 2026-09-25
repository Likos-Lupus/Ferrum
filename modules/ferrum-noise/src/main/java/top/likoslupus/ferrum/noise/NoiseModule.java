package top.likoslupus.ferrum.noise;

/**
 * FerrumNoise identity constants and capabilities.
 */
public final class NoiseModule {

    public static final String MODULE_ID = "noise";
    public static final String NAME = "FerrumNoise";
    public static final String VERSION = "0.1.0";
    /** Capability marker for the leaf batch kernel. */
    public static final String CAPABILITY_LEAF = "NOISE_BATCH";
    /** Capability marker for the leaf grid batch path. */
    public static final String CAPABILITY_GRID = "NOISE_GRID";

    private NoiseModule() {
    }

}
