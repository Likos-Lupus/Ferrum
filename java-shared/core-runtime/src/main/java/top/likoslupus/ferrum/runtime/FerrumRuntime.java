package top.likoslupus.ferrum.runtime;

import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.diagnostics.FerrumStatus;
import top.likoslupus.ferrum.runtime.diagnostics.FerrumStatusReporter;
import top.likoslupus.ferrum.runtime.ffm.NativeCall;
import top.likoslupus.ferrum.runtime.ffm.NativeCircuitBreaker;
import top.likoslupus.ferrum.runtime.ffm.NativeDiagnostics;
import top.likoslupus.ferrum.runtime.ffm.NativeRuntime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Central wiring of the Ferrum native runtime.
 *
 * <p>The loader glue initializes this once at startup. It owns the single {@link NativeRuntime},
 * the shared counters, the shared circuit breaker, and the single {@link NativeCall} wrapper that
 * every module goes through. Initialization never throws: every failure is captured as a
 * {@link NativeRuntimeState} plus a reason so callers fall back to the Java path.
 */
public final class FerrumRuntime implements AutoCloseable {

    private static final FerrumRuntime INSTANCE = new FerrumRuntime();

    private final NativeDiagnostics diagnostics = new NativeDiagnostics();
    private final NativeCircuitBreaker circuitBreaker = new NativeCircuitBreaker();

    private volatile FerrumConfig config = FerrumConfig.defaults();
    private volatile PlatformId platform = PlatformId.current();
    private volatile NativeRuntimeState state = NativeRuntimeState.UNINITIALIZED;
    private volatile @Nullable String reason;
    private volatile @Nullable NativeRuntime runtime;
    private volatile @Nullable NativeCall nativeCall;

    private FerrumRuntime() {
    }

    /**
     * Returns the process-wide runtime holder.
     *
     * @return the singleton instance
     */
    public static FerrumRuntime instance() {
        return INSTANCE;
    }

    /**
     * Initializes the runtime from a configuration and a library path.
     *
     * @param config  the active configuration
     * @param library the native library to load, or {@code null} when none is available
     */
    public synchronized void initialize(FerrumConfig config, @Nullable Path library) {
        this.config = Objects.requireNonNull(config, "config");
        closeRuntime();

        if (!config.isNativeEnabled()) {
            state = NativeRuntimeState.DISABLED_BY_CONFIG;
            reason = "native-disabled";
            return;
        }
        if (!platform.isSupported()) {
            state = NativeRuntimeState.PLATFORM_UNSUPPORTED;
            reason = "platform=" + platform.id();
            return;
        }
        if (library == null) {
            state = NativeRuntimeState.LOAD_FAILED;
            reason = "library-not-found";
            return;
        }

        var loaded = NativeRuntime.tryLoad(library);
        runtime = loaded;
        state = loaded.state();
        reason = loaded.reason();
        nativeCall = loaded.isAvailable()
                ? new NativeCall(loaded, circuitBreaker, diagnostics, config::isModuleEnabled)
                : null;
    }

    private void closeRuntime() {
        var current = runtime;
        if (current != null) {
            current.close();
        }
        runtime = null;
        nativeCall = null;
    }

    /**
     * Returns the runtime to its initial, uninitialized state.
     */
    public synchronized void reset() {
        closeRuntime();
        config = FerrumConfig.defaults();
        state = NativeRuntimeState.UNINITIALIZED;
        reason = null;
    }

    public NativeRuntimeState state() {
        return state;
    }

    /**
     * Returns the reason the runtime is not available.
     *
     * @return the reason, or {@code null} when available
     */
    public @Nullable String reason() {
        return reason;
    }

    public PlatformId platform() {
        return platform;
    }

    public FerrumConfig config() {
        return config;
    }

    public NativeDiagnostics diagnostics() {
        return diagnostics;
    }

    public NativeCircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public boolean isAvailable() {
        return state.isAvailable();
    }

    /**
     * Returns the loaded native runtime.
     *
     * @return the runtime, or {@code null} when not available
     */
    public @Nullable NativeRuntime nativeRuntime() {
        return runtime;
    }

    /**
     * Returns the shared call wrapper.
     *
     * @return the call wrapper, or {@code null} when native is not available
     */
    public @Nullable NativeCall nativeCall() {
        return nativeCall;
    }

    /**
     * Resolves the status of every module.
     *
     * @return one status per module
     */
    public List<FerrumStatus> statuses() {
        return FerrumStatusReporter.collect(state, featureBits(), config, diagnostics);
    }

    /**
     * Returns the advertised feature bits.
     *
     * @return the feature bits, or {@code 0} when native is not available
     */
    public long featureBits() {
        var current = runtime;
        return current != null && current.isAvailable()
                ? current.featureBits()
                : 0L;
    }

    /**
     * Builds the human-readable status report.
     *
     * @return the formatted report
     */
    public String statusReport() {
        return FerrumStatusReporter.report(
                state,
                reason,
                platform,
                abiVersion(),
                featureBits(),
                config,
                diagnostics
        );
    }

    /**
     * Returns the advertised ABI version.
     *
     * @return the ABI version, or {@code 0} when native is not available
     */
    public int abiVersion() {
        var current = runtime;
        return current != null && current.isAvailable()
                ? current.abiVersion()
                : 0;
    }

    @Override
    public synchronized void close() {
        closeRuntime();
    }

}
