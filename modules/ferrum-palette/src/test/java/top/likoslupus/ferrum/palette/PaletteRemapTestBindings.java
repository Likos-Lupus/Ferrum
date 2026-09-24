package top.likoslupus.ferrum.palette;

import top.likoslupus.ferrum.runtime.ffm.NativeStatus;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * Test-only FFM binding for the F-055 {@code ferrum_test_palette_remap} hook.
 *
 * <p>This binding exists only in the benchmark/spike harness, never in production. It is loaded
 * from a library compiled with the {@code test-hooks} cargo feature and returns {@code null} when
 * that symbol is absent, so normal builds are unaffected.
 */
final class PaletteRemapTestBindings implements AutoCloseable {

    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS
    );

    private final Arena arena;
    private final MethodHandle handle;

    private PaletteRemapTestBindings(
            Arena arena,
            MethodHandle handle
    ) {
        this.arena = arena;
        this.handle = handle;
    }

    /**
     * Loads the test-hook symbol from the given library.
     *
     * @param library the native library
     *
     * @return the binding, or {@code null} when the symbol is absent or the library is unloadable
     */
    static @Nullable PaletteRemapTestBindings load(Path library) {
        var arena = Arena.ofShared();
        try {
            var lookup = SymbolLookup.libraryLookup(library, arena);
            var symbol = lookup.find("ferrum_test_palette_remap").orElse(null);
            if (symbol == null) {
                arena.close();
                return null;
            }
            return new PaletteRemapTestBindings(
                    arena,
                    Linker.nativeLinker().downcallHandle(symbol, DESCRIPTOR)
            );
        } catch (RuntimeException | Error throwable) {
            arena.close();
            return null;
        }
    }

    /**
     * Runs the fused remap.
     *
     * @param input             the packed input words
     * @param inputLength       the input word count
     * @param bitsIn            the input width
     * @param valueCount        the value count
     * @param map               the old-to-new index table
     * @param mapLength         the table length
     * @param bitsOut           the output width
     * @param output            the output word segment
     * @param outputLength      the output word capacity
     * @param writtenOrRequired the written-or-required output
     *
     * @return the decoded status
     */
    NativeStatus remap(
            MemorySegment input,
            long inputLength,
            int bitsIn,
            long valueCount,
            MemorySegment map,
            long mapLength,
            int bitsOut,
            MemorySegment output,
            long outputLength,
            MemorySegment writtenOrRequired
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) handle.invokeExact(
                            input,
                            inputLength,
                            bitsIn,
                            valueCount,
                            map,
                            mapLength,
                            bitsOut,
                            output,
                            outputLength,
                            writtenOrRequired
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    @Override
    public void close() {
        arena.close();
    }

}
