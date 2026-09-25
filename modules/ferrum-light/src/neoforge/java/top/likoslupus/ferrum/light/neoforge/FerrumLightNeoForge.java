package top.likoslupus.ferrum.light.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.light.LightModule;

/**
 * NeoForge entrypoint: reports the light capability. The block-light batch is stateless, so there
 * are no handles to release here.
 */
@Mod("ferrum-light")
public final class FerrumLightNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-light");

    @SuppressWarnings("UnusedVariable")
    public FerrumLightNeoForge(IEventBus modEventBus) {
        LOGGER.info(
                "FerrumLight {} capability={}",
                LightModule.VERSION,
                LightModule.CAPABILITY_BLOCK_BATCH
        );
    }

}
