package top.likoslupus.ferrum.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.config.NativeSettings;

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

}
