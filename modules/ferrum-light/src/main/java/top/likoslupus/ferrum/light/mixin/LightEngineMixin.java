package top.likoslupus.ferrum.light.mixin;

import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.likoslupus.ferrum.light.LightHook;

/**
 * Intercepts the inherited {@code LightEngine.runLightUpdates} path only for a block-light engine
 * (ADR-0018). The block engine has no override of its own, so this mixin on the base class guards
 * on the receiver type instead of moving the boundary up to {@code LevelLightEngine}.
 */
@Mixin(LightEngine.class)
public abstract class LightEngineMixin {

    @Inject(
            method = "runLightUpdates",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private void ferrum$runLightUpdates(CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof BlockLightEngine)) {
            return;
        }
        var accessor = (LightEngineAccessor) this;
        var processed = LightHook.tryRunNative((BlockLightEngine) (Object) this, accessor);
        if (processed >= 0) {
            cir.setReturnValue(processed);
        }
    }

}
