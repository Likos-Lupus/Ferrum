package top.likoslupus.ferrum.noise;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
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
 * Kernel benchmark: vanilla {@code NormalNoise.getValue} versus the native batch kernel.
 *
 * <p>Not part of {@code check}; the {@code noiseBenchmark} task runs it against the release
 * library. Results are exploratory only and never gate enablement (ADR-0017).
 */
@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
@Tag("benchmark")
class NoiseKernelBenchmarkTest {

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
        NoiseHandleCache.closeAll();
        FerrumRuntime.instance().reset();
    }

    @Test
    void reportKernelThroughput() {
        var normal = NormalNoise.create(
                RandomSource.create(42L),
                -4,
                1.0, 0.5, 0.25, 0.125
        );
        var handle = NativeNoise.createFromDescriptor(NoiseTestExtractor.normal(normal));
        assumeTrue(handle != 0L, "native noise handle unavailable");

        IO.println("samples,vanillaNs,nativeNs,ratio");
        IntStream.of(512, 4096, 65536)
                .forEach(samples -> {
                    var coordinates = coordinates(samples);
                    var xs = new double[samples];
                    var ys = new double[samples];
                    var zs = new double[samples];

                    IntStream.range(0, samples)
                            .forEach(index -> {
                                xs[index] = coordinates[index * 3];
                                ys[index] = coordinates[index * 3 + 1];
                                zs[index] = coordinates[index * 3 + 2];
                            });
                    var out = new double[samples];

                    var vanilla = best(() -> {
                        IntStream.range(0, samples)
                                .forEach(index -> out[index] = normal.getValue(
                                        xs[index],
                                        ys[index],
                                        zs[index]
                                ));
                        sink = out[0];
                    });
                    var nativeBest = best(() -> {
                        NativeNoise.batch(handle, xs, ys, zs, out, samples);
                        sink = out[0];
                    });

                    System.out.printf(
                            "%d,%d,%d,%.3f%n",
                            samples,
                            vanilla,
                            nativeBest,
                            (double) vanilla / nativeBest
                    );
                });

        NativeNoise.destroy(handle);
    }

    private static double[] coordinates(int samples) {
        var random = new Random(samples);
        var coordinates = new double[samples * 3];
        IntStream.range(0, samples)
                .forEach(index -> {
                    coordinates[index * 3] = random.nextDouble() * 2000.0 - 1000.0;
                    coordinates[index * 3 + 1] = random.nextDouble() * 256.0;
                    coordinates[index * 3 + 2] = random.nextDouble() * 2000.0 - 1000.0;
                });
        return coordinates;
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
