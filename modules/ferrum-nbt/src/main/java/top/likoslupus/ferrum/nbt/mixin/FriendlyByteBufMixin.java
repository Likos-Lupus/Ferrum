package top.likoslupus.ferrum.nbt.mixin;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.likoslupus.ferrum.nbt.NbtHook;

/**
 * Routes {@code FriendlyByteBuf.readNbt(ByteBuf)} through the native buffer fast path when it is
 * eligible; otherwise the vanilla implementation runs unchanged.
 */
@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufMixin {

    @Inject(
            method = "readNbt(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private static void ferrum$readNbt(
            ByteBuf input,
            CallbackInfoReturnable<CompoundTag> callbackInfo
    ) {
        if (NbtHook.tryReadNbt(input) instanceof NbtHook.ReadOutcome.Handled(var tag)) {
            callbackInfo.setReturnValue(tag);
        }
    }

}
