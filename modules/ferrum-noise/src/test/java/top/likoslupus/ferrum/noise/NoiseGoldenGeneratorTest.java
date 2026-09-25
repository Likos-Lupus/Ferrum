package top.likoslupus.ferrum.noise;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the Java-authored noise descriptor corpus used by the Rust tests (ADR-0017).
 *
 * <p>Disabled by default; the {@code generateNoiseGolden} Gradle task enables it. The live vanilla
 * {@code NormalNoise} / {@code PerlinNoise} / {@code ImprovedNoise} objects are the single source
 * of truth for both the descriptor and the expected raw bits.
 */
class NoiseGoldenGeneratorTest {

    private static final double[][] BASE_COORDS = {
            {0.0, 0.0, 0.0},
            {1.0, 0.0, 0.0},
            {0.0, 1.0, 0.0},
            {0.0, 0.0, 1.0},
            {-1.0, -1.0, -1.0},
            {0.5, 0.5, 0.5},
            {1.0E-7, -1.0E-7, 1.0E-7},
            {-1.0E-7, 2.0E-7, -3.0E-7},
            {16.0, 64.0, 16.0},
            {-16.5, -64.25, 16.25},
            {15.999999, 7.9999999, -0.0000001},
            {1.0E6, 1.0E6, 1.0E6},
            {-1.0E6, -1.0E6, -1.0E6},
            {3.3554432E7, 0.0, 0.0},
            {3.3554432E7 + 1.25, 2.5, -3.75},
            {-3.3554432E7 - 0.5, 1.5, 0.25},
            {6.7108864E7, 3.3554432E7, -6.7108864E7}
    };

    @Test
    void generateNoiseCorpus() throws IOException {
        assumeTrue(
                Boolean.getBoolean("ferrum.generateNoiseGolden"),
                "noise golden generation is disabled"
        );
        var directory = Path.of(System.getProperty("ferrum.noise.golden.dir"));
        Files.createDirectories(directory);

        var manifest = new ArrayList<Entry>();
        for (var goldenCase : cases()) {
            Files.write(
                    directory.resolve(goldenCase.name() + ".desc"),
                    goldenCase.descriptor()
            );
            Files.write(
                    directory.resolve(goldenCase.name() + ".coords"),
                    doubles(goldenCase.coords())
            );
            Files.write(
                    directory.resolve(goldenCase.name() + ".expected"),
                    longs(goldenCase.expected())
            );
            manifest.add(new Entry(
                    goldenCase.name(),
                    goldenCase.expected().length,
                    sha256(goldenCase.descriptor())
            ));
        }

        Files.writeString(
                directory.resolve("manifest.json"),
                DataFormats.json().writeValueAsString(manifest)
        );
    }

    private static List<Case> cases() {
        var cases = new ArrayList<Case>();
        cases.add(normalCase(
                "n-basic",
                1L,
                0,
                1.0, 1.0, 1.0
        ));
        cases.add(normalCase(
                "n-first-negative",
                2L,
                -4,
                1.0, 0.5, 0.25, 0.125
        ));
        cases.add(normalCase(
                "n-zero-amplitude",
                3L,
                0,
                1.0, 0.0, 1.0
        ));
        cases.add(normalCase(
                "n-many-octaves",
                4L,
                -8,
                1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0
        ));
        cases.add(perlinCase(
                "p-basic",
                10L,
                0,
                1.0,
                1.0, 0.5
        ));
        cases.add(perlinCase(
                "p-negative",
                11L,
                -3,
                1.0,
                0.75, 0.25
        ));
        cases.add(improvedCase(
                "i-basic",
                20L
        ));
        cases.add(improvedCase(
                "i-other",
                21L
        ));
        return cases;
    }

    private static byte[] doubles(double[] values) {
        var buffer = ByteBuffer
                .allocate(values.length * Double.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        Arrays.stream(values).forEach(buffer::putDouble);
        return buffer.array();
    }

    private static byte[] longs(long[] values) {
        var buffer = ByteBuffer
                .allocate(values.length * Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        Arrays.stream(values).forEach(buffer::putLong);
        return buffer.array();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Case normalCase(
            String name,
            long seed,
            int firstOctave,
            double... amplitudes
    ) {
        var normal = NormalNoise.create(RandomSource.create(seed), firstOctave, amplitudes);
        var descriptor = NoiseTestExtractor.normal(normal);
        var coords = coordinates(seed);
        var expected = IntStream.range(0, coords.length / 3)
                .mapToLong(index -> Double.doubleToRawLongBits(normal.getValue(
                        coords[index * 3],
                        coords[index * 3 + 1],
                        coords[index * 3 + 2]
                )))
                .toArray();
        return new Case(name, descriptor, coords, expected);
    }

    private static Case perlinCase(
            String name,
            long seed,
            int firstOctave,
            double firstAmplitude,
            double... amplitudes
    ) {
        var perlin = PerlinNoise.create(
                RandomSource.create(seed),
                firstOctave,
                firstAmplitude,
                amplitudes
        );
        var descriptor = NoiseTestExtractor.perlin(perlin);
        var coords = coordinates(seed);
        var expected = IntStream.range(0, coords.length / 3)
                .mapToLong(index -> Double.doubleToRawLongBits(perlin.getValue(
                        coords[index * 3],
                        coords[index * 3 + 1],
                        coords[index * 3 + 2]
                )))
                .toArray();
        return new Case(name, descriptor, coords, expected);
    }

    private static Case improvedCase(String name, long seed) {
        var improved = new ImprovedNoise(RandomSource.create(seed));
        var descriptor = NoiseTestExtractor.improved(improved);
        var coords = coordinates(seed);
        var expected = IntStream.range(0, coords.length / 3)
                .mapToLong(index -> Double.doubleToRawLongBits(improved.noise(
                        coords[index * 3],
                        coords[index * 3 + 1],
                        coords[index * 3 + 2]
                )))
                .toArray();
        return new Case(name, descriptor, coords, expected);
    }

    private static double[] coordinates(long seed) {
        var random = new Random(seed);
        var extra = 24;
        var coords = new double[(BASE_COORDS.length + extra) * 3];
        var index = 0;
        for (var coordinate : BASE_COORDS) {
            coords[index] = coordinate[0];
            index++;
            coords[index] = coordinate[1];
            index++;
            coords[index] = coordinate[2];
            index++;
        }
        for (var sample = 0; sample < extra; sample++) {
            coords[index] = random.nextDouble() * 2000.0 - 1000.0;
            index++;
            coords[index] = random.nextDouble() * 256.0;
            index++;
            coords[index] = random.nextDouble() * 2000.0 - 1000.0;
            index++;
        }
        return coords;
    }

    @SuppressWarnings("ArrayRecordComponent")
    private record Case(
            String name,
            byte[] descriptor,
            double[] coords,
            long[] expected
    ) {

    }

    private record Entry(
            String name,
            int samples,
            String descriptorSha256
    ) {

    }

}
