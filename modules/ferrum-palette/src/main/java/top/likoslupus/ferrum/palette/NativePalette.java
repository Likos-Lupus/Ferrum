package top.likoslupus.ferrum.palette;

import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.ffm.NativeStatus;
import top.likoslupus.ferrum.runtime.ffm.PaletteBindings;
import top.likoslupus.ferrum.runtime.scratch.NativeScratch;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.jspecify.annotations.Nullable;

/**
 * Thin Java API over the native palette bulk pack/unpack entry points.
 *
 * <p>Every method returns a failure value on any problem so callers fall back to the vanilla path.
 * A single thread-local scratch region holds the packed words and the value array; the value count
 * is known up front, so no buffer-growth retry is needed.
 */
public final class NativePalette {

    private static final int MIN_BITS = 1;
    private static final int MAX_BITS = 32;

    private NativePalette() {
    }

    /**
     * Unpacks packed words into {@code output}.
     *
     * @param output the destination value array
     * @param data   the packed words
     * @param bits   the value width
     * @param size   the value count
     *
     * @return {@code true} when {@code output} was filled, {@code false} to fall back
     */
    public static boolean unpackInto(
            int[] output,
            long[] data,
            int bits,
            int size
    ) {
        if (!isValidBits(bits)
                || size < 0
                || output.length < size
                || data.length < requiredLongs(size, bits)
        ) {
            return false;
        }

        var bindings = bindings();
        if (bindings == null) {
            return false;
        }

        var scratch = NativeScratch.current();
        scratch.longs(data.length);
        scratch.ints(size);
        var words = scratch.longs(data.length);
        var values = scratch.ints(size);
        words.asSlice(0, (long) data.length * Long.BYTES)
                .copyFrom(MemorySegment.ofArray(data));

        var status = bindings.unpack(words, data.length, bits, size, values, size);
        if (status != NativeStatus.OK) {
            return false;
        }

        MemorySegment.copy(values, ValueLayout.JAVA_INT, 0L, output, 0, size);
        return true;
    }

    private static boolean isValidBits(int bits) {
        return bits >= MIN_BITS && bits <= MAX_BITS;
    }

    private static long requiredLongs(int size, int bits) {
        var perLong = 64L / bits;
        return ((long) size + perLong - 1L) / perLong;
    }

    private static @Nullable PaletteBindings bindings() {
        var runtime = FerrumRuntime.instance().nativeRuntime();
        return runtime == null || !runtime.isAvailable()
                ? null
                : runtime.palette();
    }

    /**
     * Packs values into the palette bit layout.
     *
     * @param values the value array
     * @param bits   the value width
     *
     * @return the packed words, or {@code null} on failure
     */
    public static long @Nullable [] pack(int[] values, int bits) {
        if (!isValidBits(bits)) {
            return null;
        }

        var bindings = bindings();
        if (bindings == null) {
            return null;
        }

        var required = requiredLongs(values.length, bits);
        if (required == 0) {
            return new long[0];
        }

        var scratch = NativeScratch.current();
        scratch.ints(values.length);
        scratch.longs(required);
        var input = scratch.ints(values.length);
        var output = scratch.longs(required);
        input.asSlice(0, (long) values.length * Integer.BYTES)
                .copyFrom(MemorySegment.ofArray(values));

        var status = bindings.pack(input, values.length, bits, output, required);
        return status == NativeStatus.OK
                ? output.asSlice(0, required * Long.BYTES).toArray(ValueLayout.JAVA_LONG)
                : null;
    }

}
