package top.likoslupus.ferrum.runtime;

import top.likoslupus.ferrum.api.FeatureGate;
import top.likoslupus.ferrum.api.FeatureState;
import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.diagnostics.FerrumStatus;
import top.likoslupus.ferrum.runtime.diagnostics.FerrumStatusReporter;
import top.likoslupus.ferrum.runtime.ffm.*;
import top.likoslupus.ferrum.runtime.nativeimage.NativeLibraryResolution;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Central wiring of the Ferrum native runtime.
 *
 * <p>The loader glue initializes this once at startup. It owns the single {@link NativeRuntime},
 * the shared counters, the shared circuit breaker, and the single {@link NativeCall} wrapper that
 * every module goes through. Initialization never throws: every failure is captured as a
 * {@link NativeRuntimeState} plus a reason so callers fall back to the Java path.
 */
public final class FerrumRuntime implements AutoCloseable, FeatureGate {

    /** The minimum Java feature release on which the native runtime may be enabled. */
    public static final int MIN_JAVA_FEATURE = 22;

    private static final FerrumRuntime INSTANCE = new FerrumRuntime();

    private final NativeDiagnostics diagnostics = new NativeDiagnostics();
    private final NativeCircuitBreaker circuitBreaker = new NativeCircuitBreaker();
    private final PlatformId platform = PlatformId.current();
    private volatile FerrumConfig config = FerrumConfig.defaults();
    private volatile NativeRuntimeState state = NativeRuntimeState.UNINITIALIZED;
    private volatile @Nullable String reason;
    private volatile @Nullable NativeRuntime runtime;
    private volatile @Nullable NativeCall nativeCall;
    private volatile @Nullable NativeBuildInfo buildInfo;
    private volatile @Nullable String librarySha256;

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
        initializeResolved(
                config,
                Optional.ofNullable(library)
                        .map(path -> NativeLibraryResolution.found(path, null))
                        .orElseGet(() -> NativeLibraryResolution.failed(
                                NativeRuntimeState.LOAD_FAILED,
                                "library-not-found"
                        ))
        );
    }

    /**
     * Initializes the runtime from a configuration and a resolved library.
     *
     * @param config     the active configuration
     * @param resolution the resolved native library or the failure state to adopt
     */
    public synchronized void initializeResolved(
            FerrumConfig config,
            NativeLibraryResolution resolution
    ) {
        this.config = requireNonNull(config, "config");
        closeRuntime();

        if (!config.isNativeEnabled()) {
            setFailure(
                    NativeRuntimeState.DISABLED_BY_CONFIG,
                    "native-disabled"
            );
            return;
        }

        if (!isJavaSupported()) {
            setFailure(
                    NativeRuntimeState.UNSUPPORTED_JAVA,
                    "java=" + Runtime.version().feature()
            );
            return;
        }

        var failureState = resolution.failureState();
        if (failureState != null) {
            setFailure(failureState, resolution.reason());
            return;
        }

        var library = resolution.library();
        if (library == null) {
            setFailure(NativeRuntimeState.LOAD_FAILED, "library-not-found");
            return;
        }

        librarySha256 = resolution.sha256();

        var loaded = NativeRuntime.tryLoad(library, config.nativeSettings().strictAbi());
        runtime = loaded;
        state = loaded.state();
        reason = loaded.reason();
        buildInfo = loaded.isAvailable()
                ? loaded.buildInfo()
                : null;
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

    private void setFailure(
            NativeRuntimeState failureState,
            @Nullable String failureReason
    ) {
        state = failureState;
        reason = failureReason;
        buildInfo = null;
        librarySha256 = null;
    }

    /**
     * Returns whether the currently running JVM meets the minimum Java feature release.
     *
     * @return {@code true} when the native runtime may be enabled
     */
    public static boolean isJavaSupported() {
        return isJavaSupported(Runtime.version().feature());
    }

    /**
     * Returns whether the given Java feature release meets the minimum.
     *
     * @param feature the Java feature release
     *
     * @return {@code true} when the native runtime may be enabled
     */
    public static boolean isJavaSupported(int feature) {
        return feature >= MIN_JAVA_FEATURE;
    }

    /**
     * Returns the runtime to its initial, uninitialized state.
     */
    public synchronized void reset() {
        closeRuntime();
        config = FerrumConfig.defaults();
        state = NativeRuntimeState.UNINITIALIZED;
        reason = null;
        buildInfo = null;
        librarySha256 = null;
    }

    /**
     * Returns the decoded native build information.
     *
     * @return the build information, or {@code null} when native is not available
     */
    public @Nullable NativeBuildInfo buildInfo() {
        return buildInfo;
    }

    /**
     * Returns the SHA-256 of the resolved native library.
     *
     * @return the checksum, or {@code null} when unknown
     */
    public @Nullable String librarySha256() {
        return librarySha256;
    }

    @Override
    public FeatureState state(ModuleId module) {
        return FerrumStatusReporter.state(state, featureBits(), config, module);
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
                diagnostics,
                Runtime.version().feature(),
                buildInfo,
                librarySha256
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
