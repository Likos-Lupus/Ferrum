package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static java.util.Objects.requireNonNull;

class NativeBindingsTest {

    private static final List<String> MODULE_SYMBOLS = List.of(
            "ferrum_nbt_parse",
            "ferrum_nbt_write",
            "ferrum_lz4_block_stream_decompress",
            "ferrum_lz4_block_stream_compress",
            "ferrum_palette_unpack",
            "ferrum_palette_pack",
            "ferrum_noise_batch",
            "ferrum_noise_destroy"
    );

    @Test
    @Tag("native")
    void bindsCoreAndDeclaredModuleSymbols() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var bindings = NativeBindings.load(requireNonNull(library))) {
            assertEquals(1, bindings.abiVersion());
            assertEquals(
                    0L,
                    bindings.featureBits(),
                    "no module feature bits must be advertised yet"
            );

            MODULE_SYMBOLS.forEach(name ->
                    assertTrue(bindings.hasSymbol(name), name)
            );

            var info = bindings.buildInfo();
            assertEquals(NativeBindings.EXPECTED_ABI, info.abiVersion());
            assertEquals(64, info.structSize());
        }
    }

    @Test
    @Tag("native")
    void moduleStubReturnsUnsupported() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");

        try (var bindings = NativeBindings.load(requireNonNull(library))) {
            var handle = requireNonNull(bindings.symbol("ferrum_noise_destroy"));
            var status = (int) handle.invokeExact(0L);
            assertEquals(NativeStatus.UNSUPPORTED, NativeStatus.fromCode(status));
        } catch (Throwable throwable) {
            throw new AssertionError("ferrum_noise_destroy invocation failed", throwable);
        }
    }

    @Test
    @Tag("native")
    void panicHookIsConvertedToPanicStatus() {
        var library = NativeLibraryLocator.find();
        assumeTrue(
                library != null,
                "native library not built"
        );

        try (var bindings = NativeBindings.load(requireNonNull(library))) {
            assumeTrue(
                    bindings.hasSymbol("ferrum_selftest_panic"),
                    "library built without test-hooks"
            );

            var handle = requireNonNull(bindings.symbol("ferrum_selftest_panic"));
            var status = (int) handle.invokeExact();
            assertEquals(NativeStatus.PANIC, NativeStatus.fromCode(status));
        } catch (Throwable throwable) {
            throw new AssertionError("ferrum_selftest_panic invocation failed", throwable);
        }
    }

}
