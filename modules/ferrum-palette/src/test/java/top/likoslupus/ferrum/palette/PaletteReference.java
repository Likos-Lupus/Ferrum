package top.likoslupus.ferrum.palette;

import net.minecraft.util.SimpleBitStorage;

import java.util.stream.IntStream;

/**
 * Java reference for the palette bulk operations, built on the vanilla {@link SimpleBitStorage} so
 * the oracle is the real game implementation rather than a re-derivation.
 *
 * <p>{@code ParameterName} is suppressed because the Minecraft constructor parameter names differ
 * between the 1.21.1 and 26.1.2 mappings; the clarifying {@code bits}/{@code size} comments keep
 * {@code ArgumentSelectionDefectChecker} satisfied on both.
 */
@SuppressWarnings("ParameterName")
final class PaletteReference {

    private PaletteReference() {
    }

    /**
     * Packs values through a fresh {@link SimpleBitStorage}.
     *
     * @param values the values
     * @param bits   the value width
     *
     * @return the packed words
     */
    static long[] pack(
            int[] values,
            int bits
    ) {
        var storage = new SimpleBitStorage(
                /* bits= */ bits,
                /* size= */ values.length
        );
        IntStream.range(0, values.length)
                .forEach(index ->
                        storage.set(index, values[index])
                );
        return storage.getRaw();
    }

    /**
     * Unpacks words through a fresh {@link SimpleBitStorage}.
     *
     * @param raw  the packed words
     * @param bits the value width
     * @param size the value count
     *
     * @return the unpacked values
     */
    static int[] unpack(
            long[] raw,
            int bits,
            int size
    ) {
        var storage = new SimpleBitStorage(
                /* bits= */ bits,
                /* size= */ size,
                raw
        );
        var values = new int[size];
        storage.unpack(values);
        return values;
    }

    /**
     * Remaps packed values through the vanilla storage, the exact production oracle.
     *
     * @param rawIn   the packed input words
     * @param bitsIn  the input width
     * @param size    the value count
     * @param map     the old-to-new index table
     * @param bitsOut the output width
     *
     * @return the packed output words
     */
    static long[] remap(
            long[] rawIn,
            int bitsIn,
            int size,
            int[] map,
            int bitsOut
    ) {
        var input = new SimpleBitStorage(
                /* bits= */ bitsIn,
                /* size= */ size,
                rawIn
        );
        var output = new SimpleBitStorage(
                /* bits= */ bitsOut,
                /* size= */ size
        );
        IntStream.range(0, size)
                .forEach(index ->
                        output.set(index, map[input.get(index)])
                );
        return output.getRaw();
    }

}
