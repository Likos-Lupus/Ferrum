package top.likoslupus.ferrum.noise;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential tests: the native noise kernels must reproduce the vanilla raw bits for every kind.
 */
@Tag("native")
class NativeNoiseDifferentialTest {

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
    void normalNoiseMatchesRawBits() {
        LongStream.of(1L, 7L, 12345L)
                .forEach(seed -> {
                    var normal = NormalNoise.create(
                            RandomSource.create(seed),
                            -4,
                            1.0, 0.5, 0.25, 0.125
                    );
                    var coordinates = coordinates(seed);
                    var expected = IntStream.range(0, coordinates.length / 3)
                            .mapToDouble(index -> normal.getValue(
                                    coordinates[index * 3],
                                    coordinates[index * 3 + 1],
                                    coordinates[index * 3 + 2]
                            ))
                            .toArray();
                    var handle = NativeNoise.createFromDescriptor(NoiseTestExtractor.normal(normal));
                    assertNotEquals(0L, handle, "create failed seed=" + seed);
                    assertRawBitsMatch(handle, coordinates, expected);
                    NativeNoise.destroy(handle);
                });
    }

    private static double[] coordinates(long seed) {
        var random = new Random(seed);
        var samples = 128;
        var coordinates = new double[samples * 3];
        IntStream.range(0, samples)
                .forEach(index -> {
                    coordinates[index * 3] = random.nextDouble() * 2000.0 - 1000.0;
                    coordinates[index * 3 + 1] = random.nextDouble() * 256.0;
                    coordinates[index * 3 + 2] = random.nextDouble() * 2000.0 - 1000.0;
                });
        coordinates[0] = 0.0;
        coordinates[1] = 0.0;
        coordinates[2] = 0.0;
        coordinates[3] = 3.3554432E7 + 1.25;
        coordinates[4] = 2.5;
        coordinates[5] = -3.75;
        return coordinates;
    }

    private static void assertRawBitsMatch(
            long handle,
            double[] coordinates,
            double[] expected
    ) {
        var samples = expected.length;
        var xs = new double[samples];
        var ys = new double[samples];
        var zs = new double[samples];
        IntStream.range(0, samples)
                .forEach(index -> {
                    xs[index] = coordinates[index * 3];
                    ys[index] = coordinates[index * 3 + 1];
                    zs[index] = coordinates[index * 3 + 2];
                });
        var actual = new double[samples];
        assertTrue(NativeNoise.batch(handle, xs, ys, zs, actual, samples), "batch failed");
        IntStream.range(0, samples)
                .forEach(index -> assertEquals(
                        Double.doubleToRawLongBits(expected[index]),
                        Double.doubleToRawLongBits(actual[index]),
                        "raw-bits mismatch at sample " + index
                ));
    }

    @Test
    void perlinNoiseMatchesRawBits() {
        var perlin = PerlinNoise.create(
                RandomSource.create(99L),
                -3,
                1.0,
                0.75,
                0.25
        );
        var coordinates = coordinates(99L);
        var expected = IntStream.range(0, coordinates.length / 3)
                .mapToDouble(index -> perlin.getValue(
                        coordinates[index * 3],
                        coordinates[index * 3 + 1],
                        coordinates[index * 3 + 2]
                ))
                .toArray();

        var handle = NativeNoise.createFromDescriptor(
                NoiseTestExtractor.perlin(perlin)
        );
        assertNotEquals(0L, handle);
        assertRawBitsMatch(handle, coordinates, expected);
        NativeNoise.destroy(handle);
    }

    @Test
    void improvedNoiseMatchesRawBits() {
        var improved = new ImprovedNoise(RandomSource.create(77L));
        var coordinates = coordinates(77L);
        var expected = IntStream.range(0, coordinates.length / 3)
                .mapToDouble(index -> improved.noise(
                        coordinates[index * 3],
                        coordinates[index * 3 + 1],
                        coordinates[index * 3 + 2]
                ))
                .toArray();

        var handle = NativeNoise.createFromDescriptor(
                NoiseTestExtractor.improved(improved)
        );
        assertNotEquals(0L, handle);
        assertRawBitsMatch(handle, coordinates, expected);
        NativeNoise.destroy(handle);
    }

    @Test
    void createRejectsGarbageDescriptor() {
        assertEquals(0L, NativeNoise.createFromDescriptor(new byte[]{1, 2, 3, 4}));
        assertEquals(0L, NativeNoise.createFromDescriptor(new byte[0]));
    }

}
