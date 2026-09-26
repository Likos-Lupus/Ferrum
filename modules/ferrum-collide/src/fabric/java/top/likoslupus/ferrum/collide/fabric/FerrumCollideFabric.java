package top.likoslupus.ferrum.collide.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.collide.CollideModule;

/**
 * Fabric entrypoint: reports the collide capabilities. The native runtime and the world lifecycle
 * are owned by FerrumCore and Minecraft respectively; both collide batches are stateless, so there
 * are no handles to release here.
 */
public final class FerrumCollideFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-collide");

    @Override
    @SuppressWarnings("UnusedVariable")
    public void onInitialize() {
        LOGGER.info(
                "FerrumCollide {} capabilities={},{}",
                CollideModule.VERSION,
                CollideModule.CAPABILITY_AABB_CLIP,
                CollideModule.CAPABILITY_SHAPE_SWEEP
        );
    }

}
