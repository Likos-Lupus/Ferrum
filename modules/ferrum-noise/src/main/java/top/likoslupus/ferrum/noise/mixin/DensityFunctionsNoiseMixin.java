package top.likoslupus.ferrum.noise.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.likoslupus.ferrum.noise.NoiseHook;

/**
 * Routes the direct {@code DensityFunctions.Noise} leaf through the native grid batch.
 *
 * <p>Only the leaf is intercepted. Wrappers such as {@code ShiftedNoise}, other composites, and
 * unknown density shapes keep their vanilla implementation, and every ineligible call falls through.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public abstract class DensityFunctionsNoiseMixin {

    @Inject(
            method = "fillArray",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private void ferrum$fillArray(
            double[] ds,
            DensityFunction.ContextProvider arg,
            CallbackInfo callbackInfo
    ) {
        var accessor = (DensityFunctionsNoiseAccessor) this;
        if (NoiseHook.tryFillLeaf(
                accessor.ferrum$noise(),
                accessor.ferrum$xzScale(),
                accessor.ferrum$yScale(),
                ds,
                arg
        )) {
            callbackInfo.cancel();
        }
    }

}
