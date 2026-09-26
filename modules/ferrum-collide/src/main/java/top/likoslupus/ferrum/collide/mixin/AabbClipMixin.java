package top.likoslupus.ferrum.collide.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.likoslupus.ferrum.collide.CollideHook;

import java.util.List;

/**
 * Intercepts the per-list {@code AABB.clip(Iterable, Vec3, Vec3, BlockPos)} path (ADR-0019).
 *
 * <p>Only re-iterable {@code List} inputs are eligible; anything else falls back to vanilla. The
 * vanilla result is {@code new BlockHitResult(point, direction, pos, false)}, which the hook
 * reproduces exactly.
 */
@Mixin(AABB.class)
public abstract class AabbClipMixin {

    @Inject(
            method = "clip(Ljava/lang/Iterable;Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)"
                    + "Lnet/minecraft/world/phys/BlockHitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private static void ferrum$clip(
            Iterable<AABB> aabBs,
            Vec3 from,
            Vec3 _to,
            BlockPos pos,
            CallbackInfoReturnable<BlockHitResult> cir
    ) {
        if (!(aabBs instanceof List<AABB> list)) {
            return;
        }
        var result = CollideHook.tryClip(list, pos, from, _to);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

}
