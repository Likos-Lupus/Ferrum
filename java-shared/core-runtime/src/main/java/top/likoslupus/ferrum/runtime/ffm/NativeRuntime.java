package top.likoslupus.ferrum.runtime.ffm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeRuntime.class);

    private static final long SELFTEST_INPUT = 0x5EED_1234L;

    private final @Nullable NativeBindings bindings;
    private final NativeRuntimeState state;
    private final @Nullable String reason;
    private final @Nullable NativeBuildInfo buildInfo;

    private NativeRuntime(
            @Nullable NativeBindings bindings,
            NativeRuntimeState state,
            @Nullable String reason,
            @Nullable NativeBuildInfo buildInfo
    ) {
        this.bindings = bindings;
        this.state = state;
        this.reason = reason;
        this.buildInfo = buildInfo;
    }

    /**
     * Attempts to load the native library and run the ABI self-check.
     *
     * @param library the library file to load
     *
     * @return a runtime whose {@link #state()} is {@link NativeRuntimeState#AVAILABLE} on success
     */
    public static NativeRuntime tryLoad(Path library) {
        return tryLoad(library, true);
    }

    /**
     * Attempts to load the native library and run the ABI self-check.
     *
     * <p>When {@code strictAbi} is {@code false}, an ABI version mismatch is logged and the
     * self-check continues; a struct-layout mismatch is always fatal because it would make every
     * call unsafe.
     *
     * @param library   the library file to load
     * @param strictAbi whether an ABI version mismatch is fatal
     *
     * @return a runtime whose {@link #state()} is {@link NativeRuntimeState#AVAILABLE} on success
     */
    public static NativeRuntime tryLoad(Path library, boolean strictAbi) {
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
                if (strictAbi) {
                    loaded.close();
                    return failed(
                            NativeRuntimeState.ABI_MISMATCH,
                            "abi=" + version
                    );
                }
                LOGGER.warn(
                        "native ABI {} != expected {}; continuing because strictAbi=false",
                        version,
                        NativeBindings.EXPECTED_ABI
                );
            }

            var buildInfo = loaded.buildInfo();
            if (validateBuildInfo(buildInfo) != NativeStatus.OK) {
                loaded.close();
                return failed(
                        NativeRuntimeState.ABI_MISMATCH,
                        "build-info-struct-size=" + buildInfo.structSize()
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
                    null,
                    buildInfo
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
        return new NativeRuntime(null, state, reason, null);
    }

    /**
     * Validates a decoded build-info struct against the layout this Java runtime understands.
     *
     * @param info the decoded build info
     *
     * @return {@link NativeStatus#OK} when compatible, otherwise {@link NativeStatus#ABI_MISMATCH}
     */
    static NativeStatus validateBuildInfo(NativeBuildInfo info) {
        if (info.structSize() != NativeBindings.BUILD_INFO_SIZE
                || info.abiVersion() != NativeBindings.EXPECTED_ABI
        ) {
            return NativeStatus.ABI_MISMATCH;
        }
        return NativeStatus.OK;
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
     * Returns the native build information.
     *
     * @return the decoded build information
     */
    public NativeBuildInfo buildInfo() {
        var current = buildInfo;
        if (current == null) {
            throw new IllegalStateException("native runtime not available: " + state);
        }
        return current;
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

    /**
     * Returns the typed NBT bindings.
     *
     * @return the NBT bindings, or {@code null} when native is not available or the symbols are
     * absent
     */
    public @Nullable NbtBindings nbt() {
        var current = bindings;
        return current != null && state.isAvailable()
                ? current.nbt()
                : null;
    }

    /**
     * Returns the typed codec bindings.
     *
     * @return the codec bindings, or {@code null} when native is not available or the symbols are
     * absent
     */
    public @Nullable CodecBindings codec() {
        var current = bindings;
        return current != null && state.isAvailable()
                ? current.codec()
                : null;
    }

    /**
     * Returns the typed palette bindings.
     *
     * @return the palette bindings, or {@code null} when native is not available or the symbols are
     * absent
     */
    public @Nullable PaletteBindings palette() {
        var current = bindings;
        return current != null && state.isAvailable()
                ? current.palette()
                : null;
    }

    @Override
    public void close() {
        var current = bindings;
        if (current != null) {
            current.close();
        }
    }

}
