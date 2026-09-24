package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * Typed wrappers over the FerrumCodec symbols.
 *
 * <p>Business code never holds a raw {@link MethodHandle} (ADR-0001 AI-02). Arguments are the raw
 * ABI values: source/destination segments, explicit lengths in bytes, the lz4-java token nibble for
 * compression, and a written-or-required output.
 */
public final class CodecBindings {

    private final MethodHandle decompress;
    private final MethodHandle compress;

    CodecBindings(
            MethodHandle decompress,
            MethodHandle compress
    ) {
        this.decompress = decompress;
        this.compress = compress;
    }

    /**
     * Decompresses an LZ4 block stream.
     *
     * @param source              the source bytes
     * @param sourceLength        the source length
     * @param destination         the destination segment
     * @param destinationCapacity the destination capacity
     * @param writtenOrRequired   the written-or-required output
     *
     * @return the decoded status
     */
    public NativeStatus decompress(
            MemorySegment source,
            long sourceLength,
            MemorySegment destination,
            long destinationCapacity,
            MemorySegment writtenOrRequired
    ) {
        return invoke(
                decompress,
                source,
                sourceLength,
                destination,
                destinationCapacity,
                writtenOrRequired
        );
    }

    private static NativeStatus invoke(
            MethodHandle handle,
            MemorySegment source,
            long sourceLength,
            MemorySegment destination,
            long destinationCapacity,
            MemorySegment writtenOrRequired
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) handle.invokeExact(
                            source,
                            sourceLength,
                            destination,
                            destinationCapacity,
                            writtenOrRequired
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    /**
     * Compresses bytes into an LZ4 block stream.
     *
     * @param source              the source bytes
     * @param sourceLength        the source length
     * @param destination         the destination segment
     * @param destinationCapacity the destination capacity
     * @param level               the lz4-java token nibble
     * @param writtenOrRequired   the written-or-required output
     *
     * @return the decoded status
     */
    public NativeStatus compress(
            MemorySegment source,
            long sourceLength,
            MemorySegment destination,
            long destinationCapacity,
            int level,
            MemorySegment writtenOrRequired
    ) {
        return invoke(
                compress,
                source,
                sourceLength,
                destination,
                destinationCapacity,
                level,
                writtenOrRequired
        );
    }

    private static NativeStatus invoke(
            MethodHandle handle,
            MemorySegment source,
            long sourceLength,
            MemorySegment destination,
            long destinationCapacity,
            int level,
            MemorySegment writtenOrRequired
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) handle.invokeExact(
                            source,
                            sourceLength,
                            destination,
                            destinationCapacity,
                            level,
                            writtenOrRequired
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

}
