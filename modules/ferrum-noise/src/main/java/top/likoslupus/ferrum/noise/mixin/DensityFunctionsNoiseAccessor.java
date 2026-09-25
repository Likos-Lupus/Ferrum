package top.likoslupus.ferrum.noise.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the record components of the protected {@code DensityFunctions.Noise} leaf.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public interface DensityFunctionsNoiseAccessor {

    @Accessor("noise")
    DensityFunction.NoiseHolder ferrum$noise();

    @Accessor("xzScale")
    double ferrum$xzScale();

    @Accessor("yScale")
    double ferrum$yScale();

}
