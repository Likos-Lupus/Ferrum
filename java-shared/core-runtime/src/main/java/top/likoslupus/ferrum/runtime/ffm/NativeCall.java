package top.likoslupus.ferrum.runtime.ffm;

import top.likoslupus.ferrum.api.ModuleId;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The single wrapper every business call goes through.
 *
 * <p>It checks the runtime state, the module switch, the advertised feature bit, and the circuit
 * breaker; captures FFM throwables; decodes the status; updates counters; and runs the Java
 * fallback. Business code must never call a native binding directly.
 */
public final class NativeCall {

    private final NativeRuntime runtime;
    private final NativeCircuitBreaker circuitBreaker;
    private final NativeDiagnostics diagnostics;
    private final Predicate<ModuleId> moduleEnabled;

    /**
     * Creates a call wrapper with default diagnostics and all modules enabled.
     *
     * @param runtime the loaded native runtime
     */
    public NativeCall(NativeRuntime runtime) {
        this(runtime, new NativeCircuitBreaker(), new NativeDiagnostics(), _ -> true);
    }

    /**
     * Creates a call wrapper.
     *
     * @param runtime        the loaded native runtime
     * @param circuitBreaker the per-module circuit breaker
     * @param diagnostics    the counters
     * @param moduleEnabled  the module switch predicate, consulted before any native call
     */
    public NativeCall(
            NativeRuntime runtime,
            NativeCircuitBreaker circuitBreaker,
            NativeDiagnostics diagnostics,
            Predicate<ModuleId> moduleEnabled
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.moduleEnabled = Objects.requireNonNull(moduleEnabled, "moduleEnabled");
    }

    /**
     * Runs a value-producing native call, falling back to Java on any failure.
     *
     * @param module       the calling module
     * @param eligible     whether the batch met the module's threshold
     * @param invocation   the native call
     * @param javaFallback the Java reference path
     * @param <T>          the produced value type
     *
     * @return the native value, or the fallback value
     */
    public <T> T execute(
            ModuleId module,
            boolean eligible,
            Invocation<? extends T> invocation,
            Supplier<? extends T> javaFallback
    ) {
        diagnostics.recordEligible(module);
        if (!useNative(module, eligible)) {
            diagnostics.recordJavaFallback(module);
            return javaFallback.get();
        }

        var outcome = invoke(module, invocation);
        if (outcome.isOk()) {
            circuitBreaker.recordSuccess(module);
            diagnostics.recordNativeCall(module);
            return Objects.requireNonNull(
                    outcome.value(),
                    "native call returned OK without a value"
            );
        }

        return javaFallback.get();
    }

    private boolean useNative(ModuleId module, boolean eligible) {
        return eligible
                && runtime.isAvailable()
                && moduleEnabled.test(module)
                && NativeFeatures.isSupported(runtime.featureBits(), module)
                && !circuitBreaker.isTripped(module);
    }

    private <T> NativeOutcome<T> invoke(ModuleId module, Invocation<T> invocation) {
        var outcome = attempt(invocation);
        if (outcome.status() == NativeStatus.BUFFER_TOO_SMALL) {
            outcome = attempt(invocation);
            if (outcome.status() == NativeStatus.BUFFER_TOO_SMALL) {
                outcome = NativeOutcome.failure(NativeStatus.INTERNAL);
            }
        }

        var status = outcome.status();
        if (!status.isOk()) {
            diagnostics.recordError(module, status);
            var wasTripped = circuitBreaker.isTripped(module);
            circuitBreaker.recordFailure(module, status);
            if (!wasTripped && circuitBreaker.isTripped(module)) {
                diagnostics.recordCircuitBreakerTrip(module);
            }
            diagnostics.recordJavaFallback(module);
        }

        return outcome;
    }

    private static <T> NativeOutcome<T> attempt(Invocation<T> invocation) {
        try {
            return invocation.invoke();
        } catch (RuntimeException | LinkageError throwable) {
            return NativeOutcome.failure(NativeStatus.INTERNAL);
        }
    }

    /**
     * Runs an in-place native call, falling back to Java on any failure.
     *
     * @param module       the calling module
     * @param eligible     whether the batch met the module's threshold
     * @param invocation   the native call
     * @param javaFallback the Java reference path
     */
    public void execute(
            ModuleId module,
            boolean eligible,
            StatusInvocation invocation,
            Runnable javaFallback
    ) {
        diagnostics.recordEligible(module);
        if (!useNative(module, eligible)) {
            diagnostics.recordJavaFallback(module);
            javaFallback.run();
            return;
        }

        var outcome = invoke(
                module,
                () -> {
                    var status = invocation.invoke();
                    return status.isOk()
                            ? NativeOutcome.ok(null)
                            : NativeOutcome.failure(status);
                }
        );
        if (outcome.isOk()) {
            circuitBreaker.recordSuccess(module);
            diagnostics.recordNativeCall(module);
            return;
        }

        javaFallback.run();
    }

    /**
     * A native call that produces a value on success.
     *
     * @param <T> the produced value type
     */
    @FunctionalInterface
    public interface Invocation<T> {

        NativeOutcome<T> invoke();

    }

    /**
     * A native call that writes into caller-owned buffers.
     */
    @FunctionalInterface
    public interface StatusInvocation {

        NativeStatus invoke();

    }

}
