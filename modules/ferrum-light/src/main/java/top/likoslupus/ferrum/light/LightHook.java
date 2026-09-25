package top.likoslupus.ferrum.light;

import net.minecraft.world.level.lighting.BlockLightEngine;
import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.light.mixin.LayerLightSectionStorageAccessor;
import top.likoslupus.ferrum.light.mixin.LightEngineAccessor;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;

import java.util.concurrent.atomic.LongAdder;

/**
 * Mixin-facing entry point for the block-light batch.
 *
 * <p>Eligibility is conservative: the runtime must be available, the light module enabled, the
 * module's feature advertised (or the test-only override set), and the storage free of pending
 * inconsistencies. The snapshot builder rejects the whole batch when any section, shape, size, or
 * queue cannot be flattened. On any decline the vanilla {@code runLightUpdates} path runs.
 */
public final class LightHook {

    /** Test-only switch that lets tests reach the path without advertising the feature bit. */
    private static final String FORCE_PROPERTY = "ferrum.test.light.force";

    private static final LongAdder FALLBACKS = new LongAdder();

    private LightHook() {
    }

    /**
     * Attempts to run the block-light batch natively.
     *
     * @param engine   the block-light engine
     * @param accessor the engine accessors
     *
     * @return the processed count, or {@code -1} to leave vanilla alone
     */
    public static int tryRunNative(
            BlockLightEngine engine,
            LightEngineAccessor accessor
    ) {
        var runtime = FerrumRuntime.instance();
        if (!runtime.isAvailable()) {
            return -1;
        }

        var settings = runtime.config().modules().get(LightModule.MODULE_ID);
        if (settings == null || !eligible(settings)) {
            return -1;
        }

        var storage = (LayerLightSectionStorageAccessor) accessor.ferrum$storage();
        if (storage.ferrum$hasInconsistencies()) {
            FALLBACKS.increment();
            return -1;
        }

        var options = LightOptions.from(settings);
        var input = LightBatch.build(
                accessor,
                storage,
                accessor.ferrum$chunkSource(),
                options.maxSections()
        );
        if (input == null) {
            FALLBACKS.increment();
            return -1;
        }

        var output = NativeLight.run(input);
        if (output == null) {
            FALLBACKS.increment();
            return -1;
        }

        var processed = LightBatch.apply(engine, accessor, storage, output);
        if (processed < 0) {
            FALLBACKS.increment();
            return -1;
        }
        return processed;
    }

    private static boolean eligible(ModuleSettings settings) {
        var runtime = FerrumRuntime.instance();
        var advertised = NativeFeatures.isSupported(runtime.featureBits(), ModuleId.LIGHT);
        return (advertised && settings.enabled()) || forced();
    }

    /**
     * Returns whether the test-only forced override is active.
     *
     * @return {@code true} when {@code ferrum.test.light.force} is set
     */
    public static boolean forced() {
        return Boolean.getBoolean(FORCE_PROPERTY);
    }

    /**
     * Returns how many times the native path declined and fell back to vanilla.
     *
     * @return the fallback count
     */
    public static long fallbacks() {
        return FALLBACKS.sum();
    }

}
