package top.likoslupus.ferrum.collide.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.likoslupus.ferrum.collide.CollideHook;

import java.util.List;

/**
 * Intercepts the entity movement sweep {@code Entity.collideWithShapes} (ADR-0019).
 *
 * <p>This is the single batch boundary for one movement: the adapter passes the moving box, the
 * desired movement, and every candidate shape; Rust computes the per-axis limits. Step-up handling
 * and world queries stay in Java.
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {

    @Inject(
            method = "collideWithShapes(Lnet/minecraft/world/phys/Vec3;"
                    + "Lnet/minecraft/world/phys/AABB;Ljava/util/List;)"
                    + "Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"UnusedMethod", "UnusedVariable"})
    private static void ferrum$collideWithShapes(
            Vec3 movement,
            AABB boundingBox,
            List<VoxelShape> shapes,
            CallbackInfoReturnable<Vec3> cir
    ) {
        var result = CollideHook.trySweep(movement, boundingBox, shapes);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

}
