package top.likoslupus.ferrum.nbt;

/**
 * FerrumNbt identity constants and capabilities.
 */
public final class NbtModule {

    public static final String MODULE_ID = "nbt";
    public static final String NAME = "FerrumNbt";
    public static final String VERSION = "0.1.0";
    /** Capability marker reported by startup diagnostics. */
    public static final String CAPABILITY_BUFFER_IO = "NBT_BUFFER_IO";
    /** Capability marker for the chunk fixed-schema spike (not enabled by default). */
    public static final String CAPABILITY_CHUNK_SCHEMA = "NBT_CHUNK_SCHEMA";

    private NbtModule() {
    }

}
