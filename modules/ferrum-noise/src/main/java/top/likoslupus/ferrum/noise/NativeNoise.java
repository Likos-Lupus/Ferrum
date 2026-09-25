package top.likoslupus.ferrum.noise;

import net.minecraft.world.level.levelgen.synth.NormalNoise;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.ffm.NativeStatus;
import top.likoslupus.ferrum.runtime.ffm.NoiseBindings;
import top.likoslupus.ferrum.runtime.scratch.NativeScratch;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.jspecify.annotations.Nullable;

/**
 * Thin Java API over the native noise create/batch/destroy entry points.
 *
 * <p>Every method returns a failure value on any problem so callers fall back to the vanilla path.
 * Coordinate and output storage lives in a single thread-local scratch region.
 */
public final class NativeNoise {

    private NativeNoise() {
    }

    /**
     * Creates a native noise field for a vanilla {@code NormalNoise}.
     *
     * @param normal the noise field
     *
     * @return the handle, or {@code 0} on failure
     */
    public static long create(NormalNoise normal) {
        return createFromDescriptor(NoiseDescriptorWriter.writeNormal(normal));
    }

    /**
     * Creates a native noise field from a descriptor.
     *
     * @param descriptor the descriptor bytes
     *
     * @return the handle, or {@code 0} on failure
     */
    static long createFromDescriptor(byte[] descriptor) {
        var bindings = bindings();
        if (bindings == null) {
            return 0L;
        }

        var scratch = NativeScratch.current();
        scratch.bytes(descriptor.length);
        scratch.longs(1L);

        var descriptorSegment = scratch.bytes(descriptor.length);
        var handleSegment = scratch.longs(1L);
        descriptorSegment
                .asSlice(0L, descriptor.length)
                .copyFrom(MemorySegment.ofArray(descriptor));

        var status = bindings.create(
                descriptorSegment,
                descriptor.length,
                handleSegment
        );
        return status == NativeStatus.OK
                ? handleSegment.get(ValueLayout.JAVA_LONG, 0L)
                : 0L;
    }

    private static @Nullable NoiseBindings bindings() {
        var runtime = FerrumRuntime.instance().nativeRuntime();
        return runtime == null || !runtime.isAvailable()
                ? null
                : runtime.noise();
    }

    /**
     * Evaluates a noise field at the given coordinates and writes one output per sample.
     *
     * @param handle the field handle
     * @param xs     the `x` coordinates
     * @param ys     the `y` coordinates
     * @param zs     the `z` coordinates
     * @param out    the output array
     * @param count  the number of samples
     *
     * @return {@code true} on success
     */
    public static boolean batch(
            long handle,
            double[] xs,
            double[] ys,
            double[] zs,
            double[] out,
            int count
    ) {
        if (count < 0
                || count > xs.length
                || count > ys.length
                || count > zs.length
                || count > out.length
        ) {
            return false;
        }

        var bindings = bindings();
        if (bindings == null) {
            return false;
        }

        var scratch = NativeScratch.current();
        scratch.doubles(4L * count);
        var region = scratch.doubles(4L * count);
        var bytes = (long) count * Double.BYTES;
        var xsSegment = region.asSlice(0L, bytes);
        var ysSegment = region.asSlice(bytes, bytes);
        var zsSegment = region.asSlice(2L * bytes, bytes);
        var outSegment = region.asSlice(3L * bytes, bytes);

        xsSegment.copyFrom(MemorySegment.ofArray(xs).asSlice(0L, bytes));
        ysSegment.copyFrom(MemorySegment.ofArray(ys).asSlice(0L, bytes));
        zsSegment.copyFrom(MemorySegment.ofArray(zs).asSlice(0L, bytes));

        var status = bindings.batch(
                handle,
                xsSegment,
                ysSegment,
                zsSegment,
                outSegment,
                count,
                0
        );
        if (status != NativeStatus.OK) {
            return false;
        }

        MemorySegment.copy(
                outSegment,
                ValueLayout.JAVA_DOUBLE,
                0L,
                out,
                0,
                count
        );
        return true;
    }

    /**
     * Evaluates a noise field into caller-provided native segments.
     *
     * @param handle the field handle
     * @param xs     the `x` coordinate segment
     * @param ys     the `y` coordinate segment
     * @param zs     the `z` coordinate segment
     * @param out    the output segment
     * @param count  the number of samples
     *
     * @return {@code true} on success
     */
    public static boolean batchInto(
            long handle,
            MemorySegment xs,
            MemorySegment ys,
            MemorySegment zs,
            MemorySegment out,
            int count
    ) {
        var bindings = bindings();
        return bindings != null
                && bindings.batch(handle, xs, ys, zs, out, count, 0) == NativeStatus.OK;
    }

    /**
     * Destroys a native noise field.
     *
     * @param handle the field handle
     */
    public static void destroy(long handle) {
        var bindings = bindings();
        if (bindings != null) {
            bindings.destroy(handle);
        }
    }

}
