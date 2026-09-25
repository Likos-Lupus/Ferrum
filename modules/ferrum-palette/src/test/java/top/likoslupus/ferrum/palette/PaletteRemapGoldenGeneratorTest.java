package top.likoslupus.ferrum.palette;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the F-055 remap corpus: Java {@code SimpleBitStorage} is the exact oracle for the
 * packed output, and the Rust fused kernel is checked byte-for-byte against it.
 *
 * <p>Disabled by default; the {@code generatePaletteGolden} Gradle task enables it.
 */
class PaletteRemapGoldenGeneratorTest {

    private static final List<RemapCase> CASES = List.of(
            new RemapCase("b4-b5-s4096", 4, 5, 4096, 11L),
            new RemapCase("b5-b4-s4096", 5, 4, 4096, 12L),
            new RemapCase("b4-b8-s4096", 4, 8, 4096, 13L),
            new RemapCase("b8-b4-s4096", 8, 4, 4096, 14L),
            new RemapCase("b5-b5-s4096", 5, 5, 4096, 15L),
            new RemapCase("b1-b8-s4096", 1, 8, 4096, 16L)
    );

    @Test
    void generateRemapCorpus() throws IOException {
        assumeTrue(
                Boolean.getBoolean("ferrum.generateRemapGolden"),
                "palette remap golden generation is disabled"
        );
        var directory = Path.of(System.getProperty("ferrum.remap.golden.dir"));
        Files.createDirectories(directory);

        var manifest = new ArrayList<Entry>();
        for (var goldenCase : CASES) {
            var maskIn = mask(goldenCase.bitsIn());
            var maskOut = mask(goldenCase.bitsOut());
            var random = new Random(goldenCase.seed());
            var map = IntStream.range(0, 1 << goldenCase.bitsIn())
                    .map(_ -> (int) (random.nextLong() & maskOut))
                    .toArray();
            var values = IntStream.range(0, goldenCase.size())
                    .map(_ -> (int) (random.nextLong() & maskIn))
                    .toArray();

            var input = PaletteReference.pack(values, goldenCase.bitsIn());
            var output = PaletteReference.remap(
                    input,
                    goldenCase.bitsIn(),
                    goldenCase.size(),
                    map,
                    goldenCase.bitsOut()
            );

            Files.write(
                    directory.resolve(goldenCase.name() + ".in.raw"),
                    PaletteGoldenGeneratorTest.longs(input)
            );
            Files.write(
                    directory.resolve(goldenCase.name() + ".map"),
                    PaletteGoldenGeneratorTest.ints(map)
            );
            Files.write(
                    directory.resolve(goldenCase.name() + ".out.raw"),
                    PaletteGoldenGeneratorTest.longs(output)
            );
            manifest.add(new Entry(
                    goldenCase.name(),
                    goldenCase.bitsIn(),
                    goldenCase.bitsOut(),
                    goldenCase.size(),
                    PaletteGoldenGeneratorTest.sha256(PaletteGoldenGeneratorTest.longs(output))
            ));
        }

        Files.writeString(
                directory.resolve("manifest.json"),
                DataFormats.json().writeValueAsString(manifest)
        );
    }

    private static long mask(int bits) {
        return (1L << bits) - 1L;
    }

    @Test
    void goldenCaseNamesAvoidWindowsReservedNames() {
        var reserved = Set.of(
                "CON", "PRN", "AUX", "NUL",
                "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
                "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        );
        CASES.forEach(goldenCase -> assertFalse(
                reserved.contains(goldenCase.name().toUpperCase(Locale.ROOT)),
                () -> "golden case name is a Windows reserved device name: " + goldenCase.name()
        ));
    }

    private record RemapCase(
            String name,
            int bitsIn,
            int bitsOut,
            int size,
            long seed
    ) {

    }

    private record Entry(
            String name,
            int bitsIn,
            int bitsOut,
            int size,
            String sha256
    ) {

    }

}
