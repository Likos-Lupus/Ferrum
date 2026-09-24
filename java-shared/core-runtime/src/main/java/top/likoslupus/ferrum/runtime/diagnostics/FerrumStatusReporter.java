package top.likoslupus.ferrum.runtime.diagnostics;

import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.api.ModuleStatus;
import top.likoslupus.ferrum.runtime.NativeRuntimeState;
import top.likoslupus.ferrum.runtime.PlatformId;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeDiagnostics;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds the {@code /ferrum status} view from the runtime, configuration, and counters.
 *
 * <p>The reporter is a pure function over its inputs so the loader glue and tests share exactly
 * the same formatting.
 */
public final class FerrumStatusReporter {

    private static final NativeDiagnostics.ModuleCounters EMPTY_COUNTERS =
            new NativeDiagnostics.ModuleCounters(
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    Map.of()
            );

    private FerrumStatusReporter() {
    }

    /**
     * Formats a multi-line status report.
     *
     * @param state       the native runtime state
     * @param reason      the runtime failure reason, or {@code null}
     * @param platform    the detected platform, or {@code null}
     * @param abiVersion  the native ABI version, or {@code 0} when unknown
     * @param featureBits the advertised feature bits
     * @param config      the active configuration
     * @param diagnostics the counters
     *
     * @return the formatted report
     */
    public static String report(
            NativeRuntimeState state,
            @Nullable String reason,
            @Nullable PlatformId platform,
            int abiVersion,
            long featureBits,
            FerrumConfig config,
            NativeDiagnostics diagnostics
    ) {
        var builder = new StringBuilder()
                .append("Ferrum native: ")
                .append(state);

        if (platform != null) {
            builder.append(" platform=").append(platform.id());
        }

        if (state.isAvailable()) {
            builder.append(" abi=").append(abiVersion)
                    .append(" features=").append(NativeFeatures.decode(featureBits));
        } else if (reason != null) {
            builder.append(" reason=").append(reason);
        }

        var lineSeparator = System.lineSeparator();

        collect(state, featureBits, config, diagnostics)
                .forEach(status -> {
                    builder.append(lineSeparator)
                            .append(String.format(
                                    Locale.ROOT,
                                    "%-8s %-8s calls=%d fallback=%d errors=%d",
                                    status.module(),
                                    status.status(),
                                    status.counters().nativeCalls(),
                                    status.counters().javaFallbackCalls(),
                                    errorCount(status)
                            ));
                    if (status.reason() != null) {
                        builder.append(" reason=").append(status.reason());
                    }
                });

        return builder.toString();
    }

    /**
     * Resolves the status of every module.
     *
     * @param state       the native runtime state
     * @param featureBits the advertised feature bits
     * @param config      the active configuration
     * @param diagnostics the counters
     *
     * @return one status per module, in declaration order
     */
    public static List<FerrumStatus> collect(
            NativeRuntimeState state,
            long featureBits,
            FerrumConfig config,
            NativeDiagnostics diagnostics
    ) {
        var snapshot = diagnostics.snapshot();
        var modules = snapshot.modules();
        var nativeAvailable = state.isAvailable();
        var nativeUnavailableReason = nativeAvailable
                ? null
                : "native-" + state.name().toLowerCase(Locale.ROOT);

        var moduleIds = ModuleId.values();
        var statuses = new ArrayList<FerrumStatus>(moduleIds.length);

        for (var module : moduleIds) {
            var counters = modules.getOrDefault(module, EMPTY_COUNTERS);

            ModuleStatus status;
            String reason;

            if (!nativeAvailable) {
                status = ModuleStatus.FALLBACK;
                reason = nativeUnavailableReason;
            } else if (module == ModuleId.CORE) {
                status = ModuleStatus.AVAILABLE;
                reason = null;
            } else if (!config.isModuleEnabled(module)) {
                status = ModuleStatus.DISABLED;
                reason = "disabled-by-config";
            } else if (!NativeFeatures.isSupported(featureBits, module)) {
                status = ModuleStatus.FALLBACK;
                reason = "feature-not-advertised";
            } else {
                status = ModuleStatus.AVAILABLE;
                reason = null;
            }

            statuses.add(new FerrumStatus(
                    module,
                    status,
                    reason,
                    counters
            ));
        }

        return List.copyOf(statuses);
    }

    private static long errorCount(FerrumStatus status) {
        return status.counters().errorsByCode().values().stream()
                .mapToLong(count -> count)
                .sum();
    }

}
