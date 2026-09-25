package top.likoslupus.ferrum.palette;

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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the Java-authored {@code SimpleBitStorage} corpus used by the Rust tests (ADR-0015).
 *
 * <p>Disabled by default; the {@code generatePaletteGolden} Gradle task enables it. Java
 * {@code SimpleBitStorage} is the single source of truth for the bit layout.
 */
class PaletteGoldenGeneratorTest {

    private static final List<Case> CASES = cases();

    private static List<Case> cases() {
        var cases = IntStream.of(1, 2, 3, 4, 5, 6, 7, 8, 16, 31, 32)
                .mapToObj(bits -> new Case(
                        "b" + bits + "-s257",
                        bits,
                        257,
                        1L + bits
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        IntStream.of(4, 5, 8, 16, 32)
                .mapToObj(bits -> new Case(
                        "b" + bits + "-s4096",
                        bits,
                        4096,
                        100L + bits
                ))
                .forEach(cases::add);
        return List.copyOf(cases);
    }

    @Test
    void generatePaletteCorpus() throws IOException {
        assumeTrue(
                Boolean.getBoolean("ferrum.generatePaletteGolden"),
                "palette golden generation is disabled"
        );
        var directory = Path.of(System.getProperty("ferrum.golden.dir"));
        Files.createDirectories(directory);

        var manifest = new ArrayList<Entry>();
        for (var goldenCase : CASES) {
            var mask = valueMask(goldenCase.bits());
            var random = new Random(goldenCase.seed());
            var values = IntStream.range(0, goldenCase.size())
                    .map(_ -> (int) (random.nextLong() & mask))
                    .toArray();
            var raw = PaletteReference.pack(values, goldenCase.bits());

            Files.write(directory.resolve(goldenCase.name() + ".raw"), longs(raw));
            Files.write(directory.resolve(goldenCase.name() + ".values"), ints(values));
            manifest.add(new Entry(
                    goldenCase.name(),
                    goldenCase.bits(),
                    goldenCase.size(),
                    sha256(longs(raw))
            ));
        }

        Files.writeString(
                directory.resolve("manifest.json"),
                DataFormats.json().writeValueAsString(manifest)
        );
    }

    /**
     * Returns the value mask the Java oracle can represent. {@code SimpleBitStorage.set} takes a
     * signed {@code int} and validates it against the unsigned mask, so a 32-bit value with the top
     * bit set cannot be authored through the oracle; the full 32-bit range is covered by the Rust
     * property tests, which use the native entry points directly.
     */
    static long valueMask(int bits) {
        return bits == 32
                ? 0x7FFF_FFFFL
                : (1L << bits) - 1L;
    }

    static byte[] longs(long[] values) {
        var buffer = ByteBuffer
                .allocate(values.length * Long.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        Arrays.stream(values).forEach(buffer::putLong);
        return buffer.array();
    }

    static byte[] ints(int[] values) {
        var buffer = ByteBuffer
                .allocate(values.length * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        Arrays.stream(values).forEach(buffer::putInt);
        return buffer.array();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
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

    private record Case(
            String name,
            int bits,
            int size,
            long seed
    ) {

    }

    private record Entry(
            String name,
            int bits,
            int size,
            String sha256
    ) {

    }

}
