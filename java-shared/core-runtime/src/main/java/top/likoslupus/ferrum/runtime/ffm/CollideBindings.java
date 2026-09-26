package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * Typed wrapper over the {@code ferrum_collide_aabb_clip} and {@code ferrum_collide_sweep}
 * symbols.
 *
 * <p>Business code never holds a raw {@link MethodHandle} (ADR-0001 AI-02). Both batches are
 * stateless: one call parses the snapshot blob, runs the kernel, and writes the result blob.
 */
public final class CollideBindings {

    private final MethodHandle aabbClip;
    private final MethodHandle sweep;

    CollideBindings(
            MethodHandle aabbClip,
            MethodHandle sweep
    ) {
        this.aabbClip = aabbClip;
        this.sweep = sweep;
    }

    /**
     * Runs one batch AABB ray clip.
     *
     * @param input       the `FBCA` snapshot blob
     * @param inputLength the snapshot length
     * @param output      the `FBCO` output segment
     * @param outputCap   the output capacity
     * @param outWritten  a one-element `u64` output segment receiving the written or required size
     *
     * @return the decoded status
     */
    public NativeStatus aabbClip(
            MemorySegment input,
            long inputLength,
            MemorySegment output,
            long outputCap,
            MemorySegment outWritten
    ) {
        return invoke(aabbClip, input, inputLength, output, outputCap, outWritten);
    }

    private static NativeStatus invoke(
            MethodHandle handle,
            MemorySegment input,
            long inputLength,
            MemorySegment output,
            long outputCap,
            MemorySegment outWritten
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) handle.invokeExact(
                            input,
                            inputLength,
                            output,
                            outputCap,
                            outWritten
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    /**
     * Runs one batched voxel-shape sweep.
     *
     * @param input       the `FBCS` snapshot blob
     * @param inputLength the snapshot length
     * @param output      the `FBCT` output segment
     * @param outputCap   the output capacity
     * @param outWritten  a one-element `u64` output segment receiving the written or required size
     *
     * @return the decoded status
     */
    public NativeStatus sweep(
            MemorySegment input,
            long inputLength,
            MemorySegment output,
            long outputCap,
            MemorySegment outWritten
    ) {
        return invoke(sweep, input, inputLength, output, outputCap, outWritten);
    }

}
