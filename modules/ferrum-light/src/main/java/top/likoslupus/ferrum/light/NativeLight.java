package top.likoslupus.ferrum.light;

import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.ffm.LightBindings;
import top.likoslupus.ferrum.runtime.ffm.NativeStatus;
import top.likoslupus.ferrum.runtime.scratch.NativeScratch;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.jspecify.annotations.Nullable;

/**
 * Thin Java API over the native {@code ferrum_light_block_batch} entry point.
 *
 * <p>Returns {@code null} on any problem so the caller runs the vanilla batch. Input, output, and
 * the written-size cell live in a single thread-local scratch region.
 */
public final class NativeLight {

    private NativeLight() {
    }

    /**
     * Runs one block-light batch.
     *
     * @param input the `FBLT` snapshot blob
     *
     * @return the `FBLO` output blob, or {@code null} on failure
     */
    public static byte @Nullable [] run(byte[] input) {
        var bindings = bindings();
        if (bindings == null) {
            return null;
        }

        var inputLength = input.length;
        var outputCapacity = inputLength + 64L;
        for (var attempt = 0; attempt < 2; attempt++) {
            var scratch = NativeScratch.current();
            scratch.bytes(inputLength);
            scratch.bytes(outputCapacity);
            scratch.longs(1L);

            var inputSegment = scratch.bytes(inputLength).asSlice(0L, inputLength);
            var outputSegment = scratch.bytes(outputCapacity).asSlice(0L, outputCapacity);
            var writtenSegment = scratch.longs(1L);
            inputSegment.copyFrom(MemorySegment.ofArray(input));

            var status = bindings.blockBatch(
                    inputSegment,
                    inputLength,
                    outputSegment,
                    outputCapacity,
                    writtenSegment
            );
            if (status == NativeStatus.OK) {
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
            if (status == NativeStatus.BUFFER_TOO_SMALL) {
                var required = writtenSegment.get(ValueLayout.JAVA_LONG, 0L);
                if (required <= outputCapacity) {
                    return null;
                }
                outputCapacity = required;
                continue;
            }
            return null;
        }
        return null;
    }

    private static @Nullable LightBindings bindings() {
        var runtime = FerrumRuntime.instance().nativeRuntime();
        return runtime == null || !runtime.isAvailable()
                ? null
                : runtime.light();
    }

}
