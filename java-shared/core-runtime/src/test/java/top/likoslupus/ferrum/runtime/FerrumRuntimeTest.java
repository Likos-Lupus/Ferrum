package top.likoslupus.ferrum.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.api.ModuleStatus;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.config.NativeSettings;
import top.likoslupus.ferrum.runtime.nativeimage.NativeLibraryResolution;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FerrumRuntimeTest {

    @AfterEach
    void tearDown() {
        FerrumRuntime.instance().reset();
    }

    @Test
    void disabledByConfigNeverTouchesTheLibrary(@TempDir Path tempDir) {
        var config = new FerrumConfig(
                new NativeSettings(
                        false,
                        true,
                        true,
                        false
                ),
                Map.of()
        );

        FerrumRuntime.instance().initialize(config, tempDir.resolve("libferrum.so"));

        var runtime = FerrumRuntime.instance();
        assertEquals(NativeRuntimeState.DISABLED_BY_CONFIG, runtime.state());
        assertEquals("native-disabled", runtime.reason());
        assertFalse(runtime.isAvailable());
        assertNull(runtime.nativeCall());
    }

    @Test
    void missingLibraryFallsBackWithoutThrowing() {
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), null);

        var runtime = FerrumRuntime.instance();
        assertEquals(NativeRuntimeState.LOAD_FAILED, runtime.state());
        assertEquals("library-not-found", runtime.reason());
        assertFalse(runtime.isAvailable());
        assertEquals(0L, runtime.featureBits());
    }

    @Test
    void statusReportIsAlwaysAvailable() {
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), null);

        var report = FerrumRuntime.instance().statusReport();

        assertTrue(report.contains("Ferrum native: LOAD_FAILED"));
        assertTrue(report.contains("CORE"));
    }

    @Test
    void javaGateMatchesPlanThreshold() {
        assertTrue(FerrumRuntime.isJavaSupported(22));
        assertTrue(FerrumRuntime.isJavaSupported(25));
        assertFalse(FerrumRuntime.isJavaSupported(21));
        assertTrue(FerrumRuntime.isJavaSupported());
    }

    @Test
    void resolutionFailureStateIsAdopted() {
        FerrumRuntime.instance().initializeResolved(
                FerrumConfig.defaults(),
                NativeLibraryResolution.failed(
                        NativeRuntimeState.EXTRACT_FAILED,
                        "checksum-mismatch"
                )
        );

        var runtime = FerrumRuntime.instance();
        assertEquals(NativeRuntimeState.EXTRACT_FAILED, runtime.state());
        assertEquals("checksum-mismatch", runtime.reason());
        assertFalse(runtime.isAvailable());
        assertNull(runtime.buildInfo());
        assertNull(runtime.librarySha256());
    }

    @Test
    void featureGateFallsBackWhenRuntimeUnavailable() {
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), null);

        var gate = FerrumRuntime.instance();
        assertEquals(ModuleStatus.FALLBACK, gate.state(ModuleId.CORE).status());
        assertFalse(gate.isNative(ModuleId.NBT));
    }

}
