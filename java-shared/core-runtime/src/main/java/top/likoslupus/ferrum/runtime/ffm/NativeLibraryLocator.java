package top.likoslupus.ferrum.runtime.ffm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Locates a locally built Ferrum native library for tests and development runs.
 */
public final class NativeLibraryLocator {

    private static final List<String> FILE_NAMES = List.of(
            "libferrum.so",
            "libferrum.dylib",
            "ferrum.dll"
    );
    private static final List<String> PROFILES = List.of("release", "debug");

    private NativeLibraryLocator() {
    }

    /**
     * Walks up from the working directory looking for a cargo-built Ferrum native library.
     *
     * @return the library path, or {@code null} when no build output is present
     */
    public static @Nullable Path find() {
        var directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            var target = directory
                    .resolve("native")
                    .resolve("ferrum-native")
                    .resolve("target");
            for (var profile : PROFILES) {
                for (var fileName : FILE_NAMES) {
                    var candidate = target.resolve(profile).resolve(fileName);
                    if (Files.isRegularFile(candidate)) {
                        return candidate;
                    }
                }
            }
            directory = directory.getParent();
        }
        return null;
    }

}
