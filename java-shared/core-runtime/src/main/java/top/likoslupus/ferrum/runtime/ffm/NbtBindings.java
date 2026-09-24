package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * Typed wrappers over the FerrumNbt symbols.
 *
 * <p>Business code never holds a raw {@link MethodHandle} (ADR-0001 AI-02). Arguments are the raw
 * ABI values: a source/destination segment, explicit lengths in bytes, a limits segment, an arena
 * segment, a root-index segment, and a used-or-required segment.
 */
public final class NbtBindings {

    private final MethodHandle parse;
    private final MethodHandle parseAny;
    private final MethodHandle write;
    private final MethodHandle writeAny;

    NbtBindings(
            MethodHandle parse,
            MethodHandle parseAny,
            MethodHandle write,
            MethodHandle writeAny
    ) {
        this.parse = parse;
        this.parseAny = parseAny;
        this.write = write;
        this.writeAny = writeAny;
    }

    /**
     * Parses the named wire form.
     *
     * @param source         the source bytes
     * @param sourceLength   the source length
     * @param limits         the {@code FerrumLimits} segment
     * @param arena          the arena segment
     * @param arenaCapacity  the arena capacity
     * @param rootIndex      the root-index output
     * @param usedOrRequired the used-or-required output
     *
     * @return the decoded status
     */
    public NativeStatus parse(
            MemorySegment source,
            long sourceLength,
            MemorySegment limits,
            MemorySegment arena,
            long arenaCapacity,
            MemorySegment rootIndex,
            MemorySegment usedOrRequired
    ) {
        return invoke(
                parse,
                source,
                sourceLength,
                limits,
                arena,
                arenaCapacity,
                rootIndex,
                usedOrRequired
        );
    }

    private static NativeStatus invoke(
            MethodHandle handle,
            MemorySegment source,
            long sourceLength,
            MemorySegment limits,
            MemorySegment arena,
            long arenaCapacity,
            MemorySegment rootIndex,
            MemorySegment usedOrRequired
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) handle.invokeExact(
                            source,
                            sourceLength,
                            limits,
                            arena,
                            arenaCapacity,
                            rootIndex,
                            usedOrRequired
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    /**
     * Parses the any-tag wire form.
     *
     * @param source         the source bytes
     * @param sourceLength   the source length
     * @param limits         the {@code FerrumLimits} segment
     * @param arena          the arena segment
     * @param arenaCapacity  the arena capacity
     * @param rootIndex      the root-index output
     * @param usedOrRequired the used-or-required output
     *
     * @return the decoded status
     */
    public NativeStatus parseAny(
            MemorySegment source,
            long sourceLength,
            MemorySegment limits,
            MemorySegment arena,
            long arenaCapacity,
            MemorySegment rootIndex,
            MemorySegment usedOrRequired,
            MemorySegment consumed
    ) {
        return invokeAny(
                parseAny,
                source,
                sourceLength,
                limits,
                arena,
                arenaCapacity,
                rootIndex,
                usedOrRequired,
                consumed
        );
    }

    private static NativeStatus invokeAny(
            MethodHandle handle,
            MemorySegment source,
            long sourceLength,
            MemorySegment limits,
            MemorySegment arena,
            long arenaCapacity,
            MemorySegment rootIndex,
            MemorySegment usedOrRequired,
            MemorySegment consumed
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) handle.invokeExact(
                            source,
                            sourceLength,
                            limits,
                            arena,
                            arenaCapacity,
                            rootIndex,
                            usedOrRequired,
                            consumed
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    /**
     * Writes the named wire form.
     *
     * @param arena               the arena bytes
     * @param arenaLength         the arena length
     * @param rootIndex           the root node index
     * @param destination         the destination segment
     * @param destinationCapacity the destination capacity
     * @param writtenOrRequired   the written-or-required output
     *
     * @return the decoded status
     */
    public NativeStatus write(
            MemorySegment arena,
            long arenaLength,
            int rootIndex,
            MemorySegment destination,
            long destinationCapacity,
            MemorySegment writtenOrRequired
    ) {
        return invokeWrite(
                write,
                arena,
                arenaLength,
                rootIndex,
                destination,
                destinationCapacity,
                writtenOrRequired
        );
    }

    private static NativeStatus invokeWrite(
            MethodHandle handle,
            MemorySegment arena,
            long arenaLength,
            int rootIndex,
            MemorySegment destination,
            long destinationCapacity,
            MemorySegment writtenOrRequired
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) handle.invokeExact(
                            arena,
                            arenaLength,
                            rootIndex,
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
     * Writes the any-tag wire form.
     *
     * @param arena               the arena bytes
     * @param arenaLength         the arena length
     * @param rootIndex           the root node index
     * @param destination         the destination segment
     * @param destinationCapacity the destination capacity
     * @param writtenOrRequired   the written-or-required output
     *
     * @return the decoded status
     */
    public NativeStatus writeAny(
            MemorySegment arena,
            long arenaLength,
            int rootIndex,
            MemorySegment destination,
            long destinationCapacity,
            MemorySegment writtenOrRequired
    ) {
        return invokeWrite(
                writeAny,
                arena,
                arenaLength,
                rootIndex,
                destination,
                destinationCapacity,
                writtenOrRequired
        );
    }

}
