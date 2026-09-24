package top.likoslupus.ferrum.runtime.config;

/**
 * Per-module settings.
 *
 * <p>Batch thresholds are intentionally absent until each module's JMH crossover fixes them; they
 * are added together with the module that measures them.
 *
 * @param enabled whether the module's native fast path may be used
 */
public record ModuleSettings(boolean enabled) {

}
