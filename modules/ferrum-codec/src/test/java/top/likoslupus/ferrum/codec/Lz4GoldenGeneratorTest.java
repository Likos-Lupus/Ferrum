package top.likoslupus.ferrum.codec;

import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the Java-authored lz4-java block-stream corpus used by the Rust tests.
 *
 * <p>Disabled by default; the {@code generateLz4Golden} Gradle task enables it. Java
 * {@code LZ4BlockOutputStream} is the single source of truth for the framing.
 */
class Lz4GoldenGeneratorTest {

    private static final List<GoldenCase> CASES = List.of(
            new GoldenCase("empty", new byte[0]),
            new GoldenCase("one-byte", new byte[]{0x2A}),
            new GoldenCase("small-ascii", "hello world".getBytes(StandardCharsets.UTF_8)),
            new GoldenCase("repetitive-4k", repeated((byte) 'a', 4096)),
            new GoldenCase("repetitive-200k", repeated((byte) 'a', 200_000)),
            new GoldenCase("incompressible-4k", random(4096, 1L)),
            new GoldenCase("incompressible-200k", random(200_000, 1L)),
            new GoldenCase("block-boundary-65535", random(65_535, 2L)),
            new GoldenCase("block-boundary-65536", random(65_536, 3L)),
            new GoldenCase("block-boundary-65537", random(65_537, 4L)),
            new GoldenCase("mixed-128k", mixed(131_072))
    );

    private static byte[] repeated(byte value, int length) {
        var bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static byte[] mixed(int length) {
        var bytes = new byte[length];
        var half = length / 2;
        Arrays.fill(bytes, 0, half, (byte) 'z');
        var tail = random(length - half, 5L);
        System.arraycopy(tail, 0, bytes, half, tail.length);
        return bytes;
    }

    private static byte[] random(int length, long seed) {
        var bytes = new byte[length];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    @Test
    void generateLz4Corpus() throws IOException {
        assumeTrue(
                Boolean.getBoolean("ferrum.generateLz4Golden"),
                "LZ4 golden generation is disabled"
        );
        var directory = Path.of(System.getProperty("ferrum.golden.dir"));
        Files.createDirectories(directory);

        var manifest = new ArrayList<GoldenEntry>();
        for (var goldenCase : CASES) {
            var compressed = compress(goldenCase.raw());
            Files.write(directory.resolve(goldenCase.name() + ".lz4"), compressed);
            Files.write(directory.resolve(goldenCase.name() + ".raw"), goldenCase.raw());
            manifest.add(new GoldenEntry(
                    goldenCase.name(),
                    goldenCase.raw().length,
                    compressed.length,
                    sha256(compressed)
            ));
        }

        Files.writeString(
                directory.resolve("manifest.json"),
                DataFormats.json().writeValueAsString(manifest)
        );
    }

    static byte[] compress(byte[] raw) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var lz4 = new LZ4BlockOutputStream(out)) {
            lz4.write(raw);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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

    @SuppressWarnings("ArrayRecordComponent")
    private record GoldenCase(
            String name,
            byte[] raw
    ) {

    }

    private record GoldenEntry(
            String name,
            int rawLength,
            int compressedLength,
            String sha256
    ) {

    }

}
