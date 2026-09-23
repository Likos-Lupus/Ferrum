package top.likoslupus.ferrum.runtime;

/**
 * Lifecycle states of the native runtime initialization.
 *
 * <p>Initialization runs once. Every state other than {@link #AVAILABLE} is an explainable
 * fallback and must never surface as an unhandled exception to a loader entrypoint.
 */
public enum NativeRuntimeState {

    UNINITIALIZED,
    DISABLED_BY_CONFIG,
    UNSUPPORTED_JAVA,
    PLATFORM_UNSUPPORTED,
    EXTRACT_FAILED,
    LOAD_FAILED,
    ABI_MISMATCH,
    SELFTEST_FAILED,
    AVAILABLE;

    /**
     * Returns whether the native runtime is usable.
     *
     * @return {@code true} when the native runtime is usable
     */
    public boolean isAvailable() {
        return this == AVAILABLE;
    }

}
