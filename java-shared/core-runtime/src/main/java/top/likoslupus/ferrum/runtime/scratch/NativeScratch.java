package top.likoslupus.ferrum.runtime.scratch;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Thread-local scratch buffers backed by a confined {@link Arena}.
 *
 * <p>Each thread owns exactly one instance. Buffers start at a small fixed capacity and grow by
 * closing the whole arena and recreating it at the larger size, so a segment obtained earlier is
 * invalidated by any later growth. Scratch buffers are short-lived and must never be handed to
 * another thread. Call {@link #close()} on the owner thread when the thread shuts down; the
 * remaining buffers are then reclaimed.
 */
public final class NativeScratch implements AutoCloseable {

    private static final long MIN_BYTES = 1L << 10;
    private static final long MIN_INTS = 1L << 8;
    private static final long MIN_LONGS = 1L << 8;
    private static final long MIN_DOUBLES = 1L << 8;

    private static final ThreadLocal<NativeScratch> CURRENT = ThreadLocal.withInitial(NativeScratch::new);

    private Arena arena;
    private MemorySegment bytes;
    private MemorySegment ints;
    private MemorySegment longs;
    private MemorySegment doubles;
    private long byteCapacity;
    private long intCapacity;
    private long longCapacity;
    private long doubleCapacity;
    private long grows;
    private boolean closed;

    private NativeScratch() {
        arena = Arena.ofConfined();
        byteCapacity = MIN_BYTES;
        intCapacity = MIN_INTS;
        longCapacity = MIN_LONGS;
        doubleCapacity = MIN_DOUBLES;
        bytes = arena.allocate(ValueLayout.JAVA_BYTE, byteCapacity);
        ints = arena.allocate(ValueLayout.JAVA_INT, intCapacity);
        longs = arena.allocate(ValueLayout.JAVA_LONG, longCapacity);
        doubles = arena.allocate(ValueLayout.JAVA_DOUBLE, doubleCapacity);
    }

    /**
     * Returns the scratch buffers for the calling thread.
     *
     * @return the thread's scratch instance
     */
    public static NativeScratch current() {
        return CURRENT.get();
    }

    /**
     * Returns the byte buffer, growing it when necessary.
     *
     * @param minCapacity the required byte capacity
     *
     * @return a segment with at least {@code minCapacity} bytes
     */
    public MemorySegment bytes(long minCapacity) {
        requireOpen();
        if (minCapacity > byteCapacity) {
            grow(minCapacity, 0L, 0L, 0L);
        }
        return bytes;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("scratch already closed");
        }
    }

    private void grow(
            long needBytes,
            long needInts,
            long needLongs,
            long needDoubles
    ) {
        byteCapacity = nextCapacity(byteCapacity, needBytes);
        intCapacity = nextCapacity(intCapacity, needInts);
        longCapacity = nextCapacity(longCapacity, needLongs);
        doubleCapacity = nextCapacity(doubleCapacity, needDoubles);

        arena.close();
        arena = Arena.ofConfined();
        bytes = arena.allocate(ValueLayout.JAVA_BYTE, byteCapacity);
        ints = arena.allocate(ValueLayout.JAVA_INT, intCapacity);
        longs = arena.allocate(ValueLayout.JAVA_LONG, longCapacity);
        doubles = arena.allocate(ValueLayout.JAVA_DOUBLE, doubleCapacity);
        grows++;
    }

    private static long nextCapacity(long current, long required) {
        return required <= current
                ? current
                : Math.max(required, current + (current >> 1));
    }

    /**
     * Returns the int buffer, growing it when necessary.
     *
     * @param minCapacity the required element capacity
     *
     * @return a segment with at least {@code minCapacity} ints
     */
    public MemorySegment ints(long minCapacity) {
        requireOpen();
        if (minCapacity > intCapacity) {
            grow(0L, minCapacity, 0L, 0L);
        }
        return ints;
    }

    /**
     * Returns the long buffer, growing it when necessary.
     *
     * @param minCapacity the required element capacity
     *
     * @return a segment with at least {@code minCapacity} longs
     */
    public MemorySegment longs(long minCapacity) {
        requireOpen();
        if (minCapacity > longCapacity) {
            grow(0L, 0L, minCapacity, 0L);
        }
        return longs;
    }

    /**
     * Returns the double buffer, growing it when necessary.
     *
     * @param minCapacity the required element capacity
     *
     * @return a segment with at least {@code minCapacity} doubles
     */
    public MemorySegment doubles(long minCapacity) {
        requireOpen();
        if (minCapacity > doubleCapacity) {
            grow(0L, 0L, 0L, minCapacity);
        }
        return doubles;
    }

    /**
     * Returns the number of times the underlying arena has been grown and recreated.
     *
     * @return the growth count
     */
    public long growthCount() {
        return grows;
    }

    /**
     * Returns whether this scratch instance has been closed.
     *
     * @return {@code true} after {@link #close()}
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            arena.close();
            CURRENT.remove();
        }
    }

}
