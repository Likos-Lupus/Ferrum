package top.likoslupus.ferrum.palette;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;
import top.likoslupus.ferrum.runtime.ffm.NativeStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static java.util.Objects.requireNonNull;

/**
 * F-055 remap spike: measures the Java baseline, the composed existing-ABI path, and the fused
 * test-hook path through FFM, and checks every fused result byte-for-byte against the Java
 * {@code SimpleBitStorage} oracle.
 *
 * <p>Not part of {@code check}; the {@code paletteRemapSpike} task runs it against the release
 * test-hooks library. This test does not wire anything into Minecraft and does not touch the stable
 * ABI.
 */
@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
@Tag("spike")
class PaletteRemapSpikeTest {

    private static final int WARMUP = 20;
    private static final int ITERATIONS = 100;

    private static final int[][] CASES = {
            {4, 5, 4096},
            {5, 4, 4096},
            {4, 8, 4096},
            {8, 4, 4096},
            {5, 5, 4096},
            {3, 4, 4096},
            {6, 5, 4096},
            {4, 8, 257},
            {8, 4, 257}
    };

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
    void reportRemapPaths() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");
        try (
                var bindings = PaletteRemapTestBindings.load(
                        requireNonNull(library, "native library")
                )
        ) {
            assumeTrue(bindings != null, "test-hooks library not built");

            IO.println(
                    "case,bitsIn,bitsOut,size,javaNs,composedNs,fusedFfmNs,composedVsJava,fusedVsJava"
            );
            Arrays.stream(CASES)
                    .forEach(goldenCase -> {
                        var bitsIn = goldenCase[0];
                        var bitsOut = goldenCase[1];
                        var size = goldenCase[2];
                        var random = new Random(bitsIn * 1_000_003L + bitsOut * 101L + size);
                        var maskIn = (1L << bitsIn) - 1L;
                        var maskOut = (1L << bitsOut) - 1L;
                        var map = IntStream.range(0, 1 << bitsIn)
                                .map(_ -> (int) (random.nextLong() & maskOut))
                                .toArray();
                        var values = IntStream.range(0, size)
                                .map(_ -> (int) (random.nextLong() & maskIn))
                                .toArray();
                        var input = PaletteReference.pack(values, bitsIn);
                        var expected = PaletteReference.remap(input, bitsIn, size, map, bitsOut);
                        var fused = fused(bindings, input, bitsIn, size, map, bitsOut);

                        assertNotNull(fused, "fused remap failed");
                        assertArrayEquals(expected, fused, "fused remap mismatch");

                        var javaNs = best(() -> sink = PaletteReference.remap(
                                input,
                                bitsIn,
                                size,
                                map,
                                bitsOut
                        ));
                        var composedNs = best(() -> sink = composed(
                                input,
                                bitsIn,
                                size,
                                map,
                                bitsOut
                        ));
                        var fusedNs = best(() -> sink = fused(
                                bindings,
                                input,
                                bitsIn,
                                size,
                                map,
                                bitsOut
                        ));

                        System.out.printf(
                                "%d-%d-s%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f%n",
                                bitsIn,
                                bitsOut,
                                size,
                                bitsIn,
                                bitsOut,
                                size,
                                javaNs,
                                composedNs,
                                fusedNs,
                                (double) javaNs / composedNs,
                                (double) javaNs / fusedNs
                        );
                    });
        }
    }

    private static long @Nullable [] fused(
            PaletteRemapTestBindings bindings,
            long[] input,
            int bitsIn,
            int size,
            int[] map,
            int bitsOut
    ) {
        var requiredOut = requiredLongs(size, bitsOut);
        try (var arena = Arena.ofConfined()) {
            var inputSegment = arena.allocate(ValueLayout.JAVA_LONG, input.length);
            MemorySegment.copy(input, 0, inputSegment, ValueLayout.JAVA_LONG, 0, input.length);
            var mapSegment = arena.allocate(ValueLayout.JAVA_INT, map.length);
            MemorySegment.copy(map, 0, mapSegment, ValueLayout.JAVA_INT, 0, map.length);
            var outputSegment = arena.allocate(ValueLayout.JAVA_LONG, requiredOut);
            var written = arena.allocate(ValueLayout.JAVA_LONG);

            var status = bindings.remap(
                    inputSegment,
                    input.length,
                    bitsIn,
                    size,
                    mapSegment,
                    map.length,
                    bitsOut,
                    outputSegment,
                    requiredOut,
                    written
            );
            if (status != NativeStatus.OK) {
                return null;
            }
            var output = new long[requiredOut];
            MemorySegment.copy(
                    outputSegment,
                    ValueLayout.JAVA_LONG,
                    0,
                    output,
                    0,
                    requiredOut
            );
            return output;
        }
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

    private static long @Nullable [] composed(
            long[] input,
            int bitsIn,
            int size,
            int[] map,
            int bitsOut
    ) {
        var unpacked = new int[size];
        if (!NativePalette.unpackInto(unpacked, input, bitsIn, size)) {
            return null;
        }
        var mapped = IntStream.range(0, size)
                .map(index -> map[unpacked[index]])
                .toArray();
        return NativePalette.pack(mapped, bitsOut);
    }

    private static int requiredLongs(int size, int bits) {
        var perLong = 64 / bits;
        return (size + perLong - 1) / perLong;
    }

}
