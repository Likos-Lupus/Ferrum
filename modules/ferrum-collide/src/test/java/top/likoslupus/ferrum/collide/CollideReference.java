package top.likoslupus.ferrum.collide;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The vanilla reference implementations used by the collide differential and golden tests.
 */
final class CollideReference {

    private CollideReference() {
    }

    /**
     * The vanilla {@code AABB.clip(Iterable, from, to, pos)} result.
     *
     * @param boxes the local boxes
     * @param from  the ray origin
     * @param to    the ray target
     * @param pos   the block position
     *
     * @return the hit, or {@code null}
     */
    static @Nullable BlockHitResult clip(
            List<AABB> boxes,
            Vec3 from,
            Vec3 to,
            BlockPos pos
    ) {
        return AABB.clip(boxes, from, to, pos);
    }

    /**
     * The vanilla {@code Entity.collideWithShapes} result, reproduced with public APIs.
     *
     * @param movement the desired movement
     * @param box      the moving box
     * @param shapes   the candidate shapes
     *
     * @return the resolved movement
     */
    static Vec3 sweep(
            Vec3 movement,
            AABB box,
            List<VoxelShape> shapes
    ) {
        if (shapes.isEmpty()) {
            return movement;
        }

        var order = Math.abs(movement.x) < Math.abs(movement.z)
                ? new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X}
                : new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z};
        double x = 0.0, y = 0.0, z = 0.0;
        for (var axis : order) {
            var amount = component(movement, axis);
            if (amount == 0.0) {
                continue;
            }

            var collision = Shapes.collide(axis, box.move(x, y, z), shapes, amount);
            switch (axis) {
                case X -> x = collision;
                case Y -> y = collision;
                case Z -> z = collision;
            }
        }

        return new Vec3(x, y, z);
    }

    /**
     * Returns the requested component of a vector.
     *
     * @param vector the vector
     * @param axis   the axis
     *
     * @return the component
     */
    static double component(Vec3 vector, Direction.Axis axis) {
        return switch (axis) {
            case X -> vector.x;
            case Y -> vector.y;
            case Z -> vector.z;
        };
    }

    /**
     * Builds a shape from a set of unit boxes.
     *
     * @param boxes the boxes
     *
     * @return the union shape
     */
    static VoxelShape union(List<? extends AABB> boxes) {
        var shape = Shapes.empty();
        for (var box : boxes) {
            shape = Shapes.or(
                    shape,
                    Shapes.box(
                            box.minX, box.minY, box.minZ,
                            box.maxX, box.maxY, box.maxZ
                    )
            );
        }
        return shape;
    }

}
