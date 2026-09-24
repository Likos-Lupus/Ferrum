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
        var result = installResult(
                loader,
                baseDir,
                expectedAbi,
                modVersion,
                verifyChecksums,
                PlatformId.current()
        );
        return result.status() == InstallStatus.INSTALLED
                ? result.library()
                : null;
    }

    /**
     * Extracts the packaged library, distinguishing "not packaged" from "packaged but broken".
     *
     * @param loader          the class loader that owns the mod resources
     * @param baseDir         the extraction cache root
     * @param expectedAbi     the ABI version this runtime understands
     * @param modVersion      the Ferrum mod version
     * @param verifyChecksums whether to verify the manifest SHA-256
     * @param platform        the platform to install for
     *
     * @return the install result; never {@code null}
     */
    public static InstallResult installResult(
            ClassLoader loader,
            Path baseDir,
            int expectedAbi,
            String modVersion,
            boolean verifyChecksums,
            PlatformId platform
    ) {
        if (!platform.isSupported()) {
            return new InstallResult(
                    InstallStatus.ABSENT,
                    null,
                    null,
                    "platform=" + platform.id()
            );
        }

        NativeManifest manifest;
        try (var manifestStream = loader.getResourceAsStream(NativeExtractor.MANIFEST_RESOURCE)) {
            if (manifestStream == null) {
                return new InstallResult(
                        InstallStatus.ABSENT,
                        null,
                        null,
                        "manifest-missing"
                );
            }
            manifest = NativeManifest.read(manifestStream);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("packaged native manifest could not be read", exception);
            return new InstallResult(
                    InstallStatus.INTEGRITY_FAILED,
                    null,
                    null,
                    "manifest-invalid"
            );
        }

        if (manifest.abi() != expectedAbi) {
            LOGGER.warn("packaged native ABI {} != expected {}", manifest.abi(), expectedAbi);
            return new InstallResult(
                    InstallStatus.ABI_MISMATCH,
                    null,
                    null,
                    "packaged-abi=" + manifest.abi()
            );
        }

        var entry = manifest.forPlatform(platform);
        if (entry == null) {
            return new InstallResult(
                    InstallStatus.ABSENT,
                    null,
                    null,
                    "platform-not-packaged"
            );
        }

        byte[] bytes;
        try (var libraryStream = loader.getResourceAsStream(LIBRARY_PREFIX + entry.path())) {
            if (libraryStream == null) {
                return new InstallResult(
                        InstallStatus.INTEGRITY_FAILED,
                        null,
                        null,
                        "library-resource-missing"
                );
            }
            bytes = libraryStream.readAllBytes();
        } catch (IOException exception) {
            LOGGER.warn("packaged native library could not be read", exception);
            return new InstallResult(
                    InstallStatus.INTEGRITY_FAILED,
                    null,
                    null,
                    "library-unreadable"
            );
        }

        try {
            var library = NativeExtractor.extract(
                    entry,
                    bytes,
                    baseDir,
                    modVersion,
                    verifyChecksums
            );
            return new InstallResult(
                    InstallStatus.INSTALLED,
                    library,
                    entry.sha256(),
                    null
            );
        } catch (NativeExtractionException exception) {
            LOGGER.warn("packaged native library failed verification", exception);
            return new InstallResult(
                    InstallStatus.INTEGRITY_FAILED,
                    null,
                    null,
                    "checksum-mismatch"
            );
        } catch (IOException exception) {
            LOGGER.warn("packaged native library could not be extracted", exception);
            return new InstallResult(
                    InstallStatus.INTEGRITY_FAILED,
                    null,
                    null,
                    "extraction-failed"
            );
        }
    }

    /**
     * Outcome of installing the packaged native library.
     */
    public enum InstallStatus {

        INSTALLED,
        ABSENT,
        ABI_MISMATCH,
        INTEGRITY_FAILED

    }

    /**
     * Result of installing the packaged native library.
     *
     * @param status  the outcome
     * @param library the extracted library when {@link InstallStatus#INSTALLED}, otherwise
     *                {@code null}
     * @param sha256  the manifest checksum when installed, otherwise {@code null}
     * @param reason  a human-readable reason when not installed, otherwise {@code null}
     */
    public record InstallResult(
            InstallStatus status,
            @Nullable Path library,
            @Nullable String sha256,
            @Nullable String reason
    ) {

    }

}
