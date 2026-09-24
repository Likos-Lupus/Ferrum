package top.likoslupus.ferrum.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FerrumCore identity constants and boot state.
 */
public final class FerrumCore {

    public static final String MOD_ID = "ferrum";
    public static final String NAME = "FerrumCore";
    public static final String VERSION = "0.1.0";

    private static final AtomicBoolean MIXIN_APPLIED = new AtomicBoolean();

    private FerrumCore() {
    }

    /**
     * Records that the FerrumCore mixin configuration was applied.
     */
    public static void markMixinApplied() {
        MIXIN_APPLIED.set(true);
    }

    /**
     * Returns whether the FerrumCore mixin configuration has been applied.
     *
     * @return {@code true} once the sentinel mixin ran
     */
    public static boolean isMixinApplied() {
        return MIXIN_APPLIED.get();
    }

}
