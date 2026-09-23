package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.NativeRuntimeState;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeRuntimeTest {

    @Test
    @Tag("native")
    void loadsBuiltLibraryWhenPresent() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var runtime = NativeRuntime.tryLoad(Objects.requireNonNull(library))) {
            assertEquals(NativeRuntimeState.AVAILABLE, runtime.state());
            assertTrue(runtime.isAvailable());
            assertEquals(1, runtime.abiVersion());
            assertEquals(runtime.selftest(42L), runtime.selftest(42L));
        }
    }

    @Test
    void fallsBackWhenLibraryMissing() {
        try (var runtime = NativeRuntime.tryLoad(Path.of("/nonexistent/libferrum.so"))) {
            assertFalse(runtime.isAvailable());
            assertNotNull(runtime.reason());
        }
    }

}
