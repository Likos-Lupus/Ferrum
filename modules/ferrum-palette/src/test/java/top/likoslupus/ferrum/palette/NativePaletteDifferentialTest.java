package top.likoslupus.ferrum.palette;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential tests: the native palette bulk pack/unpack must match the vanilla
 * {@code SimpleBitStorage} bit layout for every width and tail shape.
 */
@Tag("native")
class NativePaletteDifferentialTest {

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
    void nativePackMatchesVanillaForEveryWidthAndSize() {
        IntStream.rangeClosed(1, 32)
                .forEach(bits -> IntStream.of(1, 7, 16, 17, 64, 257, 4096)
                        .forEach(size -> {
                            var values = randomValues(bits, size, bits * 31L + size);
                            var expected = PaletteReference.pack(values, bits);
                            var actual = NativePalette.pack(values, bits);
                            assertNotNull(
                                    actual,
                                    "native pack failed bits=" + bits + " size=" + size
                            );
                            assertArrayEquals(
                                    expected,
                                    actual,
                                    "pack mismatch bits=" + bits + " size=" + size
                            );
                        })
                );
    }

    private static int[] randomValues(
            int bits,
            int size,
            long seed
    ) {
        var mask = PaletteGoldenGeneratorTest.valueMask(bits);
        int[] values;
        var random = new Random(seed);
        values = IntStream.range(0, size)
                .map(_ ->
                        (int) (random.nextLong() & mask)
                ).toArray();
        return values;
    }

    @Test
    void nativeUnpackMatchesVanillaForEveryWidthAndSize() {
        IntStream.rangeClosed(1, 32)
                .forEach(bits -> IntStream.of(1, 7, 16, 17, 64, 257, 4096)
                        .forEach(size -> {
                            var values = randomValues(bits, size, bits * 17L + size);
                            var raw = PaletteReference.pack(values, bits);
                            var output = new int[size];
                            assertTrue(
                                    NativePalette.unpackInto(output, raw, bits, size),
                                    "native unpack failed bits=" + bits + " size=" + size
                            );
                            assertArrayEquals(
                                    values,
                                    output,
                                    "unpack mismatch bits=" + bits + " size=" + size
                            );
                        })
                );
    }

    @Test
    void unpackFallsBackForInvalidInputs() {
        var raw = PaletteReference.pack(new int[]{1, 2, 3, 4}, 4);
        assertFalse(NativePalette.unpackInto(new int[4], raw, 0, 4));
        assertFalse(NativePalette.unpackInto(new int[4], raw, 33, 4));
        assertFalse(NativePalette.unpackInto(new int[3], raw, 4, 4));
        assertFalse(NativePalette.unpackInto(new int[4], new long[0], 4, 4));
    }

    @Test
    void edgeValueSetsRoundTrip() {
        var allZero = new int[64];
        assertArrayEquals(allZero, roundTrip(allZero, 5));
        var allOnes = new int[64];
        Arrays.fill(allOnes, (1 << 5) - 1);
        assertArrayEquals(allOnes, roundTrip(allOnes, 5));
    }

    private static int[] roundTrip(int[] values, int bits) {
        var raw = PaletteReference.pack(values, bits);
        var output = new int[values.length];
        assertTrue(NativePalette.unpackInto(output, raw, bits, values.length));
        return output;
    }

}
