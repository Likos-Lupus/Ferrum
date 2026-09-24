package top.likoslupus.ferrum.runtime.nativeimage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;

class NativeResourceInstallerTest {

    @Test
    void returnsNullWhenNothingIsPackaged(@TempDir Path tempDir) {
        var installed = NativeResourceInstaller.install(
                getClass().getClassLoader(),
                tempDir,
                1,
                "0.1.0",
                true
        );

        assertNull(installed);
    }

}
