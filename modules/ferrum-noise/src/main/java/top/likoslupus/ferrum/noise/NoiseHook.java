package top.likoslupus.ferrum.noise;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;
import top.likoslupus.ferrum.runtime.scratch.NativeScratch;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.jspecify.annotations.Nullable;

/**
 * Mixin-facing entry point for the leaf noise grid batch.
 *
 * <p>Eligibility is conservative: the runtime must be available, the noise module enabled, the
 * grid path enabled, the output above the configured {@code minSamples}, and the density provider a
 * {@link NoiseChunk} whose {@code forIndex} mapping can be reproduced. Only the direct
 * {@link DensityFunctions.Noise} leaf is batched; every wrapper, composite, and unknown shape falls
 * back to vanilla. If the native kernel declines for any reason, the original method runs.
 */
public final class NoiseHook {

    /** Test-only switch that lets tests reach the path without advertising the feature bit. */
    private static final String FORCE_PROPERTY = "ferrum.test.noise.force";

    private NoiseHook() {
    }

    /**
     * Attempts to fill a leaf noise density function natively.
     *
     * @param noiseHolder the leaf's noise holder
     * @param xzScale     the leaf's horizontal coordinate scale
     * @param yScale      the leaf's vertical coordinate scale
     * @param output      the destination array
     * @param provider    the density context provider
     *
     * @return {@code true} when the array was filled natively, {@code false} to leave vanilla alone
     */
    public static boolean tryFillLeaf(
            DensityFunction.NoiseHolder noiseHolder,
            double xzScale,
            double yScale,
            double[] output,
            DensityFunction.ContextProvider provider
    ) {
        var settings = eligibleSettings();
        if (settings == null || output.length == 0) {
            return false;
        }

        var options = NoiseOptions.from(settings);
        if (!options.grid() || output.length < options.minSamples()) {
            return false;
        }

        var normal = noiseHolder.noise();
        if (normal == null) {
            return false;
        }

        var handle = NoiseHandleCache.acquire(normal);
        return handle != 0L && fillWithHandle(
                handle,
                xzScale,
                yScale,
                output,
                provider
        );
    }

    private static @Nullable ModuleSettings eligibleSettings() {
        var runtime = FerrumRuntime.instance();
        if (!runtime.isAvailable()
                || !runtime.config().isModuleEnabled(ModuleId.NOISE)
        ) {
            return null;
        }

        var advertised = NativeFeatures.isSupported(
                runtime.featureBits(),
                ModuleId.NOISE
        );
        if (!advertised && !forced()) {
            return null;
        }

        return runtime.config().modules().get(NoiseModule.MODULE_ID);
    }

    /**
     * Fills a grid from an existing handle.
     *
     * <p>Package-private: the in-game hook acquires the handle through the cache, while the grid
     * differential test supplies a handle built from the test-side extractor.
     *
     * @param handle   the native field handle
     * @param xzScale  the horizontal coordinate scale
     * @param yScale   the vertical coordinate scale
     * @param output   the destination array
     * @param provider the density context provider
     *
     * @return {@code true} when the array was filled natively
     */
    static boolean fillWithHandle(
            long handle,
            double xzScale,
            double yScale,
            double[] output,
            DensityFunction.ContextProvider provider
    ) {
        var count = output.length;
        var scratch = NativeScratch.current();
        scratch.doubles(4L * count);
        var region = scratch.doubles(4L * count);
        var bytes = (long) count * Double.BYTES;
        var xs = region.asSlice(0L, bytes);
        var ys = region.asSlice(bytes, bytes);
        var zs = region.asSlice(2L * bytes, bytes);
        var out = region.asSlice(3L * bytes, bytes);

        for (var index = 0; index < count; index++) {
            var context = provider.forIndex(index);
            if (!(context instanceof NoiseChunk)) {
                return false;
            }

            var offset = (long) index * Double.BYTES;
            xs.set(ValueLayout.JAVA_DOUBLE, offset, context.blockX() * xzScale);
            ys.set(ValueLayout.JAVA_DOUBLE, offset, context.blockY() * yScale);
            zs.set(ValueLayout.JAVA_DOUBLE, offset, context.blockZ() * xzScale);
        }

        if (!NativeNoise.batchInto(handle, xs, ys, zs, out, count)) {
            return false;
        }

        MemorySegment.copy(
                out,
                ValueLayout.JAVA_DOUBLE,
                0L,
                output,
                0,
                count
        );
        return true;
    }

    /**
     * Returns whether the test-only forced override is active.
     *
     * @return {@code true} when {@code ferrum.test.noise.force} is set
     */
    static boolean forced() {
        return Boolean.getBoolean(FORCE_PROPERTY);
    }

}
