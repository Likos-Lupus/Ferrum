package top.likoslupus.ferrum.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.api.ModuleStatus;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("native")
class FerrumRuntimeNativeTest {

    @AfterEach
    void tearDown() {
        FerrumRuntime.instance().reset();
    }

    @Test
    void loadsRealLibraryAndReportsModuleStatus() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "cargo-built libferrum is required");

        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), library);

        var runtime = FerrumRuntime.instance();
        assertEquals(NativeRuntimeState.AVAILABLE, runtime.state());
        assertTrue(runtime.isAvailable());
        assertEquals(0L, runtime.featureBits());

        var statuses = runtime.statuses();
        var core = statuses.stream()
                .filter(status -> status.module() == ModuleId.CORE)
                .findFirst()
                .orElseThrow();
        assertEquals(ModuleStatus.AVAILABLE, core.status());

        var nbt = statuses.stream()
                .filter(status -> status.module() == ModuleId.NBT)
                .findFirst()
                .orElseThrow();
        assertEquals(ModuleStatus.FALLBACK, nbt.status());
    }

}
