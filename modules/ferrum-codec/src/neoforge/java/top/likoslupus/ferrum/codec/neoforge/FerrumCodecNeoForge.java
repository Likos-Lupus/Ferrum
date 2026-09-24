package top.likoslupus.ferrum.codec.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.likoslupus.ferrum.codec.CodecModule;

/**
 * NeoForge entrypoint: reports the codec capabilities. The native runtime itself is owned by
 * FerrumCore.
 */
@Mod("ferrum-codec")
public final class FerrumCodecNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-codec");

    public FerrumCodecNeoForge(IEventBus modEventBus) {
        LOGGER.info(
                "FerrumCodec {} capability={}",
                CodecModule.VERSION,
                CodecModule.CAPABILITY_LZ4
        );
    }

}
