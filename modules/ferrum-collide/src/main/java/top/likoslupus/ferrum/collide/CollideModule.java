package top.likoslupus.ferrum.collide;

/**
 * FerrumCollide identity constants and capabilities.
 */
public final class CollideModule {

    public static final String MODULE_ID = "collide";
    public static final String NAME = "FerrumCollide";
    public static final String VERSION = "0.1.0";
    /** Capability marker for the batch AABB clip kernel. */
    public static final String CAPABILITY_AABB_CLIP = "COLLIDE_AABB_CLIP";
    /** Capability marker for the batched voxel-shape sweep kernel. */
    public static final String CAPABILITY_SHAPE_SWEEP = "COLLIDE_SHAPE_SWEEP";

    private CollideModule() {
    }

}
