package top.likoslupus.ferrum.codec;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.io.IOException;
import java.util.Random;

import org.jspecify.annotations.Nullable;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kernel benchmark: vanilla {@code lz4-java} versus the native block-stream codec.
 *
 * <p>Not part of {@code check}; the {@code codecBenchmark} task runs it against the release
 * library.
 */
@Tag("benchmark")
class Lz4KernelBenchmarkTest {

    private static final int WARMUP = 10;
    private static final int ITERATIONS = 50;

    @BeforeAll
    static void initializeRuntime() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), library);
        var runtime = FerrumRuntime.instance().nativeRuntime();
        assumeTrue(runtime != null && runtime.isAvailable(), "native runtime unavailable");
    }

    @AfterAll
    static void resetRuntime() {
        FerrumRuntime.instance().reset();
    }

    @Test
    void reportEncodeDecodeThroughput() throws IOException {
        IO.println(
                "bytes,vanillaDecodeNs,nativeDecodeNs,decodeRatio,vanillaEncodeNs,nativeEncodeNs,encodeRatio"
        );
        for (var size : new int[]{4096, 65_536, 1_048_576}) {
            var raw = random(size);
            var compressed = NativeLz4DifferentialTest.vanillaEncode(raw);

            var vanillaDecode = best(
                    () -> NativeLz4DifferentialTest.vanillaDecode(compressed)
            );
            var nativeDecode = best(() -> NativeLz4.decode(compressed));
            var vanillaEncode = best(() -> NativeLz4DifferentialTest.vanillaEncode(raw));
            var nativeEncode = best(() -> NativeLz4.encode(raw));

            System.out.printf(
                    "%d,%d,%d,%.3f,%d,%d,%.3f%n",
                    size,
                    vanillaDecode,
                    nativeDecode,
                    (double) vanillaDecode / nativeDecode,
                    vanillaEncode,
                    nativeEncode,
                    (double) vanillaEncode / nativeEncode
            );
        }
    }

    private static byte[] random(int length) {
        var bytes = new byte[length];
        new Random(9L).nextBytes(bytes);
        return bytes;
    }

    private static long best(Measured measured) throws IOException {
        for (var i = 0; i < WARMUP; i++) {
            measured.run();
        }
        var best = Long.MAX_VALUE;
        for (var i = 0; i < ITERATIONS; i++) {
            var start = System.nanoTime();
            measured.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    @FunctionalInterface
    private interface Measured {

        byte @Nullable [] run() throws IOException;

    }

}
