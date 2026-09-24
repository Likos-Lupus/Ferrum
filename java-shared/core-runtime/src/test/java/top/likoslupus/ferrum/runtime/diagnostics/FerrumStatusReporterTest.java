package top.likoslupus.ferrum.runtime.diagnostics;

import org.junit.jupiter.api.Test;

import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.api.ModuleStatus;
import top.likoslupus.ferrum.runtime.NativeRuntimeState;
import top.likoslupus.ferrum.runtime.PlatformId;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeDiagnostics;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FerrumStatusReporterTest {

    private final FerrumConfig config = FerrumConfig.defaults();
    private final NativeDiagnostics diagnostics = new NativeDiagnostics();

    @Test
    void availableRuntimeWithNoFeatureBitsFallsBackModules() {
        var statuses = FerrumStatusReporter.collect(
                NativeRuntimeState.AVAILABLE,
                0L,
                config,
                diagnostics
        );

        var core = find(statuses, ModuleId.CORE);
        assertEquals(ModuleStatus.AVAILABLE, core.status());

        var nbt = find(statuses, ModuleId.NBT);
        assertEquals(ModuleStatus.FALLBACK, nbt.status());
        assertEquals("feature-not-advertised", nbt.reason());

        var light = find(statuses, ModuleId.LIGHT);
        assertEquals(ModuleStatus.DISABLED, light.status());
        assertEquals("disabled-by-config", light.reason());
    }

    private static FerrumStatus find(List<FerrumStatus> statuses, ModuleId module) {
        return statuses.stream()
                .filter(status -> status.module() == module)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void unavailableRuntimeFallsBackEveryModule() {
        var statuses = FerrumStatusReporter.collect(
                NativeRuntimeState.LOAD_FAILED,
                0L,
                config,
                diagnostics
        );

        statuses.forEach(status -> {
            assertEquals(ModuleStatus.FALLBACK, status.status());
            assertEquals("native-load_failed", status.reason());
        });
    }

    @Test
    void reportIncludesHeaderAndModules() {
        var report = FerrumStatusReporter.report(
                NativeRuntimeState.AVAILABLE,
                null,
                PlatformId.LINUX_GLIBC_X86_64,
                1,
                0L,
                config,
                diagnostics
        );

        assertTrue(report.contains("Ferrum native: AVAILABLE"));
        assertTrue(report.contains("platform=linux-glibc-x86_64"));
        assertTrue(report.contains("NBT"));
        assertTrue(report.contains("LIGHT"));
    }

}
