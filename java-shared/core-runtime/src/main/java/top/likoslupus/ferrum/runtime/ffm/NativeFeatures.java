package top.likoslupus.ferrum.runtime.ffm;

import top.likoslupus.ferrum.api.ModuleId;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps {@code ferrum_feature_bits()} to the {@link ModuleId} modules this build actually
 * implements.
 *
 * <p>A feature bit is advertised only once its module passes the correctness gates. A missing bit
 * means the module must stay on the Java fallback path regardless of symbol presence.
 */
public final class NativeFeatures {

    private static final Map<ModuleId, Long> BITS = new EnumMap<>(ModuleId.class);

    static {
        BITS.put(ModuleId.NBT, 1L << 0);
        BITS.put(ModuleId.CODEC, 1L << 1);
        BITS.put(ModuleId.PALETTE, 1L << 2);
        BITS.put(ModuleId.NOISE, 1L << 3);
        BITS.put(ModuleId.LIGHT, 1L << 4);
        BITS.put(ModuleId.COLLIDE, 1L << 5);
        BITS.put(ModuleId.PATH, 1L << 6);
    }

    private NativeFeatures() {
    }

    /**
     * Returns whether the given feature bits advertise the module as natively implemented.
     *
     * <p>{@link ModuleId#CORE} is always considered supported: it is the runtime itself, not an
     * optional feature bit.
     *
     * @param featureBits the bits returned by {@code ferrum_feature_bits()}
     * @param module      the module to test
     *
     * @return {@code true} when the module may use its native path
     */
    public static boolean isSupported(long featureBits, ModuleId module) {
        if (module == ModuleId.CORE) {
            return true;
        }

        var bit = BITS.get(module);
        return bit != null && (featureBits & bit) != 0L;
    }

    /**
     * Returns the set of natively implemented modules advertised by the given bits.
     *
     * @param featureBits the bits returned by {@code ferrum_feature_bits()}
     *
     * @return the advertised modules, excluding {@link ModuleId#CORE}
     */
    public static Set<ModuleId> decode(long featureBits) {
        return BITS.entrySet().stream()
                .filter(entry ->
                        (featureBits & entry.getValue()) != 0L
                )
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(
                        () -> EnumSet.noneOf(ModuleId.class)
                ));
    }

}
