package top.likoslupus.ferrum.runtime.config;

/**
 * Global native runtime settings.
 *
 * @param enabled         whether the native runtime may be enabled at all
 * @param strictAbi       whether an ABI mismatch disables the whole native runtime
 * @param verifyChecksums whether packaged native libraries are checksum-verified before loading
 * @param diagnostics     whether per-call diagnostics counters are exposed
 */
public record NativeSettings(
        boolean enabled,
        boolean strictAbi,
        boolean verifyChecksums,
        boolean diagnostics
) {

}
