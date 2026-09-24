package top.likoslupus.ferrum.palette.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.likoslupus.ferrum.palette.PaletteModule;

/**
 * NeoForge entrypoint: reports the palette capabilities. The native runtime itself is owned by
 * FerrumCore.
 */
@Mod("ferrum-palette")
public final class FerrumPaletteNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-palette");

    public FerrumPaletteNeoForge(IEventBus modEventBus) {
        LOGGER.info(
                "FerrumPalette {} capability={}",
                PaletteModule.VERSION,
                PaletteModule.CAPABILITY_BULK
        );
    }

}
