package top.likoslupus.ferrum.nbt;

import net.minecraft.nbt.Tag;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.ffm.NativeLimits;
import top.likoslupus.ferrum.runtime.scratch.NativeScratch;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Thin Java API over the native NBT entry points.
 *
 * <p>Every method returns {@code null} on any problem so callers fall back to the vanilla path. A
 * single thread-local scratch region is split into the input, arena, and (for writes) output
 * buffers; growth invalidates earlier slices, so slices are always recomputed inside the retry
 * loop.
 */
public final class NativeNbt {

    private static final int HEADER_GUESS_BASE = 128;
    private static final int ARENA_GUESS_FACTOR = 16;
    private static final int WRITE_GUESS_FACTOR = 3;
    private static final int MAX_ATTEMPTS = 2;

    private NativeNbt() {
    }

    /**
     * Parses the any-tag wire form.
     *
     * @param source the source bytes
     * @param offset the first source byte
     * @param length the source length
     * @param limits the parse limits
     *
     * @return the root tag and consumed length, or {@code null} on failure
     */
    public static @Nullable AnyResult parseAny(
            byte[] source,
            int offset,
            int length,
            NbtLimits limits
    ) {
        var raw = parseRaw(
                source,
                offset,
                length,
                limits,
                true,
                false
        );
        if (raw == null) {
            return null;
        }

        var tag = ArenaMaterializer.materialize(
                raw.segment().asSlice(0, raw.used()),
                raw.root()
        );
        return new AnyResult(tag, raw.consumed());
    }

    private static @Nullable Raw parseRaw(
            byte[] source,
            int offset,
            int length,
            NbtLimits limits,
            boolean any,
            boolean keepArena
    ) {
        var runtime = FerrumRuntime.instance().nativeRuntime();
        if (runtime == null || !runtime.isAvailable()) {
            return null;
        }

        var bindings = runtime.nbt();
        if (bindings == null) {
            return null;
        }

        var scratch = NativeScratch.current();
        var arenaCapacity = Math.max(
                256L,
                (long) length * ARENA_GUESS_FACTOR + HEADER_GUESS_BASE
        );

        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            var big = scratch.bytes(length + arenaCapacity);
            var input = big.asSlice(0, length);
            input.copyFrom(MemorySegment.ofArray(source).asSlice(offset, length));
            var arena = big.asSlice(length, arenaCapacity);

            var control = scratch.longs(6);
            NativeLimits.write(
                    control.asSlice(0, NativeLimits.LAYOUT.byteSize()),
                    limits.maxTotalBytes(),
                    limits.maxDepth(),
                    limits.maxNodes(),
                    limits.maxArrayLength(),
                    limits.maxStringEncodedBytes()
            );

            var limitsSegment = control.asSlice(0, NativeLimits.LAYOUT.byteSize());
            var rootSegment = control.asSlice(40, 4);
            var usedSegment = control.asSlice(24, 8);
            var consumedSegment = control.asSlice(32, 8);

            var status = any
                    ?
                    bindings.parseAny(
                            input,
                            length,
                            limitsSegment,
                            arena,
                            arenaCapacity,
                            rootSegment,
                            usedSegment,
                            consumedSegment
                    )
                    : bindings.parse(
                            input,
                            length,
                            limitsSegment,
                            arena,
                            arenaCapacity,
                            rootSegment,
                            usedSegment
                    );

            switch (status) {
                case OK -> {
                    var used = control.get(ValueLayout.JAVA_LONG_UNALIGNED, 24L);
                    var root = control.get(ValueLayout.JAVA_INT_UNALIGNED, 40L);
                    var consumed = any
                            ? control.get(ValueLayout.JAVA_LONG_UNALIGNED, 32L)
                            : length;
                    var arenaBytes = keepArena
                            ? arena.asSlice(0, used).toArray(ValueLayout.JAVA_BYTE)
                            : null;
                    return new Raw(arenaBytes, arena, used, root, (int) consumed);
                }
                case BUFFER_TOO_SMALL -> {
                    arenaCapacity = Math.max(
                            control.get(ValueLayout.JAVA_LONG_UNALIGNED, 24L),
                            arenaCapacity + 1L
                    );
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

    /**
     * Parses the named wire form.
     *
     * @param source the source bytes
     * @param limits the parse limits
     *
     * @return the root tag, or {@code null} on failure
     */
    public static @Nullable Tag parseNamed(byte[] source, NbtLimits limits) {
        var raw = parseRaw(
                source,
                0,
                source.length,
                limits,
                false,
                false
        );
        return Optional.ofNullable(raw)
                .map(raw1 -> ArenaMaterializer.materialize(
                        raw1.segment().asSlice(0, raw1.used()),
                        raw1.root()
                ))
                .orElse(null);
    }

    /**
     * Parses the named wire form and returns the raw arena for the native writer.
     *
     * <p>Intended for the differential tests and future chunk-schema work; it copies the arena out
     * of the scratch buffer.
     *
     * @param source the source bytes
     * @param limits the parse limits
     *
     * @return the arena bytes and root index, or {@code null} on failure
     */
    public static @Nullable ArenaResult parseNamedToArena(byte[] source, NbtLimits limits) {
        var raw = parseRaw(
                source,
                0,
                source.length,
                limits,
                false,
                true
        );
        var arena = Optional.ofNullable(raw)
                .map(Raw::arena)
                .orElse(null);
        return raw == null || arena == null
                ? null
                : new ArenaResult(arena, raw.root(), raw.consumed());
    }

    /**
     * Writes the named wire form from an arena.
     *
     * @param arenaBytes the arena bytes
     * @param rootIndex  the root node index
     *
     * @return the encoded bytes, or {@code null} on failure
     */
    public static byte @Nullable [] writeNamed(byte[] arenaBytes, int rootIndex) {
        return write(arenaBytes, rootIndex, true);
    }

    private static byte @Nullable [] write(
            byte[] arenaBytes,
            int rootIndex,
            boolean named
    ) {
        var runtime = FerrumRuntime.instance().nativeRuntime();
        if (runtime == null || !runtime.isAvailable()) {
            return null;
        }

        var bindings = runtime.nbt();
        if (bindings == null) {
            return null;
        }

        var scratch = NativeScratch.current();
        var capacity = Math.max(
                256L,
                (long) arenaBytes.length * WRITE_GUESS_FACTOR + HEADER_GUESS_BASE
        );

        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            var big = scratch.bytes(arenaBytes.length + capacity);
            var arena = big.asSlice(0, arenaBytes.length);
            arena.copyFrom(MemorySegment.ofArray(arenaBytes));
            var output = big.asSlice(arenaBytes.length, capacity);

            var control = scratch.longs(1);
            var writtenSegment = control.asSlice(0, 8);

            var status = named
                    ?
                    bindings.write(
                            arena,
                            arenaBytes.length,
                            rootIndex,
                            output,
                            capacity,
                            writtenSegment
                    )
                    : bindings.writeAny(
                            arena,
                            arenaBytes.length,
                            rootIndex,
                            output,
                            capacity,
                            writtenSegment
                    );

            switch (status) {
                case OK -> {
                    var written = control.get(ValueLayout.JAVA_LONG_UNALIGNED, 0L);
                    return output.asSlice(0, written).toArray(ValueLayout.JAVA_BYTE);
                }
                case BUFFER_TOO_SMALL -> {
                    capacity = Math.max(
                            control.get(ValueLayout.JAVA_LONG_UNALIGNED, 0L),
                            capacity + 1L
                    );
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

    /**
     * Writes the any-tag wire form from an arena.
     *
     * @param arenaBytes the arena bytes
     * @param rootIndex  the root node index
     *
     * @return the encoded bytes, or {@code null} on failure
     */
    public static byte @Nullable [] writeAny(byte[] arenaBytes, int rootIndex) {
        return write(arenaBytes, rootIndex, false);
    }

    /**
     * A parsed any-form document.
     *
     * @param tag      the root tag, or {@code null} for an end tag
     * @param consumed the number of input bytes consumed
     */
    public record AnyResult(
            @Nullable Tag tag,
            int consumed
    ) {

    }

    /**
     * A parsed named-form document with its raw arena.
     */
    public static final class ArenaResult {

        private final byte[] arena;
        private final int root;
        private final int consumed;

        ArenaResult(
                byte[] arena,
                int root,
                int consumed
        ) {
            this.arena = arena;
            this.root = root;
            this.consumed = consumed;
        }

        public byte[] arena() {
            return arena;
        }

        public int root() {
            return root;
        }

        public int consumed() {
            return consumed;
        }

    }

    @SuppressWarnings("ArrayRecordComponent")
    private record Raw(
            byte @Nullable [] arenaBytes,
            MemorySegment segment,
            long used,
            int root,
            int consumed
    ) {

        byte @Nullable [] arena() {
            return arenaBytes;
        }

    }

}
