package top.likoslupus.ferrum.collide.mixin;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected {@code VoxelShape.shape} occupancy field so it can be serialized
 * (ADR-0019).
 */
@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor {

    /**
     * Returns the discrete occupancy behind this shape.
     *
     * @return the discrete shape
     */
    @Accessor("shape")
    DiscreteVoxelShape ferrum$shape();

}
