package top.likoslupus.ferrum.codec;

import net.jpountz.lz4.LZ4BlockInputStream;

import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Mixin-facing entry point for the LZ4 region block-stream fast path.
 *
 * <p>Eligibility is conservative: the runtime must be available, the codec module enabled and
 * advertised, and the {@code lz4} and {@code accelerateExistingLz4} options on. If the native
 * kernel declines for any reason, the original bytes are handed to {@code lz4-java} so valid and
 * corrupt input behave exactly like vanilla.
 */
public final class Lz4Hook {

    /** The largest compressed payload the fast path will buffer. */
    public static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;

    private Lz4Hook() {
    }

    /**
     * Wraps a region payload stream with the native decoder when eligible.
     *
     * @param input the payload stream
     *
     * @return the decoded stream, or {@code null} to leave the vanilla path untouched
     *
     * @throws IOException when the underlying stream cannot be read
     */
    @SuppressWarnings("deprecation")
    public static @Nullable InputStream wrapInput(InputStream input) throws IOException {
        var settings = eligibleSettings();
        if (settings == null) {
            return null;
        }

        var available = input.available();
        if (available <= 0
                || available < settings.minBatch()
                || available > MAX_PAYLOAD_BYTES
        ) {
            return null;
        }

        var payload = input.readAllBytes();
        var decoded = NativeLz4.decode(payload);

        return decoded != null
                ? new ByteArrayInputStream(decoded)
                : new LZ4BlockInputStream(new ByteArrayInputStream(payload));
    }

    private static @Nullable ModuleSettings eligibleSettings() {
        var runtime = FerrumRuntime.instance();
        if (!runtime.isAvailable()
                || !runtime.config().isModuleEnabled(ModuleId.CODEC)
                || !NativeFeatures.isSupported(runtime.featureBits(), ModuleId.CODEC)
        ) {
            return null;
        }

        var settings = runtime.config().modules().get(CodecModule.MODULE_ID);
        var options = Optional.ofNullable(settings)
                .map(CodecOptions::from)
                .orElseGet(CodecOptions::defaults);

        return !options.lz4() || !options.accelerateExistingLz4()
                ? null
                : settings;
    }

    /**
     * Wraps a region payload stream with the native encoder when eligible.
     *
     * @param output the payload stream
     *
     * @return the encoding stream, or {@code null} to leave the vanilla path untouched
     */
    public static @Nullable OutputStream wrapOutput(OutputStream output) {
        return eligibleSettings() == null
                ? null
                : new NativeLz4OutputStream(output);
    }

}
