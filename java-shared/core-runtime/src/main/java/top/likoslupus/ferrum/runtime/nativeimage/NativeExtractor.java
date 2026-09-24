package top.likoslupus.ferrum.runtime.nativeimage;

import top.likoslupus.ferrum.runtime.PlatformId;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Verifies and extracts a native library into a content-addressed cache directory.
 *
 * <p>Extraction is integrity-first: the SHA-256 of the library bytes must match the manifest, and
 * the final file appears only via an atomic move so a concurrent or interrupted run can never load
 * a half-written library.
 */
public final class NativeExtractor {

    /** The classpath location of the packaged manifest. */
    public static final String MANIFEST_RESOURCE = "META-INF/ferrum/native/abi-1/manifest.json";

    private static final int SHA_PREFIX_LENGTH = 16;

    private NativeExtractor() {
    }

    /**
     * Extracts a verified library into
     * {@code <baseDir>/<modVersion>/abi-<abi>/<platform>/<sha12>}.
     *
     * <p>When the destination already exists it is reused without rewriting.
     *
     * @param entry        the manifest entry describing the library
     * @param libraryBytes the library bytes read from the JAR
     * @param baseDir      the cache root (for example {@code <gameDir>/.ferrum/native})
     * @param modVersion   the Ferrum mod version
     *
     * @return the path of the verified library
     *
     * @throws NativeExtractionException when the checksum does not match or the platform is
     *                                   unsupported
     * @throws IOException               when writing or moving the library fails
     */
    public static Path extract(
            NativeManifestEntry entry,
            byte[] libraryBytes,
            Path baseDir,
            String modVersion
    ) throws IOException {
        return extract(entry, libraryBytes, baseDir, modVersion, true);
    }

    /**
     * Extracts a library into the content-addressed cache.
     *
     * @param entry           the manifest entry describing the library
     * @param libraryBytes    the library bytes read from the JAR
     * @param baseDir         the cache root
     * @param modVersion      the Ferrum mod version
     * @param verifyChecksums whether the SHA-256 must match the manifest before extracting
     *
     * @return the path of the extracted library
     *
     * @throws NativeExtractionException when verification is enabled and the checksum does not
     *                                   match, or the platform is unsupported
     * @throws IOException               when writing or moving the library fails
     */
    public static Path extract(
            NativeManifestEntry entry,
            byte[] libraryBytes,
            Path baseDir,
            String modVersion,
            boolean verifyChecksums
    ) throws IOException {
        var actual = sha256Hex(libraryBytes);
        if (verifyChecksums && !actual.equalsIgnoreCase(entry.sha256())) {
            throw new NativeExtractionException(
                    "checksum mismatch for %s: expected %s but was %s".formatted(
                            entry.platform(),
                            entry.sha256(),
                            actual
                    )
            );
        }

        var platform = PlatformId.fromId(entry.platform());
        if (!platform.isSupported()) {
            throw new NativeExtractionException("unsupported platform: " + entry.platform());
        }

        var directory = baseDir
                .resolve(modVersion)
                .resolve("abi-" + entry.abi())
                .resolve(platform.id())
                .resolve(actual.substring(0, SHA_PREFIX_LENGTH));
        var target = directory.resolve(platform.libraryFileName());
        if (Files.isRegularFile(target)) {
            return target;
        }

        Files.createDirectories(directory);
        var temporary = directory.resolve(platform.libraryFileName() + ".tmp-" + UUID.randomUUID());
        Files.write(temporary, libraryBytes);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    /**
     * Computes the lowercase hex SHA-256 of the given bytes.
     *
     * @param bytes the input bytes
     *
     * @return the hex digest
     */
    public static String sha256Hex(byte[] bytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

}
