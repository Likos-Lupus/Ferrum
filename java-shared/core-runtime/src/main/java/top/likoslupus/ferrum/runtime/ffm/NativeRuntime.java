package top.likoslupus.ferrum.runtime.ffm;

import top.likoslupus.ferrum.runtime.NativeRuntimeState;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

/**
 * Owns a loaded Ferrum native library and its ABI self-check.
 *
 * <p>Use {@link #tryLoad(Path)} to obtain an instance. Loading never throws: any failure is
 * reported through {@link #state()} so callers can fall back to the Java path. Initialization runs
 * once; every state other than {@link NativeRuntimeState#AVAILABLE} is an explainable fallback.
 */
public final class NativeRuntime implements AutoCloseable {

    private static final long SELFTEST_INPUT = 0x5EED_1234L;

    private final @Nullable NativeBindings bindings;
    private final NativeRuntimeState state;
    private final @Nullable String reason;

    private NativeRuntime(
            @Nullable NativeBindings bindings,
            NativeRuntimeState state,
            @Nullable String reason
    ) {
        this.bindings = bindings;
        this.state = state;
        this.reason = reason;
    }

    /**
     * Attempts to load the native library and run the ABI self-check.
     *
     * @param library the library file to load
     *
     * @return a runtime whose {@link #state()} is {@link NativeRuntimeState#AVAILABLE} on success
     */
    public static NativeRuntime tryLoad(Path library) {
        NativeBindings loaded;
        try {
            loaded = NativeBindings.load(library);
        } catch (RuntimeException | LinkageError throwable) {
            return failed(
                    NativeRuntimeState.LOAD_FAILED,
                    throwable.getClass().getSimpleName()
            );
        }

        try {
            var version = loaded.abiVersion();
            if (version != NativeBindings.EXPECTED_ABI) {
                loaded.close();
                return failed(
                        NativeRuntimeState.ABI_MISMATCH,
                        "abi=" + version
                );
            }

            var outcome = loaded.invokeSelftest(SELFTEST_INPUT);
            if (!outcome.isOk()) {
                loaded.close();
                return failed(
                        NativeRuntimeState.SELFTEST_FAILED,
                        "status=" + outcome.status()
                );
            }

            return new NativeRuntime(
                    loaded,
                    NativeRuntimeState.AVAILABLE,
                    null
            );
        } catch (RuntimeException | LinkageError throwable) {
            loaded.close();
            return failed(
                    NativeRuntimeState.SELFTEST_FAILED,
                    throwable.getClass().getSimpleName()
            );
        }
    }

    private static NativeRuntime failed(NativeRuntimeState state, @Nullable String reason) {
        return new NativeRuntime(null, state, reason);
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return the runtime state
     */
    public NativeRuntimeState state() {
        return state;
    }

    /**
     * Returns whether the native runtime is usable.
     *
     * @return {@code true} when native calls are available
     */
    public boolean isAvailable() {
        return state.isAvailable();
    }

    /**
     * Returns the fallback reason, when not available.
     *
     * @return a human-readable reason, or {@code null}
     */
    public @Nullable String reason() {
        return reason;
    }

    /**
     * Returns the ABI version reported by the library.
     *
     * @return the ABI version
     */
    public int abiVersion() {
        return requireBindings().abiVersion();
    }

    private NativeBindings requireBindings() {
        var current = bindings;
        if (current == null || !state.isAvailable()) {
            throw new IllegalStateException("native runtime not available: " + state);
        }

        return current;
    }

    /**
     * Returns the feature bits reported by the library.
     *
     * @return the advertised feature bits
     */
    public long featureBits() {
        return requireBindings().featureBits();
    }

    /**
     * Reads the native build information.
     *
     * @return the decoded build information
     */
    public NativeBuildInfo buildInfo() {
        return requireBindings().buildInfo();
    }

    /**
     * Runs the deterministic selftest checksum.
     *
     * @param input the selftest input
     *
     * @return the checksum value
     */
    public long selftest(long input) {
        return requireBindings().selftest(input);
    }

    /**
     * Runs the deterministic selftest checksum, surfacing its status.
     *
     * @param input the selftest input
     *
     * @return the outcome, carrying the checksum on success
     */
    public NativeOutcome<Long> invokeSelftest(long input) {
        return requireBindings().invokeSelftest(input);
    }

    @Override
    public void close() {
        var current = bindings;
        if (current != null) {
            current.close();
        }
    }

}
