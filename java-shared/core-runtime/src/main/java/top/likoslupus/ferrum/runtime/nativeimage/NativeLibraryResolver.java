package top.likoslupus.ferrum.runtime.nativeimage;

import top.likoslupus.ferrum.runtime.NativeRuntimeState;
import top.likoslupus.ferrum.runtime.PlatformId;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the native library to load: the packaged resource when present, otherwise a development
 * build, otherwise an explainable fallback state.
 *
 * <p>The resolver never throws. A packaged library that is present but broken maps to
 * {@link NativeRuntimeState#EXTRACT_FAILED}; a library that is simply not packaged falls through to
 * the development locator.
 */
public final class NativeLibraryResolver {

    private NativeLibraryResolver() {
    }

    /**
     * Resolves the library for the current platform.
     *
     * @param loader          the class loader that owns the mod resources
     * @param baseDir         the extraction cache root
     * @param expectedAbi     the ABI version this runtime understands
     * @param modVersion      the Ferrum mod version
     * @param verifyChecksums whether to verify the manifest SHA-256
     *
     * @return the resolution; never {@code null}
     */
    public static NativeLibraryResolution resolve(
            ClassLoader loader,
            Path baseDir,
            int expectedAbi,
            String modVersion,
            boolean verifyChecksums
    ) {
        var platform = PlatformId.current();
        if (!platform.isSupported()) {
            return NativeLibraryResolution.failed(
                    NativeRuntimeState.PLATFORM_UNSUPPORTED,
                    "platform=" + platform.id()
            );
        }

        var install = NativeResourceInstaller.installResult(
                loader,
                baseDir,
                expectedAbi,
                modVersion,
                verifyChecksums,
                platform
        );
        switch (install.status()) {
            case INSTALLED -> {
                var library = install.library();
                if (library != null) {
                    return NativeLibraryResolution.found(library, install.sha256());
                }
            }
            case ABI_MISMATCH -> {
                return NativeLibraryResolution.failed(
                        NativeRuntimeState.ABI_MISMATCH,
                        install.reason()
                );
            }
            case INTEGRITY_FAILED -> {
                return NativeLibraryResolution.failed(
                        NativeRuntimeState.EXTRACT_FAILED,
                        install.reason()
                );
            }
            case ABSENT -> {
            }
        }

        var development = NativeLibraryLocator.find();
        return Optional.ofNullable(development)
                .map(path -> NativeLibraryResolution.found(path, null))
                .orElseGet(() -> NativeLibraryResolution.failed(
                        NativeRuntimeState.LOAD_FAILED,
                        "library-not-found"
                ));

    }

}
