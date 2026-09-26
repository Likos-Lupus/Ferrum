package top.likoslupus.ferrum.collide;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.reflect.Field;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Reads the protected {@code VoxelShape.shape} field for tests.
 *
 * <p>Mixin accessors are inactive under plain JUnit, so the tests reflect the field directly (dev
 * mappings expose it as {@code shape}). Production code uses the {@code VoxelShapeAccessor} mixin.
 */
final class TestShapeExtractor {

    private static final Field SHAPE_FIELD = shapeField();

    private TestShapeExtractor() {
    }

    static byte[] describe(VoxelShape shape) {
        var voxel = voxel(shape);
        return requireNonNull(
                CollideBlob.serializeShape(
                        voxel,
                        shape.getCoords(Direction.Axis.X),
                        shape.getCoords(Direction.Axis.Y),
                        shape.getCoords(Direction.Axis.Z)
                ),
                "serializable shape"
        );
    }

    static DiscreteVoxelShape voxel(VoxelShape shape) {
        try {
            return requireNonNull(
                    (DiscreteVoxelShape) SHAPE_FIELD.get(shape),
                    "shape field"
            );
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("cannot read VoxelShape.shape", exception);
        }
    }

    private static Field shapeField() {
        try {
            var field = VoxelShape.class.getDeclaredField("shape");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException("VoxelShape.shape not found", exception);
        }
    }

}
