package top.likoslupus.ferrum.runtime;

/**
 * Normalized native platform identifier.
 *
 * <p>The set of values mirrors the supported native build targets; {@link #UNSUPPORTED} is used
 * when no supported platform can be determined.
 */
public enum PlatformId {

    WINDOWS_X86_64,
    WINDOWS_AARCH64,
    MACOS_X86_64,
    MACOS_AARCH64,
    LINUX_GLIBC_X86_64,
    LINUX_GLIBC_AARCH64,
    LINUX_MUSL_X86_64,
    LINUX_MUSL_AARCH64,
    UNSUPPORTED;

    /**
     * Returns whether this is a supported native platform.
     *
     * @return {@code true} when this is a supported native platform
     */
    public boolean isSupported() {
        return this != UNSUPPORTED;
    }

}
