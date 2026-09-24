package top.likoslupus.ferrum.runtime.config;

import top.likoslupus.ferrum.api.ModuleId;

import java.util.Locale;
import java.util.Map;

/**
 * The persisted Ferrum configuration ({@code config/ferrum.json}).
 *
 * @param nativeSettings global native runtime settings
 * @param modules        per-module settings keyed by the lowercase module name
 */
public record FerrumConfig(
        NativeSettings nativeSettings,
        Map<String, ModuleSettings> modules
) {

    public FerrumConfig {
        modules = Map.copyOf(modules);
    }

    /**
     * Returns the default configuration: native enabled with fallback-friendly defaults, and only
     * the MVP modules enabled.
     *
     * @return the default configuration
     */
    public static FerrumConfig defaults() {
        return new FerrumConfig(
                new NativeSettings(
                        true,
                        true,
                        true,
                        false
                ),
                Map.of(
                        "nbt", new ModuleSettings(true),
                        "codec", new ModuleSettings(true),
                        "palette", new ModuleSettings(true),
                        "noise", new ModuleSettings(true),
                        "light", new ModuleSettings(false),
                        "collide", new ModuleSettings(false),
                        "path", new ModuleSettings(false)
                )
        );
    }

    /**
     * Returns whether the native runtime is enabled.
     *
     * @return {@code true} when native is enabled
     */
    public boolean isNativeEnabled() {
        return nativeSettings.enabled();
    }

    /**
     * Returns whether a module's native fast path is enabled by configuration.
     *
     * @param module the module to check
     *
     * @return {@code true} when the module is enabled and native is enabled
     */
    public boolean isModuleEnabled(ModuleId module) {
        if (!nativeSettings.enabled()) {
            return false;
        }
        if (module == ModuleId.CORE) {
            return true;
        }
        var settings = modules.get(module.name().toLowerCase(Locale.ROOT));
        return settings != null && settings.enabled();
    }

}
