package top.likoslupus.ferrum.runtime.nativeimage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.likoslupus.ferrum.runtime.PlatformId;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NativeResourceInstallerTest {

    private static final String LIBRARY = "ferrum-test-library";

    @Test
    void returnsNullWhenNothingIsPackaged(@TempDir Path tempDir) {
        var installed = NativeResourceInstaller.install(
                getClass().getClassLoader(),
                tempDir,
                1,
                "0.1.0",
                true
        );

        assertNull(installed);
    }

    @Test
    void installerReportsAbsentWhenNothingIsPackaged(@TempDir Path resources) throws Exception {
        try (var loader = classLoaderOver(resources)) {
            var result = NativeResourceInstaller.installResult(
                    loader,
                    resources.resolve("cache"),
                    1,
                    "0.1.0",
                    true,
                    PlatformId.LINUX_GLIBC_X86_64
            );

            assertEquals(NativeResourceInstaller.InstallStatus.ABSENT, result.status());
            assertEquals("manifest-missing", result.reason());
        }
    }

    private static URLClassLoader classLoaderOver(Path root) throws Exception {
        return new URLClassLoader(new URL[]{root.toUri().toURL()}, null);
    }

    @Test
    void installsVerifiedLibrary(@TempDir Path resources) throws Exception {
        var bytes = LIBRARY.getBytes(StandardCharsets.UTF_8);
        var sha = NativeExtractor.sha256Hex(bytes);
        writeManifest(resources, 1, "linux-glibc-x86_64", sha);
        writeLibrary(resources, "linux-glibc-x86_64", bytes);

        try (var loader = classLoaderOver(resources)) {
            var result = NativeResourceInstaller.installResult(
                    loader,
                    resources.resolve("cache"),
                    1,
                    "0.1.0",
                    true,
                    PlatformId.LINUX_GLIBC_X86_64
            );

            assertEquals(NativeResourceInstaller.InstallStatus.INSTALLED, result.status());
            assertEquals(sha, result.sha256());
            var library = result.library();
            assertNotNull(library);
            assertTrue(Files.isRegularFile(library));
        }
    }

    private static void writeManifest(
            Path resources,
            int abi,
            String platform,
            String sha
    ) throws Exception {
        var manifest = /* language=JSON */ """
                {
                  "abi": %d,
                  "rustVersion": "0.1.0",
                  "gitCommit": "0000000000000000000000000000000000000000",
                  "libraries": [
                    {
                      "platform": "%s",
                      "path": "%s/libferrum.so",
                      "sha256": "%s",
                      "abi": %d
                    }
                  ]
                }
                """.formatted(abi, platform, platform, sha, abi);
        var path = resources.resolve("META-INF/ferrum/native/abi-1/manifest.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, manifest);
    }

    private static void writeLibrary(
            Path resources,
            String platform,
            byte[] bytes
    ) throws Exception {
        var path = resources
                .resolve("META-INF/ferrum/native/abi-1")
                .resolve(platform)
                .resolve("libferrum.so");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    @Test
    void tamperedLibraryFailsIntegrity(@TempDir Path resources) throws Exception {
        var bytes = LIBRARY.getBytes(StandardCharsets.UTF_8);
        writeManifest(resources, 1, "linux-glibc-x86_64", "0".repeat(64));
        writeLibrary(resources, "linux-glibc-x86_64", bytes);

        try (var loader = classLoaderOver(resources)) {
            var result = NativeResourceInstaller.installResult(
                    loader,
                    resources.resolve("cache"),
                    1,
                    "0.1.0",
                    true,
                    PlatformId.LINUX_GLIBC_X86_64
            );

            assertEquals(NativeResourceInstaller.InstallStatus.INTEGRITY_FAILED, result.status());
            assertEquals("checksum-mismatch", result.reason());
        }
    }

    @Test
    void packagedAbiMismatchIsReported(@TempDir Path resources) throws Exception {
        var bytes = LIBRARY.getBytes(StandardCharsets.UTF_8);
        var sha = NativeExtractor.sha256Hex(bytes);
        writeManifest(resources, 2, "linux-glibc-x86_64", sha);
        writeLibrary(resources, "linux-glibc-x86_64", bytes);

        try (var loader = classLoaderOver(resources)) {
            var result = NativeResourceInstaller.installResult(
                    loader,
                    resources.resolve("cache"),
                    1,
                    "0.1.0",
                    true,
                    PlatformId.LINUX_GLIBC_X86_64
            );

            assertEquals(NativeResourceInstaller.InstallStatus.ABI_MISMATCH, result.status());
            assertEquals("packaged-abi=2", result.reason());
        }
    }

    @Test
    void platformNotPackagedIsAbsent(@TempDir Path resources) throws Exception {
        var bytes = LIBRARY.getBytes(StandardCharsets.UTF_8);
        var sha = NativeExtractor.sha256Hex(bytes);
        writeManifest(resources, 1, "windows-x86_64", sha);
        writeLibrary(resources, "windows-x86_64", bytes);

        try (var loader = classLoaderOver(resources)) {
            var result = NativeResourceInstaller.installResult(
                    loader,
                    resources.resolve("cache"),
                    1,
                    "0.1.0",
                    true,
                    PlatformId.LINUX_GLIBC_X86_64
            );

            assertEquals(NativeResourceInstaller.InstallStatus.ABSENT, result.status());
            assertEquals("platform-not-packaged", result.reason());
        }
    }

}
