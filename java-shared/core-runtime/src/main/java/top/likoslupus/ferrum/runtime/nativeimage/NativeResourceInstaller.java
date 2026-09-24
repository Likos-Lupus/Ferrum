package top.likoslupus.ferrum.runtime.nativeimage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.runtime.PlatformId;

import java.io.IOException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Installs the native library carried inside the FerrumCore JAR.
 *
 * <p>Returns {@code null} on any missing or unusable resource so callers can fall back to a
 * development build or the Java path. Failures are never thrown.
 */
public final class NativeResourceInstaller {

    private static final String LIBRARY_PREFIX = "META-INF/ferrum/native/abi-1/";

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeResourceInstaller.class);

    private NativeResourceInstaller() {
    }

    /**
     * Extracts the packaged library for the current platform, if present.
     *
     * @param loader          the class loader that owns the mod resources
     * @param baseDir         the extraction cache root
     * @param expectedAbi     the ABI version this runtime understands
     * @param modVersion      the Ferrum mod version
     * @param verifyChecksums whether to verify the manifest SHA-256
     *
     * @return the extracted library path, or {@code null} when no usable resource is packaged
     */
    public static @Nullable Path install(
            ClassLoader loader,
            Path baseDir,
            int expectedAbi,
            String modVersion,
            boolean verifyChecksums
    ) {
        var platform = PlatformId.current();
        if (!platform.isSupported()) {
            return null;
        }

        try (var manifestStream = loader.getResourceAsStream(NativeExtractor.MANIFEST_RESOURCE)) {
            if (manifestStream == null) {
                return null;
            }

            var manifest = NativeManifest.read(manifestStream);
            if (manifest.abi() != expectedAbi) {
                LOGGER.warn("packaged native ABI {} != expected {}", manifest.abi(), expectedAbi);
                return null;
            }

            var entry = manifest.forPlatform(platform);
            if (entry == null) {
                return null;
            }

            try (var libraryStream = loader.getResourceAsStream(LIBRARY_PREFIX + entry.path())) {
                if (libraryStream == null) {
                    return null;
                }

                var bytes = libraryStream.readAllBytes();
                return NativeExtractor.extract(
                        entry,
                        bytes,
                        baseDir,
                        modVersion,
                        verifyChecksums
                );
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("packaged native library could not be installed", exception);
            return null;
        }
    }

}
