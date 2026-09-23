package top.likoslupus.ferrum.api;

import org.jspecify.annotations.Nullable;

/**
 * Reports the current state of a module together with an optional reason.
 *
 * @param module the module this state belongs to
 * @param status the availability of the module
 * @param reason a human-readable reason when the module is not fully available, otherwise
 *               {@code null}
 */
public record FeatureState(
        ModuleId module,
        ModuleStatus status,
        @Nullable String reason
) {

    public static FeatureState available(ModuleId module) {
        return new FeatureState(module, ModuleStatus.AVAILABLE, null);
    }

    public static FeatureState fallback(ModuleId module, String reason) {
        return new FeatureState(module, ModuleStatus.FALLBACK, reason);
    }

    public static FeatureState disabled(ModuleId module, String reason) {
        return new FeatureState(module, ModuleStatus.DISABLED, reason);
    }

    public boolean isNative() {
        return status == ModuleStatus.AVAILABLE;
    }

}
