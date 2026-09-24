package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.api.ModuleId;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static java.util.Objects.requireNonNull;

class NativeCallTest {

    @Test
    void fallsBackWhenRuntimeUnavailable() {
        try (var runtime = NativeRuntime.tryLoad(Path.of("/nonexistent/libferrum.so"))) {
            assertFalse(runtime.isAvailable());
            var call = new NativeCall(runtime);

            var value = call.execute(
                    ModuleId.NBT,
                    true,
                    () -> NativeOutcome.ok(11L),
                    () -> 22L
            );

            assertEquals(22L, value);
        }
    }

    @Test
    @Tag("native")
    void executesNativeCallWhenEligible() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var runtime = NativeRuntime.tryLoad(requireNonNull(library))) {
            var call = new NativeCall(runtime);

            var value = call.execute(
                    ModuleId.CORE,
                    true,
                    () -> runtime.invokeSelftest(7L),
                    () -> -1L
            );

            assertEquals(runtime.selftest(7L), value);
        }
    }

    @Test
    @Tag("native")
    void fallsBackWhenIneligibleWithoutCallingNative() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var runtime = NativeRuntime.tryLoad(requireNonNull(library))) {
            var call = new NativeCall(runtime);
            var nativeRan = new AtomicBoolean();

            var value = call.execute(
                    ModuleId.CORE,
                    false,
                    () -> {
                        nativeRan.set(true);
                        return NativeOutcome.ok(11L);
                    },
                    () -> 22L
            );

            assertEquals(22L, value);
            assertFalse(nativeRan.get());
        }
    }

    @Test
    @Tag("native")
    void fallsBackForModuleWithoutAdvertisedFeatureBit() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var runtime = NativeRuntime.tryLoad(requireNonNull(library))) {
            var call = new NativeCall(runtime);
            var nativeRan = new AtomicBoolean();

            var value = call.execute(
                    ModuleId.NOISE,
                    true,
                    () -> {
                        nativeRan.set(true);
                        return NativeOutcome.ok(11L);
                    },
                    () -> 22L
            );

            assertEquals(22L, value);
            assertFalse(nativeRan.get());
        }
    }

    @Test
    @Tag("native")
    void inPlaceFailureFallsBack() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var runtime = NativeRuntime.tryLoad(requireNonNull(library))) {
            var call = new NativeCall(runtime);
            var fallbackRan = new AtomicBoolean();

            call.execute(
                    ModuleId.CORE,
                    true,
                    () -> NativeStatus.MALFORMED_INPUT,
                    () -> fallbackRan.set(true)
            );

            assertTrue(fallbackRan.get());
        }
    }

}
