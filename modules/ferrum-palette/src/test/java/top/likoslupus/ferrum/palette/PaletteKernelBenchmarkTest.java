package top.likoslupus.ferrum.palette;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.util.Random;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kernel benchmark: vanilla {@code SimpleBitStorage} versus the native bulk pack/unpack.
 *
 * <p>Not part of {@code check}; the {@code paletteBenchmark} task runs it against the release
 * library.
 */
@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
@Tag("benchmark")
class PaletteKernelBenchmarkTest {

    private static final int WARMUP = 10;
    private static final int ITERATIONS = 50;

    private static @Nullable Object sink;

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
    void reportBulkThroughput() {
        IO.println(
                "bits,size,vanillaUnpackNs,nativeUnpackNs,unpackRatio,vanillaPackNs,nativePackNs,packRatio"
        );
        IntStream.of(4, 5, 8)
                .forEach(bits -> IntStream.of(256, 4096, 65_536)
                        .forEach(size -> {
                            var values = randomValues(bits, size);
                            var raw = PaletteReference.pack(values, bits);
                            var vanillaUnpack = best(() -> sink = PaletteReference.unpack(
                                    raw,
                                    bits,
                                    size
                            ));
                            var nativeUnpack = best(() -> {
                                var output = new int[size];
                                NativePalette.unpackInto(output, raw, bits, size);
                                sink = output;
                            });
                            var vanillaPack = best(() -> sink = PaletteReference.pack(
                                    values,
                                    bits
                            ));
                            var nativePack = best(() -> sink = NativePalette.pack(values, bits));
                            System.out.printf(
                                    "%d,%d,%d,%d,%.3f,%d,%d,%.3f%n",
                                    bits,
                                    size,
                                    vanillaUnpack,
                                    nativeUnpack,
                                    (double) vanillaUnpack / nativeUnpack,
                                    vanillaPack,
                                    nativePack,
                                    (double) vanillaPack / nativePack
                            );
                        }));
    }

    private static int[] randomValues(int bits, int size) {
        var mask = (1L << bits) - 1L;
        int[] values;
        var random = new Random(bits * 1000L + size);
        values = IntStream.range(0, size)
                .map(_ -> (int) (random.nextLong() & mask))
                .toArray();
        return values;
    }

    private static long best(Runnable action) {
        IntStream.range(0, WARMUP).forEach(_ -> action.run());
        var best = Long.MAX_VALUE;
        for (var index = 0; index < ITERATIONS; index++) {
            var start = System.nanoTime();
            action.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

}
