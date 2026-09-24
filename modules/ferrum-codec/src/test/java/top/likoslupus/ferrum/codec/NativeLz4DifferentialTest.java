package top.likoslupus.ferrum.codec;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential tests: the native LZ4 block-stream codec must interoperate with vanilla
 * {@code lz4-java} in both directions.
 */
@Tag("native")
class NativeLz4DifferentialTest {

    @BeforeAll
    static void initializeRuntime() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), library);
        var runtime = FerrumRuntime.instance().nativeRuntime();
        assumeTrue(
                runtime != null && runtime.isAvailable(),
                "native runtime unavailable"
        );
    }

    @AfterAll
    static void resetRuntime() {
        FerrumRuntime.instance().reset();
    }

    @Test
    void nativeDecodesVanillaStreams() throws IOException {
        for (var raw : samples()) {
            var compressed = vanillaEncode(raw);
            var decoded = NativeLz4.decode(compressed);
            assertNotNull(
                    decoded,
                    "native decode failed for " + raw.length + " bytes"
            );
            assertArrayEquals(
                    raw,
                    decoded,
                    "native decode mismatch for " + raw.length + " bytes"
            );
        }
    }

    private static byte[][] samples() {
        return new byte[][]{
                new byte[0],
                {0x2A},
                "hello world".getBytes(StandardCharsets.UTF_8),
                repeated((byte) 'a', 4096),
                repeated((byte) 'b', 200_000),
                random(4096, 11L),
                random(200_000, 12L),
                random(65_537, 13L),
        };
    }

    static byte[] vanillaEncode(byte[] raw) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var lz4 = new LZ4BlockOutputStream(out)) {
            lz4.write(raw);
        }
        return out.toByteArray();
    }

    private static byte[] repeated(byte value, int length) {
        var bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static byte[] random(int length, long seed) {
        var bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    @Test
    void vanillaDecodesNativeStreams() throws IOException {
        for (var raw : samples()) {
            var compressed = NativeLz4.encode(raw);
            assertNotNull(
                    compressed,
                    "native encode failed for " + raw.length + " bytes"
            );
            assertArrayEquals(
                    raw,
                    vanillaDecode(compressed),
                    "vanilla decode mismatch for " + raw.length + " bytes"
            );
        }
    }

    @SuppressWarnings("deprecation")
    static byte[] vanillaDecode(byte[] compressed) throws IOException {
        try (var in = new LZ4BlockInputStream(new ByteArrayInputStream(compressed))) {
            return in.readAllBytes();
        }
    }

    @Test
    void nativeOutputStreamProducesVanillaReadableStreams() throws IOException {
        var raw = repeated((byte) 'q', 100_000);
        var out = new ByteArrayOutputStream();
        try (var stream = new NativeLz4OutputStream(out)) {
            stream.write(raw);
        }

        var compressed = out.toByteArray();
        assertArrayEquals(raw, vanillaDecode(compressed));
        assertArrayEquals(raw, NativeLz4.decode(compressed));
    }

    @Test
    void corruptStreamIsRejectedLikeVanilla() throws IOException {
        var compressed = vanillaEncode(repeated((byte) 'w', 4096));
        compressed[9 + 12] = (byte) (compressed[9 + 12] ^ 0xFF);

        assertNull(NativeLz4.decode(compressed));
        assertThrows(IOException.class, () -> vanillaDecode(compressed));
    }

    @Test
    void emptyPayloadRoundTrips() throws IOException {
        var compressed = NativeLz4.encode(new byte[0]);
        assertNotNull(compressed);
        assertArrayEquals(new byte[0], vanillaDecode(compressed));
        assertArrayEquals(new byte[0], NativeLz4.decode(compressed));
    }

}
