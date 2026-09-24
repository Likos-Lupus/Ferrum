package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * Typed wrappers over the FerrumPalette symbols.
 *
 * <p>Business code never holds a raw {@link MethodHandle} (ADR-0001 AI-02). Arguments are the raw
 * ABI values: the packed `u64` words, the value width in bits, the value count, and the output
 * segment with its explicit element capacity.
 */
public final class PaletteBindings {

    private final MethodHandle unpack;
    private final MethodHandle pack;

    PaletteBindings(
            MethodHandle unpack,
            MethodHandle pack
    ) {
        this.unpack = unpack;
        this.pack = pack;
    }

    /**
     * Unpacks packed palette values into one `u32` per value.
     *
     * @param data       the packed `u64` words
     * @param dataLength the number of words available
     * @param bits       the value width
     * @param valueCount the number of values to unpack
     * @param outValues  the output `u32` segment
     * @param outLength  the number of output slots available
     *
     * @return the decoded status
     */
    public NativeStatus unpack(
            MemorySegment data,
            long dataLength,
            int bits,
            long valueCount,
            MemorySegment outValues,
            long outLength
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) unpack.invokeExact(
                            data,
                            dataLength,
                            bits,
                            valueCount,
                            outValues,
                            outLength
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    /**
     * Packs one `u32` per value into the palette bit layout.
     *
     * @param values     the input `u32` values
     * @param valueCount the number of values to pack
     * @param bits       the value width
     * @param outData    the output `u64` segment
     * @param outLength  the number of output words available
     *
     * @return the decoded status
     */
    public NativeStatus pack(
            MemorySegment values,
            long valueCount,
            int bits,
            MemorySegment outData,
            long outLength
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) pack.invokeExact(
                            values,
                            valueCount,
                            bits,
                            outData,
                            outLength
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

}
