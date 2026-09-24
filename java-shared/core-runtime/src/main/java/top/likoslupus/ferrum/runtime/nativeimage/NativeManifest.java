package top.likoslupus.ferrum.runtime.nativeimage;

import top.likoslupus.ferrum.runtime.PlatformId;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.io.InputStream;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The packaged native library manifest.
 *
 * <p>The manifest lists one {@link NativeManifestEntry} per supported platform. It is read through
 * the Jackson 3 {@link DataFormats} facade only.
 *
 * @param abi         the ABI version shared by every entry
 * @param rustVersion the Rust package version that produced the libraries
 * @param gitCommit   the git commit of the native build
 * @param libraries   the per-platform library entries
 */
public record NativeManifest(
        int abi,
        String rustVersion,
        String gitCommit,
        List<NativeManifestEntry> libraries
) {

    public NativeManifest {
        libraries = List.copyOf(libraries);
    }

    /**
     * Parses a manifest from JSON.
     *
     * @param input the JSON input stream
     *
     * @return the parsed manifest
     */
    public static NativeManifest read(InputStream input) {
        return DataFormats.json().readValue(input, NativeManifest.class);
    }

    /**
     * Finds the entry for a platform.
     *
     * @param platform the platform to look up
     *
     * @return the matching entry, or {@code null} when the platform is not packaged
     */
    public @Nullable NativeManifestEntry forPlatform(PlatformId platform) {
        return libraries.stream()
                .filter(entry ->
                        entry.platform().equalsIgnoreCase(platform.id())
                )
                .findFirst()
                .orElse(null);
    }

}
