package top.likoslupus.ferrum.core.mixin;

import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.likoslupus.ferrum.core.FerrumCore;

@Mixin(SharedConstants.class)
public abstract class SharedConstantsMixin {

    @Inject(
            method = "<clinit>",
            at = @At("RETURN")
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private static void ferrum$markMixinApplied(CallbackInfo callbackInfo) {
        FerrumCore.markMixinApplied();
    }

}
