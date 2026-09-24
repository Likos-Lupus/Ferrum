package top.likoslupus.ferrum.runtime.ffm;

import top.likoslupus.ferrum.api.ModuleId;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Per-module native call counters.
 *
 * <p>Counters are updated on the call paths and only read for diagnostics, so they use lock-free
 * adders. Hot paths never log; {@link #snapshot()} is meant for a debug command or startup report.
 */
public final class NativeDiagnostics {

    private final Map<ModuleId, Counters> counters = new EnumMap<>(ModuleId.class);

    /**
     * Creates a diagnostics collector with zeroed counters for every module.
     */
    public NativeDiagnostics() {
        for (var module : ModuleId.values()) {
            counters.put(module, new Counters());
        }
    }

    public void recordEligible(ModuleId module) {
        counters(module).eligibleCalls.increment();
    }

    public void recordNativeCall(ModuleId module) {
        counters(module).nativeCalls.increment();
    }

    public void recordJavaFallback(ModuleId module) {
        counters(module).javaFallbackCalls.increment();
    }

    public void recordError(ModuleId module, NativeStatus status) {
        counters(module).errorsByCode.computeIfAbsent(status, _ -> new LongAdder()).increment();
    }

    public void recordProcessed(ModuleId module, long amount) {
        counters(module).processed.add(amount);
    }

    public void recordBufferGrow(ModuleId module) {
        counters(module).bufferGrows.increment();
    }

    public void recordCircuitBreakerTrip(ModuleId module) {
        counters(module).circuitBreakerTrips.increment();
    }

    private Counters counters(ModuleId module) {
        var current = counters.get(module);
        if (current == null) {
            throw new IllegalStateException("missing counters for " + module);
        }
        return current;
    }

    /**
     * Returns a consistent snapshot of all counters.
     *
     * @return the current counters, keyed by module
     */
    public Snapshot snapshot() {
        var modules = new EnumMap<ModuleId, ModuleCounters>(ModuleId.class);
        for (var entry : counters.entrySet()) {
            modules.put(entry.getKey(), entry.getValue().snapshot());
        }
        return new Snapshot(Map.copyOf(modules));
    }

    /**
     * Counters for a single module.
     *
     * @param eligibleCalls          calls that reached the native/fallback decision
     * @param nativeCalls            calls that used the native result
     * @param javaFallbackCalls      calls that fell back to the Java reference
     * @param processed              bytes or items processed by native calls
     * @param bufferGrows            scratch buffer growth events
     * @param circuitBreakerTrips    times the module was disabled by the circuit breaker
     * @param errorsByCode           native error counts by status
     */
    public record ModuleCounters(
            long eligibleCalls,
            long nativeCalls,
            long javaFallbackCalls,
            long processed,
            long bufferGrows,
            long circuitBreakerTrips,
            Map<NativeStatus, Long> errorsByCode
    ) {
    }

    /**
     * Immutable snapshot of every module's counters.
     *
     * @param modules counters keyed by module
     */
    public record Snapshot(Map<ModuleId, ModuleCounters> modules) {
    }

    private static final class Counters {

        private final LongAdder eligibleCalls = new LongAdder();
        private final LongAdder nativeCalls = new LongAdder();
        private final LongAdder javaFallbackCalls = new LongAdder();
        private final LongAdder processed = new LongAdder();
        private final LongAdder bufferGrows = new LongAdder();
        private final LongAdder circuitBreakerTrips = new LongAdder();
        private final ConcurrentHashMap<NativeStatus, LongAdder> errorsByCode = new ConcurrentHashMap<>();

        private ModuleCounters snapshot() {
            var errors = new EnumMap<NativeStatus, Long>(NativeStatus.class);
            for (var entry : errorsByCode.entrySet()) {
                errors.put(entry.getKey(), entry.getValue().sum());
            }
            return new ModuleCounters(
                    eligibleCalls.sum(),
                    nativeCalls.sum(),
                    javaFallbackCalls.sum(),
                    processed.sum(),
                    bufferGrows.sum(),
                    circuitBreakerTrips.sum(),
                    Map.copyOf(errors)
            );
        }

    }

}
