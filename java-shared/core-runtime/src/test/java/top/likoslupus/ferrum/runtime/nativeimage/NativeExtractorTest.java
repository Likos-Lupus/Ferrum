package top.likoslupus.ferrum.runtime.nativeimage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativeExtractorTest {

    @Test
    void extractsVerifiedLibraryAndReusesIt(@TempDir Path tempDir) throws IOException {
        var bytes = "ferrum-native".getBytes(StandardCharsets.UTF_8);
        var sha = NativeExtractor.sha256Hex(bytes);
        var entry = new NativeManifestEntry(
                "linux-glibc-x86_64",
                "x",
                sha,
                1
        );

        var extracted = NativeExtractor.extract(entry, bytes, tempDir, "0.1.0");

        assertTrue(Files.isRegularFile(extracted));
        assertEquals(sha, NativeExtractor.sha256Hex(Files.readAllBytes(extracted)));
        assertTrue(extracted.toString().contains("linux-glibc-x86_64"));

        var again = NativeExtractor.extract(entry, bytes, tempDir, "0.1.0");
        assertEquals(extracted, again);
    }

    @Test
    void rejectsChecksumMismatch(@TempDir Path tempDir) {
        var bytes = "ferrum-native".getBytes(StandardCharsets.UTF_8);
        var entry = new NativeManifestEntry(
                "linux-glibc-x86_64",
                "x",
                "deadbeef",
                1
        );

        assertThrows(
                NativeExtractionException.class,
                () -> NativeExtractor.extract(entry, bytes, tempDir, "0.1.0")
        );
    }

    @Test
    void rejectsUnsupportedPlatform(@TempDir Path tempDir) {
        var bytes = "ferrum-native".getBytes(StandardCharsets.UTF_8);
        var sha = NativeExtractor.sha256Hex(bytes);
        var entry = new NativeManifestEntry(
                "plan9-x86_64",
                "x",
                sha,
                1
        );

        assertThrows(
                NativeExtractionException.class,
                () -> NativeExtractor.extract(entry, bytes, tempDir, "0.1.0")
        );
    }

}
