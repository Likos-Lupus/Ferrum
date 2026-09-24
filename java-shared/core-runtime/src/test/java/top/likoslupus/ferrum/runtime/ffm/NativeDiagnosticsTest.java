package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.api.ModuleId;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NativeDiagnosticsTest {

    @Test
    void recordsAndSnapshotsCounters() {
        var diagnostics = new NativeDiagnostics();

        diagnostics.recordEligible(ModuleId.NBT);
        diagnostics.recordEligible(ModuleId.NBT);
        diagnostics.recordNativeCall(ModuleId.NBT);
        diagnostics.recordJavaFallback(ModuleId.NBT);
        diagnostics.recordError(ModuleId.NBT, NativeStatus.INTERNAL);
        diagnostics.recordProcessed(ModuleId.NBT, 1024L);
        diagnostics.recordBufferGrow(ModuleId.NBT);
        diagnostics.recordCircuitBreakerTrip(ModuleId.NBT);

        var counters = diagnostics.snapshot().modules().get(ModuleId.NBT);
        assertNotNull(counters);
        assertEquals(2L, counters.eligibleCalls());
        assertEquals(1L, counters.nativeCalls());
        assertEquals(1L, counters.javaFallbackCalls());
        assertEquals(1024L, counters.processed());
        assertEquals(1L, counters.bufferGrows());
        assertEquals(1L, counters.circuitBreakerTrips());
        assertEquals(1L, counters.errorsByCode().get(NativeStatus.INTERNAL));
    }

    @Test
    void snapshotIncludesEveryModule() {
        var snapshot = new NativeDiagnostics().snapshot();

        Arrays.stream(ModuleId.values())
                .forEach(module ->
                        assertNotNull(snapshot.modules().get(module), module.name())
                );
    }

}
