package top.likoslupus.ferrum.runtime.ffm;

import top.likoslupus.ferrum.api.ModuleId;

import java.util.Set;

/**
 * Decoded {@code ferrum_build_info} payload.
 *
 * @param structSize   the native {@code FerrumBuildInfo} size in bytes
 * @param abiVersion   the ABI version the library was built against
 * @param featureBits  the advertised feature bits
 * @param gitCommitHex the 40-character hex git commit, or 40 zeros when unknown
 */
public record NativeBuildInfo(
        int structSize,
        int abiVersion,
        long featureBits,
        String gitCommitHex
) {

    /**
     * Returns the modules advertised by this build.
     *
     * @return the natively implemented modules, excluding {@link ModuleId#CORE}
     */
    public Set<ModuleId> features() {
        return NativeFeatures.decode(featureBits);
    }

}
