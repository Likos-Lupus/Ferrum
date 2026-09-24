package top.likoslupus.ferrum.nbt.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.likoslupus.ferrum.nbt.NbtModule;

/**
 * NeoForge entrypoint: reports the NBT capabilities. The native runtime itself is owned by
 * FerrumCore.
 */
@Mod("ferrum-nbt")
public final class FerrumNbtNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-nbt");

    public FerrumNbtNeoForge(IEventBus modEventBus) {
        LOGGER.info(
                "FerrumNbt {} capability={}",
                NbtModule.VERSION,
                NbtModule.CAPABILITY_BUFFER_IO
        );
    }

}
