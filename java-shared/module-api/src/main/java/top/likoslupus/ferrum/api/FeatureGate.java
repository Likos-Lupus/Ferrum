package top.likoslupus.ferrum.api;

/**
 * Public read-only view of which modules may currently use their native fast path.
 *
 * <p>Modules consult this facade at their batch boundary. It reflects configuration, the native
 * runtime state, and the feature bits advertised by the loaded library; it never triggers a native
 * call itself.
 */
public interface FeatureGate {

    /**
     * Returns whether a module may use its native fast path.
     *
     * @param module the module to query
     *
     * @return {@code true} when the module is natively available
     */
    default boolean isNative(ModuleId module) {
        return state(module).isNative();
    }

    /**
     * Resolves the current state of a module.
     *
     * @param module the module to query
     *
     * @return the module state, including a reason when not natively available
     */
    FeatureState state(ModuleId module);

}
