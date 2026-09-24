package top.likoslupus.ferrum.palette;

import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;

import org.jspecify.annotations.Nullable;

/**
 * Mixin-facing entry point for the palette bulk unpack fast path.
 *
 * <p>Eligibility is conservative: the runtime must be available, the palette module enabled and
 * advertised, the value width valid, the value count above the configured {@code minValues}, and
 * the caller's output array large enough to preserve the vanilla exception semantics. If the native
 * kernel declines for any reason, the original method runs unchanged.
 */
public final class PaletteHook {

    private PaletteHook() {
    }

    /**
     * Attempts to unpack a {@code SimpleBitStorage} into {@code output} natively.
     *
     * @param output the destination value array
     * @param data   the packed words
     * @param bits   the value width
     * @param size   the value count
     *
     * @return {@code true} when the array was filled natively, {@code false} to leave vanilla alone
     */
    public static boolean tryUnpack(
            int[] output,
            long[] data,
            int bits,
            int size
    ) {
        var settings = eligibleSettings();
        return settings != null
                && bits >= 1
                && bits <= 32
                && size >= PaletteOptions.from(settings).minValues()
                && NativePalette.unpackInto(output, data, bits, size);
    }

    private static @Nullable ModuleSettings eligibleSettings() {
        var runtime = FerrumRuntime.instance();
        return !runtime.isAvailable()
                || !runtime.config().isModuleEnabled(ModuleId.PALETTE)
                || !NativeFeatures.isSupported(runtime.featureBits(), ModuleId.PALETTE)
                ? null
                : runtime.config().modules().get(PaletteModule.MODULE_ID);
    }

}
