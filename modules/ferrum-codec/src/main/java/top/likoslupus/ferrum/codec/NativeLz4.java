package top.likoslupus.ferrum.codec;

import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.ffm.CodecBindings;
import top.likoslupus.ferrum.runtime.scratch.NativeScratch;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.jspecify.annotations.Nullable;

/**
 * Thin Java API over the native LZ4 block-stream entry points.
 *
 * <p>Every method returns {@code null} on any problem so callers fall back to the vanilla
 * {@code lz4-java} path. A single thread-local scratch region holds the input and the output;
 * growth invalidates earlier slices, so slices are always recomputed inside the retry loop.
 */
public final class NativeLz4 {

    /** The lz4-java token nibble for Minecraft's default 64 KiB block size. */
    public static final int DEFAULT_LEVEL = 6;

    private static final int MAX_ATTEMPTS = 2;
    private static final long MAX_DECOMPRESSED = 64L * 1024 * 1024;
    private static final long MAX_ENCODED = 64L * 1024 * 1024;

    private NativeLz4() {
    }

    /**
     * Decodes an lz4-java block stream.
     *
     * @param source the compressed bytes
     *
     * @return the decoded bytes, or {@code null} on failure
     */
    public static byte @Nullable [] decode(byte[] source) {
        var bindings = bindings();
        if (bindings == null) {
            return null;
        }

        var scratch = NativeScratch.current();
        var capacity = Math.max(1024L, (long) source.length * 3L);

        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            var region = scratch.bytes((long) source.length + capacity);
            var input = region.asSlice(0, source.length);
            input.copyFrom(MemorySegment.ofArray(source));
            var output = region.asSlice(source.length, capacity);
            var written = scratch.longs(1).asSlice(0, Long.BYTES);

            var status = bindings.decompress(
                    input,
                    source.length,
                    output,
                    capacity,
                    written
            );
            switch (status) {
                case OK -> {
                    var count = written.get(ValueLayout.JAVA_LONG_UNALIGNED, 0L);
                    return output.asSlice(0, count).toArray(ValueLayout.JAVA_BYTE);
                }
                case BUFFER_TOO_SMALL -> {
                    var required = written.get(ValueLayout.JAVA_LONG_UNALIGNED, 0L);
                    if (required <= capacity || required > MAX_DECOMPRESSED) {
                        return null;
                    }
                    capacity = required;
                    continue;
                }
                default -> {
                    // no-op
                }
            }
            return null;
        }
        return null;
    }

    private static @Nullable CodecBindings bindings() {
        var runtime = FerrumRuntime.instance().nativeRuntime();
        return runtime == null || !runtime.isAvailable()
                ? null
                : runtime.codec();
    }

    /**
     * Encodes bytes into an lz4-java block stream using the default level.
     *
     * @param source the uncompressed bytes
     *
     * @return the encoded bytes, or {@code null} on failure
     */
    public static byte @Nullable [] encode(byte[] source) {
        return encode(source, DEFAULT_LEVEL);
    }

    /**
     * Encodes bytes into an lz4-java block stream.
     *
     * @param source the uncompressed bytes
     * @param level  the lz4-java token nibble
     *
     * @return the encoded bytes, or {@code null} on failure
     */
    public static byte @Nullable [] encode(byte[] source, int level) {
        var bindings = bindings();
        if (bindings == null) {
            return null;
        }

        var scratch = NativeScratch.current();
        var capacity = Math.max(1024L, (long) source.length + ((long) source.length / 255L) + 128L);

        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            var region = scratch.bytes((long) source.length + capacity);
            var input = region.asSlice(0, source.length);
            input.copyFrom(MemorySegment.ofArray(source));
            var output = region.asSlice(source.length, capacity);
            var written = scratch.longs(1).asSlice(0, Long.BYTES);

            var status = bindings.compress(
                    input,
                    source.length,
                    output,
                    capacity,
                    level,
                    written
            );
            switch (status) {
                case OK -> {
                    var count = written.get(ValueLayout.JAVA_LONG_UNALIGNED, 0L);
                    return output.asSlice(0, count).toArray(ValueLayout.JAVA_BYTE);
                }
                case BUFFER_TOO_SMALL -> {
                    var required = written.get(ValueLayout.JAVA_LONG_UNALIGNED, 0L);
                    if (required <= capacity || required > MAX_ENCODED) {
                        return null;
                    }
                    capacity = required;
                    continue;
                }
                default -> {
                    // no-op
                }
            }
            return null;
        }
        return null;
    }

}
