package top.likoslupus.ferrum.nbt;

/**
 * Limits passed to the native parser.
 *
 * @param maxTotalBytes          maximum input bytes
 * @param maxDepth               maximum nesting depth
 * @param maxNodes               maximum node count
 * @param maxArrayLength         maximum array or list length
 * @param maxStringEncodedBytes  maximum encoded string length
 */
public record NbtLimits(
        long maxTotalBytes,
        int maxDepth,
        int maxNodes,
        int maxArrayLength,
        int maxStringEncodedBytes
) {

    private static final int DEFAULT_MAX_DEPTH = 512;
    private static final int MAX_STRING_ENCODED_BYTES = 0xFFFF;

    /**
     * Builds limits for parsing a buffer of the given length.
     *
     * <p>The input length bounds every structural cap, so the native parser can never accept a
     * structurally larger document than the caller's buffer allows.
     *
     * @param length the buffer length
     *
     * @return the limits
     */
    public static NbtLimits forBuffer(int length) {
        var bound = Math.max(1, length);
        return new NbtLimits(
                length,
                DEFAULT_MAX_DEPTH,
                bound,
                bound,
                MAX_STRING_ENCODED_BYTES
        );
    }

}
