package top.likoslupus.ferrum.runtime.diagnostics;

import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.api.ModuleStatus;
import top.likoslupus.ferrum.runtime.ffm.NativeDiagnostics;

import org.jspecify.annotations.Nullable;

/**
 * The resolved runtime state of a single module.
 *
 * @param module   the module
 * @param status   its availability
 * @param reason   the reason when not natively available, otherwise {@code null}
 * @param counters the module's call counters
 */
public record FerrumStatus(
        ModuleId module,
        ModuleStatus status,
        @Nullable String reason,
        NativeDiagnostics.ModuleCounters counters
) {

}
