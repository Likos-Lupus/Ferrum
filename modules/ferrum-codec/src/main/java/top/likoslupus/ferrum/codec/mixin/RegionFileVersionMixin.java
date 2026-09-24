package top.likoslupus.ferrum.codec.mixin;

import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.likoslupus.ferrum.codec.Lz4Hook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Routes the LZ4 region payload streams through the native block-stream fast path.
 *
 * <p>Only {@link RegionFileVersion#VERSION_LZ4} is intercepted; every other version and every
 * ineligible call falls through to the vanilla implementation unchanged.
 */
@Mixin(RegionFileVersion.class)
public abstract class RegionFileVersionMixin {

    @Inject(
            method = "wrap(Ljava/io/InputStream;)Ljava/io/InputStream;",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private void ferrum$wrapInput(
            InputStream input,
            CallbackInfoReturnable<InputStream> callbackInfo
    ) throws IOException {
        if ((Object) this != RegionFileVersion.VERSION_LZ4) {
            return;
        }
        var wrapped = Lz4Hook.wrapInput(input);
        if (wrapped != null) {
            callbackInfo.setReturnValue(wrapped);
        }
    }

    @Inject(
            method = "wrap(Ljava/io/OutputStream;)Ljava/io/OutputStream;",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private void ferrum$wrapOutput(
            OutputStream output,
            CallbackInfoReturnable<OutputStream> callbackInfo
    ) {
        if ((Object) this != RegionFileVersion.VERSION_LZ4) {
            return;
        }
        var wrapped = Lz4Hook.wrapOutput(output);
        if (wrapped != null) {
            callbackInfo.setReturnValue(wrapped);
        }
    }

}
