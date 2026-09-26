package top.likoslupus.ferrum.collide;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Mixin-facing entry point for the collide fast paths (ADR-0019).
 *
 * <p>Eligibility is conservative: the runtime must be available, the collide module enabled, the
 * module's feature advertised (or the test-only override set), and the batch at least the
 * configured minimum. A shape that cannot be flattened, an over-limit batch, or any native failure
 * returns {@code null} so the vanilla path runs unchanged.
 */
public final class CollideHook {

    /** Test-only switch that lets tests reach the path without advertising the feature bit. */
    private static final String FORCE_PROPERTY = "ferrum.test.collide.force";

    private static final LongAdder FALLBACKS = new LongAdder();
    private static final ShapeDescriptorCache SHAPES = new ShapeDescriptorCache();
    private static final Direction[] DIRECTIONS = Direction.values();

    private CollideHook() {
    }

    /**
     * Attempts to run the batch AABB ray clip natively.
     *
     * @param boxes the local (block-relative) boxes
     * @param pos   the block position
     * @param from  the ray origin
     * @param to    the ray target
     *
     * @return the hit, or {@code null} to leave vanilla alone
     */
    public static @Nullable BlockHitResult tryClip(
            List<? extends AABB> boxes,
            BlockPos pos,
            Vec3 from,
            Vec3 to
    ) {
        var settings = settings();
        if (settings == null) {
            return null;
        }

        var options = CollideOptions.from(settings);
        if (!options.clip() || boxes.size() < options.minBoxes()) {
            FALLBACKS.increment();
            return null;
        }

        var world = boxes.stream()
                .map(box -> box.move(pos))
                .collect(Collectors.toCollection(() -> new ArrayList<>(boxes.size())));
        var output = NativeCollide.clip(CollideBlob.clipInput(world, from, to));
        if (output == null) {
            FALLBACKS.increment();
            return null;
        }

        var outcome = CollideBlob.readClip(output);
        if (!outcome.found() || outcome.direction() >= DIRECTIONS.length) {
            FALLBACKS.increment();
            return null;
        }

        var scale = outcome.scale();
        var hit = from.add(
                (to.x - from.x) * scale,
                (to.y - from.y) * scale,
                (to.z - from.z) * scale
        );
        return new BlockHitResult(
                hit,
                DIRECTIONS[outcome.direction()],
                pos,
                false
        );
    }

    private static @Nullable ModuleSettings settings() {
        var runtime = FerrumRuntime.instance();
        if (!runtime.isAvailable()) {
            return null;
        }

        var settings = runtime.config().modules().get(CollideModule.MODULE_ID);
        if (settings == null) {
            return null;
        }

        var advertised = NativeFeatures.isSupported(runtime.featureBits(), ModuleId.COLLIDE);
        return (advertised && settings.enabled()) || forced()
                ? settings
                : null;
    }

    /**
     * Returns whether the test-only forced override is active.
     *
     * @return {@code true} when {@code ferrum.test.collide.force} is set
     */
    public static boolean forced() {
        return Boolean.getBoolean(FORCE_PROPERTY);
    }

    /**
     * Attempts to run the batched voxel-shape sweep natively.
     *
     * @param movement the desired movement
     * @param box      the moving box
     * @param shapes   the candidate shapes
     *
     * @return the resolved movement, or {@code null} to leave vanilla alone
     */
    public static @Nullable Vec3 trySweep(
            Vec3 movement,
            AABB box,
            List<? extends VoxelShape> shapes
    ) {
        var settings = settings();
        if (settings == null) {
            return null;
        }

        var options = CollideOptions.from(settings);
        if (!options.sweep() || shapes.size() < options.minShapes()) {
            FALLBACKS.increment();
            return null;
        }

        var serialized = new ArrayList<byte[]>(shapes.size());
        for (var shape : shapes) {
            var bytes = SHAPES.describe(shape);
            if (bytes == null) {
                FALLBACKS.increment();
                return null;
            }
            serialized.add(bytes);
        }

        // Direction.axisStepOrder: Y first, then the larger horizontal axis (tie -> X first).
        var axisOrder = Math.abs(movement.x) < Math.abs(movement.z)
                ? new byte[]{1, 2, 0}
                : new byte[]{1, 0, 2};
        var output = NativeCollide.sweep(
                CollideBlob.sweepInput(movement, box, axisOrder, serialized)
        );
        if (output == null) {
            FALLBACKS.increment();
            return null;
        }

        var resolved = CollideBlob.readSweep(output);
        return new Vec3(resolved[0], resolved[1], resolved[2]);
    }

    /**
     * Returns how many times a native path declined and fell back to vanilla.
     *
     * @return the fallback count
     */
    public static long fallbacks() {
        return FALLBACKS.sum();
    }

    /**
     * Drops every cached shape descriptor, called on resource reload.
     */
    public static void clearShapeCache() {
        SHAPES.clear();
    }

    /**
     * Returns the number of cached shape descriptors.
     *
     * @return the cache size
     */
    public static int cachedShapes() {
        return SHAPES.size();
    }

}
