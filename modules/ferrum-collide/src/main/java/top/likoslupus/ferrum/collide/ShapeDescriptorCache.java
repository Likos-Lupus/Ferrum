package top.likoslupus.ferrum.collide;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.likoslupus.ferrum.collide.mixin.VoxelShapeAccessor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Identity cache of serialized voxel-shape descriptors.
 *
 * <p>Shape instances are effectively immutable and long lived, so each is encoded once and reused
 * across movements. The cache is bounded by the set of distinct shapes; a shape that cannot be
 * flattened returns {@code null} and the caller falls back to vanilla.
 */
public final class ShapeDescriptorCache {

    private final Map<VoxelShape, byte[]> cache =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /**
     * Returns the serialized descriptor for a shape, encoding it on first use.
     *
     * @param shape the shape
     *
     * @return the descriptor bytes, or {@code null} when the shape is not serializable
     */
    public byte @Nullable [] describe(VoxelShape shape) {
        var cached = cache.get(shape);
        if (cached != null) {
            return cached;
        }
        if (!(shape instanceof VoxelShapeAccessor accessor)) {
            return null;
        }
        var voxel = accessor.ferrum$shape();
        var bytes = CollideBlob.serializeShape(
                voxel,
                shape.getCoords(Direction.Axis.X),
                shape.getCoords(Direction.Axis.Y),
                shape.getCoords(Direction.Axis.Z)
        );
        if (bytes != null) {
            cache.put(shape, bytes);
        }
        return bytes;
    }

    /**
     * Drops every cached descriptor.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Returns the number of cached descriptors.
     *
     * @return the cache size
     */
    public int size() {
        return cache.size();
    }

}
