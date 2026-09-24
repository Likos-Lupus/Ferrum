package top.likoslupus.ferrum.nbt;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the Java-authored MUTF-8 golden corpus used by the Rust tests (ADR-0002).
 *
 * <p>Disabled by default; the {@code generateNbtGolden} Gradle task enables it. Java
 * {@code DataOutputStream.writeUTF} is the single source of truth for the encoding.
 */
class NbtGoldenGeneratorTest {

    private static final List<GoldenCase> CASES = List.of(
            new GoldenCase("empty", ""),
            new GoldenCase("ascii", "hello world"),
            new GoldenCase("boundary", "\u0001\u007F\u0080߿ࠀ\uFFFF"),
            new GoldenCase("nul-single", "\u0000"),
            new GoldenCase("nul-mixed", "a\u0000b"),
            new GoldenCase("two-byte", "café"),
            new GoldenCase("three-byte", "日本語"),
            new GoldenCase("surrogate", "\uD83D\uDE00"),
            new GoldenCase("lone-high", "\uD800"),
            new GoldenCase("lone-low", "\uDFFF"),
            new GoldenCase("long", "x".repeat(200)),
            new GoldenCase("mixed", "A\u0000é中\uD83D\uDE00Z")
    );

    @Test
    void generateMutf8Corpus() throws IOException {
        assumeTrue(
                Boolean.getBoolean("ferrum.generateNbtGolden"),
                "golden generation is disabled"
        );
        var directory = Path.of(System.getProperty("ferrum.golden.dir"));
        Files.createDirectories(directory);

        var manifest = new ArrayList<GoldenEntry>();
        for (var goldenCase : CASES) {
            var out = new ByteArrayOutputStream();
            new DataOutputStream(out).writeUTF(goldenCase.value());
            var bytes = out.toByteArray();

            Files.write(directory.resolve(goldenCase.name() + ".bin"), bytes);
            Files.write(
                    directory.resolve(goldenCase.name() + ".utf16le"),
                    utf16le(goldenCase.value())
            );
            manifest.add(new GoldenEntry(goldenCase.name(), bytes.length, sha256(bytes)));
        }

        Files.writeString(
                directory.resolve("manifest.json"),
                DataFormats.json().writeValueAsString(manifest)
        );
    }

    private static byte[] utf16le(String value) {
        var bytes = new byte[value.length() * 2];
        IntStream.range(0, value.length())
                .forEach(index -> {
                    var unit = value.charAt(index);
                    bytes[index * 2] = (byte) ((int) unit & 0xFF);
                    bytes[index * 2 + 1] = (byte) (((int) unit >> 8) & 0xFF);
                });
        return bytes;
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

    private record GoldenCase(
            String name,
            String value
    ) {

    }

    private record GoldenEntry(
            String name,
            int byteLength,
            String sha256
    ) {

    }

}
