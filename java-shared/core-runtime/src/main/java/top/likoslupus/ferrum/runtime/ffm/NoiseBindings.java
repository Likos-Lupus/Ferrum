package top.likoslupus.ferrum.runtime.ffm;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * Typed wrappers over the FerrumNoise symbols.
 *
 * <p>Business code never holds a raw {@link MethodHandle} (ADR-0001 AI-02). A field is created
 * from
 * a descriptor, evaluated through {@link #batch}, and released exactly once through
 * {@link #destroy}.
 */
public final class NoiseBindings {

    private final MethodHandle create;
    private final MethodHandle batch;
    private final MethodHandle destroy;

    NoiseBindings(
            MethodHandle create,
            MethodHandle batch,
            MethodHandle destroy
    ) {
        this.create = create;
        this.batch = batch;
        this.destroy = destroy;
    }

    /**
     * Creates a noise field from a descriptor.
     *
     * @param descriptor       the descriptor bytes
     * @param descriptorLength the descriptor length
     * @param outHandle        a one-element `u64` output segment receiving the handle
     *
     * @return the decoded status
     */
    public NativeStatus create(
            MemorySegment descriptor,
            long descriptorLength,
            MemorySegment outHandle
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) create.invokeExact(
                            descriptor,
                            descriptorLength,
                            outHandle
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    /**
     * Evaluates a noise field at many coordinates.
     *
     * @param handle      the field handle
     * @param xs          the `x` coordinate segment
     * @param ys          the `y` coordinate segment
     * @param zs          the `z` coordinate segment
     * @param outValues   the output `f64` segment
     * @param sampleCount the number of samples
     * @param flags       reserved flags, must be zero
     *
     * @return the decoded status
     */
    public NativeStatus batch(
            long handle,
            MemorySegment xs,
            MemorySegment ys,
            MemorySegment zs,
            MemorySegment outValues,
            long sampleCount,
            int flags
    ) {
        try {
            return NativeStatus.fromCode(
                    (int) batch.invokeExact(
                            handle,
                            xs,
                            ys,
                            zs,
                            outValues,
                            sampleCount,
                            flags
                    )
            );
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

    /**
     * Destroys a noise field.
     *
     * @param handle the field handle
     *
     * @return the decoded status
     */
    public NativeStatus destroy(long handle) {
        try {
            return NativeStatus.fromCode((int) destroy.invokeExact(handle));
        } catch (RuntimeException | Error throwable) {
            throw throwable;
        } catch (Throwable throwable) {
            return NativeStatus.INTERNAL;
        }
    }

}
