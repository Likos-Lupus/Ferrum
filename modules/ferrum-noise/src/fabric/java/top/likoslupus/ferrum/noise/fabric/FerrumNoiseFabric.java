package top.likoslupus.ferrum.noise.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.noise.NoiseHandleCache;
import top.likoslupus.ferrum.noise.NoiseModule;

/**
 * Fabric entrypoint: reports the noise capabilities and releases cached handles when a world or
 * resource generation ends. The native runtime itself is owned by FerrumCore.
 */
public final class FerrumNoiseFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-noise");

    @Override
    @SuppressWarnings("UnusedVariable")
    public void onInitialize() {
        LOGGER.info(
                "FerrumNoise {} capabilities={},{}",
                NoiseModule.VERSION,
                NoiseModule.CAPABILITY_LEAF,
                NoiseModule.CAPABILITY_GRID
        );
        ServerLifecycleEvents.SERVER_STOPPED.register(_ -> NoiseHandleCache.closeAll());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (_, _, _) -> NoiseHandleCache.closeAll()
        );
    }

}
