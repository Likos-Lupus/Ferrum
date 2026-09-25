package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * Typed wrapper over the {@code ferrum_light_block_batch} symbol.
 *
 * <p>Business code never holds a raw {@link MethodHandle} (ADR-0001 AI-02). The batch is
 * stateless:
 * one call parses the snapshot blob, runs the block-light BFS, and writes the changed sections.
 */
public final class LightBindings {

    private final MethodHandle blockBatch;

    LightBindings(MethodHandle blockBatch) {
        this.blockBatch = blockBatch;
    }

    /**
     * Runs one block-light batch.
     *
     * @param input       the `FBLT` snapshot blob
     * @param inputLength the snapshot length
     * @param output      the `FBLO` output segment
     * @param outputCap   the output capacity
     * @param outWritten  a one-element `u64` output segment receiving the written or required size
     *
     * @return the decoded status
     */
    public NativeStatus blockBatch(
            MemorySegment input,
            long inputLength,
            MemorySegment output,
            long outputCap,
            MemorySegment outWritten
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) blockBatch.invokeExact(
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

}
