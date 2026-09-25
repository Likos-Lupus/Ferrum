package top.likoslupus.ferrum.light.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.light.LightModule;

/**
 * Fabric entrypoint: reports the light capability. The native runtime and the light engine's
 * lifecycle are owned by FerrumCore and Minecraft respectively; the block-light batch is stateless,
 * so there are no handles to release here.
 */
public final class FerrumLightFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-light");

    @Override
    @SuppressWarnings("UnusedVariable")
    public void onInitialize() {
        LOGGER.info(
                "FerrumLight {} capability={}",
                LightModule.VERSION,
                LightModule.CAPABILITY_BLOCK_BATCH
        );
    }

}
