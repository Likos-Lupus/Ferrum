package top.likoslupus.ferrum.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.config.FerrumConfigLoader;
import top.likoslupus.ferrum.runtime.ffm.NativeBindings;
import top.likoslupus.ferrum.runtime.nativeimage.NativeLibraryResolution;
import top.likoslupus.ferrum.runtime.nativeimage.NativeLibraryResolver;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loader-agnostic FerrumCore initialization.
 *
 * <p>The loader entrypoint calls {@link #initialize(Path)} once; it loads the configuration,
 * locates the packaged or development native library, initializes {@link FerrumRuntime}, and logs
 * the startup status. Initialization never throws.
 */
public final class FerrumCoreBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(FerrumCoreBootstrap.class);

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private FerrumCoreBootstrap() {
    }

    /**
     * Initializes the FerrumCore runtime for the given game directory.
     *
     * @param gameDir the game directory
     */
    public static void initialize(Path gameDir) {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        var configFile = gameDir.resolve("config").resolve(FerrumConfigLoader.FILE_NAME);
        var config = FerrumConfigLoader.load(configFile);
        var resolution = resolveLibrary(gameDir, config);

        FerrumRuntime.instance().initializeResolved(config, resolution);
        LOGGER.info(status());
    }

    private static NativeLibraryResolution resolveLibrary(Path gameDir, FerrumConfig config) {
        return NativeLibraryResolver.resolve(
                FerrumCoreBootstrap.class.getClassLoader(),
                gameDir.resolve(".ferrum").resolve("native"),
                NativeBindings.EXPECTED_ABI,
                FerrumCore.VERSION,
                config.nativeSettings().verifyChecksums()
        );
    }

    /**
     * Returns the current status report, including the mixin sentinel state.
     *
     * @return the status report
     */
    public static String status() {
        return "FerrumCore version=" + FerrumCore.VERSION
                + " mixin-applied=" + FerrumCore.isMixinApplied()
                + System.lineSeparator()
                + FerrumRuntime.instance().statusReport();
    }

}
