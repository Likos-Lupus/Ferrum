package top.likoslupus.ferrum.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNullElse;

/**
 * Normalized native platform identifier.
 *
 * <p>The set of values mirrors the supported native build targets; {@link #UNSUPPORTED} is used
 * when no supported platform can be determined. Each value carries the stable identifier used by
 * the native manifest and the file name of its shared library.
 */
public enum PlatformId {

    WINDOWS_X86_64("windows-x86_64", "ferrum.dll"),
    WINDOWS_AARCH64("windows-aarch64", "ferrum.dll"),
    MACOS_X86_64("macos-x86_64", "libferrum.dylib"),
    MACOS_AARCH64("macos-aarch64", "libferrum.dylib"),
    LINUX_GLIBC_X86_64("linux-glibc-x86_64", "libferrum.so"),
    LINUX_GLIBC_AARCH64("linux-glibc-aarch64", "libferrum.so"),
    LINUX_MUSL_X86_64("linux-musl-x86_64", "libferrum.so"),
    LINUX_MUSL_AARCH64("linux-musl-aarch64", "libferrum.so"),
    UNSUPPORTED("unsupported", "libferrum.so");

    private final String id;
    private final String libraryFileName;

    PlatformId(
            String id,
            String libraryFileName
    ) {
        this.id = id;
        this.libraryFileName = libraryFileName;
    }

    /**
     * Detects the current platform from JVM system properties.
     *
     * <p>The full identifier can be forced with the {@code ferrum.platform} system property, and
     * the Linux libc family with {@code ferrum.libc} ({@code glibc} or {@code musl}). When the libc
     * family cannot be determined reliably, glibc is assumed.
     *
     * @return the current platform, or {@link #UNSUPPORTED} when it cannot be determined
     */
    public static PlatformId current() {
        var override = System.getProperty("ferrum.platform");
        if (override != null && !override.isBlank()) {
            return fromId(override);
        }

        var osName = requireNonNullElse(System.getProperty("os.name"), "");
        var osArch = requireNonNullElse(System.getProperty("os.arch"), "");
        var libcOverride = System.getProperty("ferrum.libc");
        var libc = libcOverride != null && !libcOverride.isBlank()
                ? libcOverride
                : detectLibcFamily(osName);
        return from(osName, osArch, libc);
    }

    /**
     * Resolves a stable platform identifier.
     *
     * @param id the identifier such as {@code linux-glibc-x86_64}
     *
     * @return the matching platform, or {@link #UNSUPPORTED}
     */
    public static PlatformId fromId(String id) {
        return Arrays.stream(values())
                .filter(platform -> platform.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(UNSUPPORTED);
    }

    private static String detectLibcFamily(String osName) {
        if (!osName.toLowerCase(Locale.ROOT).startsWith("linux")) {
            return "glibc";
        }
        try {
            var maps = Files.readString(Path.of("/proc/self/maps"));
            return maps.contains("musl")
                    ? "musl"
                    : "glibc";
        } catch (IOException exception) {
            return "glibc";
        }
    }

    /**
     * Maps raw OS, architecture, and libc names to a platform identifier.
     *
     * @param osName     the {@code os.name} value
     * @param osArch     the {@code os.arch} value
     * @param libcFamily the Linux libc family ({@code glibc} or {@code musl}); ignored elsewhere
     *
     * @return the matching platform, or {@link #UNSUPPORTED}
     */
    public static PlatformId from(
            String osName,
            String osArch,
            String libcFamily
    ) {
        var os = osName.toLowerCase(Locale.ROOT);
        var arch = normalizeArch(osArch);
        if (arch == null) {
            return UNSUPPORTED;
        }

        if (os.startsWith("windows")) {
            return switch (arch) {
                case "x86_64" -> WINDOWS_X86_64;
                case "aarch64" -> WINDOWS_AARCH64;
                default -> UNSUPPORTED;
            };
        }

        if (os.startsWith("mac") || os.contains("darwin") || os.startsWith("os x")) {
            return switch (arch) {
                case "x86_64" -> MACOS_X86_64;
                case "aarch64" -> MACOS_AARCH64;
                default -> UNSUPPORTED;
            };
        }

        if (os.startsWith("linux")) {
            var musl = "musl".equalsIgnoreCase(libcFamily);
            return switch (arch) {
                case "x86_64" -> musl
                        ? LINUX_MUSL_X86_64
                        : LINUX_GLIBC_X86_64;
                case "aarch64" -> musl
                        ? LINUX_MUSL_AARCH64
                        : LINUX_GLIBC_AARCH64;
                default -> UNSUPPORTED;
            };
        }

        return UNSUPPORTED;
    }

    private static @Nullable String normalizeArch(String osArch) {
        return switch (osArch.toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64", "x86-64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            default -> null;
        };
    }

    /**
     * Returns the stable platform identifier used by the native manifest.
     *
     * @return the platform identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the platform-specific shared library file name.
     *
     * @return the library file name
     */
    public String libraryFileName() {
        return libraryFileName;
    }

    /**
     * Returns whether this is a supported native platform.
     *
     * @return {@code true} when this is a supported native platform
     */
    public boolean isSupported() {
        return this != UNSUPPORTED;
    }

}
