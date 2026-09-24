package top.likoslupus.ferrum.runtime.ffm;

import top.likoslupus.ferrum.api.ModuleId;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracks native failures and disables modules that keep failing.
 *
 * <p>Policy (frozen by the ABI error model):
 *
 * <ul>
 *   <li>{@link NativeStatus#ABI_MISMATCH} and {@link NativeStatus#PANIC} disable all native calls
 *       for the session;
 *   <li>{@code INTERNAL} disables a module after three consecutive occurrences;
 *   <li>malformed external input never counts;
 *   <li>a successful call resets the module's internal-failure streak.
 * </ul>
 */
public final class NativeCircuitBreaker {

    private static final int INTERNAL_THRESHOLD = 3;

    private final Map<ModuleId, Integer> internalStreak = new EnumMap<>(ModuleId.class);
    private final Set<ModuleId> disabledModules = EnumSet.noneOf(ModuleId.class);
    private boolean sessionTripped;

    /**
     * Records a successful call, clearing the module's consecutive internal-failure streak.
     *
     * @param module the module that succeeded
     */
    public synchronized void recordSuccess(ModuleId module) {
        internalStreak.put(module, 0);
    }

    /**
     * Records one non-fatal failure according to the status policy.
     *
     * @param module the module that failed
     * @param status the decoded status
     */
    public synchronized void recordFailure(ModuleId module, NativeStatus status) {
        if (status.isSessionFatal()) {
            sessionTripped = true;
            return;
        }

        if (!status.doesCountTowardCircuitBreaker()) {
            return;
        }

        var streak = internalStreak.merge(module, 1, Integer::sum);
        if (streak >= INTERNAL_THRESHOLD) {
            disabledModules.add(module);
        }
    }

    /**
     * Returns whether a module's native path is currently disabled.
     *
     * @param module the module to test
     *
     * @return {@code true} when the module must use its Java fallback
     */
    public synchronized boolean isModuleDisabled(ModuleId module) {
        return disabledModules.contains(module);
    }

    /**
     * Returns whether all native calls are disabled for the session.
     *
     * @return {@code true} after an ABI mismatch or panic
     */
    public synchronized boolean isSessionTripped() {
        return sessionTripped;
    }

    /**
     * Returns whether any module has been disabled.
     *
     * @return {@code true} when at least one module or the whole session is disabled
     */
    public synchronized boolean isTripped(ModuleId module) {
        return sessionTripped || disabledModules.contains(module);
    }

}
