package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.*;

/**
 * The Java mirror of the native {@code FerrumLimits} struct.
 *
 * <p>Layout: {@code u64 max_total_bytes} followed by four {@code u32} fields, for a total of 24
 * bytes. The native side asserts the same size and offsets in its layout tests.
 */
public final class NativeLimits {

    /** The struct layout, matching the native {@code FerrumLimits}. */
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
            ValueLayout.JAVA_LONG.withName("max_total_bytes"),
            ValueLayout.JAVA_INT.withName("max_depth"),
            ValueLayout.JAVA_INT.withName("max_nodes"),
            ValueLayout.JAVA_INT.withName("max_array_length"),
            ValueLayout.JAVA_INT.withName("max_string_encoded_bytes")
    );

    private NativeLimits() {
    }

    /**
     * Allocates and fills a limits segment.
     *
     * @param arena                 the arena that owns the segment
     * @param maxTotalBytes         the total input byte limit
     * @param maxDepth              the maximum nesting depth
     * @param maxNodes              the maximum node count
     * @param maxArrayLength        the maximum array/list length
     * @param maxStringEncodedBytes the maximum encoded string size
     *
     * @return the filled segment
     */
    public static MemorySegment allocate(
            Arena arena,
            long maxTotalBytes,
            int maxDepth,
            int maxNodes,
            int maxArrayLength,
            int maxStringEncodedBytes
    ) {
        var segment = arena.allocate(LAYOUT);
        write(segment, maxTotalBytes, maxDepth, maxNodes, maxArrayLength, maxStringEncodedBytes);
        return segment;
    }

    /**
     * Writes the fields into an existing segment.
     *
     * @param segment               a segment of at least {@link #LAYOUT} size
     * @param maxTotalBytes         the total input byte limit
     * @param maxDepth              the maximum nesting depth
     * @param maxNodes              the maximum node count
     * @param maxArrayLength        the maximum array/list length
     * @param maxStringEncodedBytes the maximum encoded string size
     */
    public static void write(
            MemorySegment segment,
            long maxTotalBytes,
            int maxDepth,
            int maxNodes,
            int maxArrayLength,
            int maxStringEncodedBytes
    ) {
        segment.set(ValueLayout.JAVA_LONG, 0L, maxTotalBytes);
        segment.set(ValueLayout.JAVA_INT, 8L, maxDepth);
        segment.set(ValueLayout.JAVA_INT, 12L, maxNodes);
        segment.set(ValueLayout.JAVA_INT, 16L, maxArrayLength);
        segment.set(ValueLayout.JAVA_INT, 20L, maxStringEncodedBytes);
    }

}
