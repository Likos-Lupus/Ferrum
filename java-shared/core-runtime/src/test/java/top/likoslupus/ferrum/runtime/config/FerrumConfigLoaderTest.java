package top.likoslupus.ferrum.runtime.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.likoslupus.ferrum.api.ModuleId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FerrumConfigLoaderTest {

    private static final String VALID = /* language=JSON */ """
            {
              "native": {
                "enabled": true,
                "strictAbi": true,
                "verifyChecksums": true,
                "diagnostics": false
              },
              "modules": {
                "nbt": {
                  "enabled": false
                },
                "light": {
                  "enabled": true
                }
              }
            }
            """;

    @Test
    void missingFileFallsBackToDefaults(@TempDir Path tempDir) {
        var config = FerrumConfigLoader.load(tempDir.resolve("absent.json"));

        assertTrue(config.isNativeEnabled());
        assertTrue(config.isModuleEnabled(ModuleId.NBT));
    }

    @Test
    void readsValidConfiguration(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("ferrum.json");
        Files.writeString(file, VALID);

        var config = FerrumConfigLoader.load(file);

        assertTrue(config.isNativeEnabled());
        assertFalse(config.isModuleEnabled(ModuleId.NBT));
        assertTrue(config.isModuleEnabled(ModuleId.LIGHT));
        assertFalse(config.isModuleEnabled(ModuleId.CODEC));
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("broken.json");
        Files.writeString(file, "{ this is not json");

        var config = FerrumConfigLoader.load(file);

        assertTrue(config.isNativeEnabled());
        assertTrue(config.isModuleEnabled(ModuleId.NBT));
        assertFalse(config.isModuleEnabled(ModuleId.LIGHT));
    }

}
