package top.likoslupus.ferrum.palette.mixin;

import net.minecraft.util.SimpleBitStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.likoslupus.ferrum.palette.PaletteHook;

/**
 * Routes {@link SimpleBitStorage#unpack(int[])} through the native bulk unpack fast path.
 *
 * <p>Only {@code SimpleBitStorage} is intercepted. Single-value {@code get}/{@code set} and every
 * other {@code BitStorage} implementation are untouched, and every ineligible call falls through to
 * the vanilla implementation.
 */
@Mixin(SimpleBitStorage.class)
public abstract class SimpleBitStorageMixin {

    @Inject(
            method = "unpack([I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private void ferrum$unpack(int[] output, CallbackInfo callbackInfo) {
        var storage = (SimpleBitStorage) (Object) this;
        if (PaletteHook.tryUnpack(
                output,
                storage.getRaw(),
                storage.getBits(),
                storage.getSize()
        )) {
            callbackInfo.cancel();
        }
    }

}
