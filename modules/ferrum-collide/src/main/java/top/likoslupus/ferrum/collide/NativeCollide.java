package top.likoslupus.ferrum.collide;

import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.ffm.CollideBindings;
import top.likoslupus.ferrum.runtime.ffm.NativeStatus;
import top.likoslupus.ferrum.runtime.scratch.NativeScratch;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Thin Java API over the native collide entry points.
 *
 * <p>Returns {@code null} on any problem so the caller runs the vanilla path. Input, output, and
 * the written-size cell live in a single thread-local scratch region.
 */
public final class NativeCollide {

    private static final long OUTPUT_CAPACITY = 64L;

    private NativeCollide() {
    }

    /**
     * Runs one batch AABB ray clip.
     *
     * @param input the `FBCA` snapshot blob
     *
     * @return the `FBCO` output blob, or {@code null} on failure
     */
    public static byte @Nullable [] clip(byte[] input) {
        var bindings = bindings();
        return Optional.ofNullable(bindings)
                .map(collideBindings -> run(input, collideBindings, true))
                .orElse(null);
    }

    private static @Nullable CollideBindings bindings() {
        var runtime = FerrumRuntime.instance().nativeRuntime();
        return runtime == null || !runtime.isAvailable()
                ? null
                : runtime.collide();
    }

    private static byte @Nullable [] run(
            byte[] input,
            CollideBindings bindings,
            boolean clip
    ) {
        var inputLength = input.length;
        var scratch = NativeScratch.current();
        scratch.bytes(inputLength);
        scratch.bytes(OUTPUT_CAPACITY);
        scratch.longs(1L);

        var inputSegment = scratch.bytes(inputLength).asSlice(0L, inputLength);
        var outputSegment = scratch.bytes(OUTPUT_CAPACITY).asSlice(0L, OUTPUT_CAPACITY);
        var writtenSegment = scratch.longs(1L);
        inputSegment.copyFrom(MemorySegment.ofArray(input));

        var status = clip
                ?
                bindings.aabbClip(
                        inputSegment,
                        inputLength,
                        outputSegment,
                        OUTPUT_CAPACITY,
                        writtenSegment
                )
                : bindings.sweep(
                        inputSegment,
                        inputLength,
                        outputSegment,
                        OUTPUT_CAPACITY,
                        writtenSegment
                );
        if (status != NativeStatus.OK) {
            return null;
        }

        var written = writtenSegment.get(ValueLayout.JAVA_LONG, 0L);
        var result = new byte[Math.toIntExact(written)];
        MemorySegment.copy(
                outputSegment,
                ValueLayout.JAVA_BYTE,
                0L,
                result,
                0,
                result.length
        );
        return result;
    }

    /**
     * Runs one batched voxel-shape sweep.
     *
     * @param input the `FBCS` snapshot blob
     *
     * @return the `FBCT` output blob, or {@code null} on failure
     */
    public static byte @Nullable [] sweep(byte[] input) {
        var bindings = bindings();
        return Optional.ofNullable(bindings)
                .map(collideBindings -> run(input, collideBindings, false))
                .orElse(null);
    }

}
