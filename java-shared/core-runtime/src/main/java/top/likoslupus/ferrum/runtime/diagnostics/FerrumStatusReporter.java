package top.likoslupus.ferrum.runtime.diagnostics;

import top.likoslupus.ferrum.api.FeatureState;
import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.runtime.NativeRuntimeState;
import top.likoslupus.ferrum.runtime.PlatformId;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeBuildInfo;
import top.likoslupus.ferrum.runtime.ffm.NativeDiagnostics;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;

import java.util.*;
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
        return report(
                state,
                reason,
                platform,
                abiVersion,
                featureBits,
                config,
                diagnostics,
                Runtime.version().feature(),
                null,
                null
        );
    }

    /**
     * Formats a multi-line status report including the runtime environment details.
     *
     * @param state         the native runtime state
     * @param reason        the runtime failure reason, or {@code null}
     * @param platform      the detected platform, or {@code null}
     * @param abiVersion    the native ABI version, or {@code 0} when unknown
     * @param featureBits   the advertised feature bits
     * @param config        the active configuration
     * @param diagnostics   the counters
     * @param javaFeature   the running Java feature release
     * @param buildInfo     the native build information, or {@code null}
     * @param librarySha256 the resolved library checksum, or {@code null}
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
            NativeDiagnostics diagnostics,
            int javaFeature,
            @Nullable NativeBuildInfo buildInfo,
            @Nullable String librarySha256
    ) {
        var builder = new StringBuilder()
                .append("Ferrum native: ")
                .append(state);

        if (platform != null) {
            builder.append(" platform=").append(platform.id());
        }
        builder.append(" java=").append(javaFeature);

        if (state.isAvailable()) {
            builder.append(" abi=").append(abiVersion)
                    .append(" features=").append(NativeFeatures.decode(featureBits));
        } else if (reason != null) {
            builder.append(" reason=").append(reason);
        }

        if (buildInfo != null) {
            builder.append(" commit=").append(buildInfo.gitCommitHex());
        }
        if (librarySha256 != null && librarySha256.length() >= 12) {
            builder.append(" sha256=").append(librarySha256, 0, 12);
        }

        appendModules(builder, state, featureBits, config, diagnostics);

        return builder.toString();
    }

    private static void appendModules(
            StringBuilder builder,
            NativeRuntimeState state,
            long featureBits,
            FerrumConfig config,
            NativeDiagnostics diagnostics
    ) {
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

        var moduleIds = ModuleId.values();
        var statuses = new ArrayList<FerrumStatus>(moduleIds.length);

        Arrays.stream(moduleIds)
                .forEach(module -> {
                    var counters = modules.getOrDefault(module, EMPTY_COUNTERS);
                    var moduleState = state(state, featureBits, config, module);
                    statuses.add(new FerrumStatus(
                            module,
                            moduleState.status(),
                            moduleState.reason(),
                            counters
                    ));
                });

        return List.copyOf(statuses);
    }

    private static long errorCount(FerrumStatus status) {
        return status.counters().errorsByCode().values().stream()
                .mapToLong(count -> count)
                .sum();
    }

    /**
     * Resolves the state of a single module.
     *
     * @param state       the native runtime state
     * @param featureBits the advertised feature bits
     * @param config      the active configuration
     * @param module      the module
     *
     * @return the module's state
     */
    public static FeatureState state(
            NativeRuntimeState state,
            long featureBits,
            FerrumConfig config,
            ModuleId module
    ) {
        if (!state.isAvailable()) {
            return FeatureState.fallback(
                    module,
                    "native-" + state.name().toLowerCase(Locale.ROOT)
            );
        }

        if (module == ModuleId.CORE) {
            return FeatureState.available(module);
        }
        if (!config.isModuleEnabled(module)) {
            return FeatureState.disabled(module, "disabled-by-config");
        }
        if (!NativeFeatures.isSupported(featureBits, module)) {
            return FeatureState.fallback(module, "feature-not-advertised");
        }

        return FeatureState.available(module);
    }

}
