package top.likoslupus.ferrum.collide.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.collide.CollideModule;

/**
 * NeoForge entrypoint: reports the collide capabilities. Both collide batches are stateless, so
 * there are no handles to release here.
 */
@Mod("ferrum-collide")
public final class FerrumCollideNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-collide");

    @SuppressWarnings("UnusedVariable")
    public FerrumCollideNeoForge(IEventBus modEventBus) {
        LOGGER.info(
                "FerrumCollide {} capabilities={},{}",
                CollideModule.VERSION,
                CollideModule.CAPABILITY_AABB_CLIP,
                CollideModule.CAPABILITY_SHAPE_SWEEP
        );
    }

}
