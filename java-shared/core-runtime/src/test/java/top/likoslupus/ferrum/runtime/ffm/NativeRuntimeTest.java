package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.NativeRuntimeState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    @Tag("native")
    void concurrentSelftestIsStable() throws Exception {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var runtime = NativeRuntime.tryLoad(Objects.requireNonNull(library))) {
            assertTrue(runtime.isAvailable());
            var expected = runtime.selftest(7L);

            var threads = 16;
            var perThread = 64;
            var executor = Executors.newFixedThreadPool(threads);
            try {
                var futures = IntStream.range(0, threads)
                        .mapToObj(_ -> executor.submit(() -> {
                            var value = 0L;
                            for (var call = 0; call < perThread; call++) {
                                value = runtime.selftest(7L);
                            }
                            return value;
                        }))
                        .collect(Collectors.toCollection(() -> new ArrayList<>(threads)));
                for (var future : futures) {
                    assertEquals(expected, future.get());
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void validatesBuildInfoLayout() {
        var commit = "0".repeat(40);
        assertEquals(
                NativeStatus.OK,
                NativeRuntime.validateBuildInfo(new NativeBuildInfo(
                        NativeBindings.BUILD_INFO_SIZE,
                        1,
                        0L,
                        commit
                ))
        );
        assertEquals(
                NativeStatus.ABI_MISMATCH,
                NativeRuntime.validateBuildInfo(new NativeBuildInfo(
                        NativeBindings.BUILD_INFO_SIZE + 8,
                        1,
                        0L,
                        commit
                ))
        );
        assertEquals(
                NativeStatus.ABI_MISMATCH,
                NativeRuntime.validateBuildInfo(new NativeBuildInfo(
                        NativeBindings.BUILD_INFO_SIZE,
                        2,
                        0L,
                        commit
                ))
        );
    }

    @Test
    void fallsBackWhenLibraryMissing() {
        try (var runtime = NativeRuntime.tryLoad(Path.of("/nonexistent/libferrum.so"))) {
            assertFalse(runtime.isAvailable());
            assertNotNull(runtime.reason());
        }
    }

}
