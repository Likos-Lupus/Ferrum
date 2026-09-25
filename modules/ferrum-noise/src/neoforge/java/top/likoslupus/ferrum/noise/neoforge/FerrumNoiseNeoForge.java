package top.likoslupus.ferrum.noise.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.noise.NoiseHandleCache;
import top.likoslupus.ferrum.noise.NoiseModule;

/**
 * NeoForge entrypoint: reports the noise capabilities and releases cached handles when the server
 * stops. The native runtime itself is owned by FerrumCore.
 */
@Mod("ferrum-noise")
public final class FerrumNoiseNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-noise");

    public FerrumNoiseNeoForge(IEventBus modEventBus) {
        LOGGER.info(
                "FerrumNoise {} capabilities={},{}",
                NoiseModule.VERSION,
                NoiseModule.CAPABILITY_LEAF,
                NoiseModule.CAPABILITY_GRID
        );
        NeoForge.EVENT_BUS.addListener(FerrumNoiseNeoForge::onServerStopped);
    }

    @SuppressWarnings("UnusedVariable")
    private static void onServerStopped(ServerStoppedEvent event) {
        NoiseHandleCache.closeAll();
    }

}
