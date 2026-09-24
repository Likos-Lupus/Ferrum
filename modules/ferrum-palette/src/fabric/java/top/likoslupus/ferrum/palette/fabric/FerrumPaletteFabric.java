package top.likoslupus.ferrum.palette.fabric;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.likoslupus.ferrum.palette.PaletteModule;

/**
 * Fabric entrypoint: reports the palette capabilities. The native runtime itself is owned by
 * FerrumCore.
 */
public final class FerrumPaletteFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-palette");

    @Override
    public void onInitialize() {
        LOGGER.info(
                "FerrumPalette {} capability={}",
                PaletteModule.VERSION,
                PaletteModule.CAPABILITY_BULK
        );
    }

}
