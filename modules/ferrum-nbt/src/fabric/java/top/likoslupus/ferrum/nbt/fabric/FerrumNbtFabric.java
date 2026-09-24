package top.likoslupus.ferrum.nbt.fabric;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.likoslupus.ferrum.nbt.NbtModule;

/**
 * Fabric entrypoint: reports the NBT capabilities. The native runtime itself is owned by
 * FerrumCore.
 */
public final class FerrumNbtFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-nbt");

    @Override
    public void onInitialize() {
        LOGGER.info(
                "FerrumNbt {} capability={}",
                NbtModule.VERSION,
                NbtModule.CAPABILITY_BUFFER_IO
        );
    }

}
