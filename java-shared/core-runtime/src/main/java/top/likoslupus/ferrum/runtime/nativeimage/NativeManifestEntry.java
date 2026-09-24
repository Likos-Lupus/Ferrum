package top.likoslupus.ferrum.runtime.nativeimage;

/**
 * One platform's native library entry in a {@link NativeManifest}.
 *
 * @param platform the stable platform identifier
 * @param path     the classpath-relative resource path of the library
 * @param sha256   the lowercase hex SHA-256 of the library bytes
 * @param abi      the ABI version the library implements
 */
public record NativeManifestEntry(
        String platform,
        String path,
        String sha256,
        int abi
) {

}
