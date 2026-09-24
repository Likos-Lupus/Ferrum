package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.likoslupus.ferrum.runtime.NativeRuntimeState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NativeRuntimeFaultInjectionTest {

    @Test
    void missingLibraryFallsBackWithoutThrowing(@TempDir Path tempDir) {
        try (var runtime = NativeRuntime.tryLoad(tempDir.resolve("absent.so"))) {
            assertEquals(NativeRuntimeState.LOAD_FAILED, runtime.state());
            assertFalse(runtime.isAvailable());
        }
    }

    @Test
    void corruptLibraryFallsBackWithoutThrowing(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("libferrum.so");
        Files.writeString(file, "this is not a shared library");

        try (var runtime = NativeRuntime.tryLoad(file)) {
            assertFalse(runtime.isAvailable());
        }
    }

}
