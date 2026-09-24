package top.likoslupus.ferrum.codec.fabric;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.likoslupus.ferrum.codec.CodecModule;

/**
 * Fabric entrypoint: reports the codec capabilities. The native runtime itself is owned by
 * FerrumCore.
 */
public final class FerrumCodecFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ferrum-codec");

    @Override
    public void onInitialize() {
        LOGGER.info(
                "FerrumCodec {} capability={}",
                CodecModule.VERSION,
                CodecModule.CAPABILITY_LZ4
        );
    }

}
