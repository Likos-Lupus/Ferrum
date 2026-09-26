package top.likoslupus.ferrum.collide;

import top.likoslupus.ferrum.runtime.config.ModuleSettings;

/**
 * The typed projection of the collide module's configuration options.
 *
 * @param clip      whether the batch AABB ray clip fast path may be used
 * @param sweep     whether the batched voxel-shape sweep fast path may be used
 * @param minBoxes  the minimum number of boxes for the clip fast path
 * @param minShapes the minimum number of shapes for the sweep fast path
 */
public record CollideOptions(
        boolean clip,
        boolean sweep,
        int minBoxes,
        int minShapes
) {

    public CollideOptions {
        if (minBoxes < 1) {
            throw new IllegalArgumentException("minBoxes must be >= 1");
        }
        if (minShapes < 1) {
            throw new IllegalArgumentException("minShapes must be >= 1");
        }
    }

    /**
     * Projects the module settings onto the typed collide options.
     *
     * @param settings the module settings
     *
     * @return the collide options
     */
    public static CollideOptions from(ModuleSettings settings) {
        return new CollideOptions(
                settings.optionBoolean("clip", true),
                settings.optionBoolean("sweep", true),
                settings.optionInt("minBoxes", 8),
                settings.optionInt("minShapes", 4)
        );
    }

    /**
     * Returns the default collide options.
     *
     * @return the default options
     */
    public static CollideOptions defaults() {
        return new CollideOptions(true, true, 8, 4);
    }

}
