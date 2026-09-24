package top.likoslupus.ferrum.runtime.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.likoslupus.ferrum.api.ModuleId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

import static java.util.Objects.requireNonNull;

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
                },
                "codec": {
                  "minBatch": 4096,
                  "options": {
                    "preferLz4ForNewWrites": true,
                    "accelerateExistingLz4": false,
                    "bogusObject": {
                      "nested": 1
                    }
                  }
                },
                "unknownModule": {
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
    }

    @Test
    void unlistedModulesInheritTheirDefaults(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("ferrum.json");
        Files.writeString(file, VALID);

        var config = FerrumConfigLoader.load(file);

        // Default-enabled MVP modules stay enabled even though the file lists only some modules.
        assertTrue(config.isModuleEnabled(ModuleId.CODEC));
        assertTrue(config.isModuleEnabled(ModuleId.PALETTE));
        assertTrue(config.isModuleEnabled(ModuleId.NOISE));
        assertFalse(config.isModuleEnabled(ModuleId.COLLIDE));
    }

    @Test
    void moduleOptionsAreMergedWithDefaultsAndScalarsOnly(
            @TempDir Path tempDir
    ) throws IOException {
        var file = tempDir.resolve("ferrum.json");
        Files.writeString(file, VALID);

        var config = FerrumConfigLoader.load(file);
        var codec = requireNonNull(config.modules().get("codec"), "codec");

        assertEquals(4096, codec.minBatch());
        assertTrue(codec.optionBoolean("preferLz4ForNewWrites", false));
        assertFalse(codec.optionBoolean("accelerateExistingLz4", true));
        assertTrue(codec.optionBoolean("lz4", true));
        assertFalse(codec.options().containsKey("bogusObject"));
    }

    @Test
    void unknownModulesAreIgnored(@TempDir Path tempDir) throws IOException {
        var file = tempDir.resolve("ferrum.json");
        Files.writeString(file, VALID);

        var config = FerrumConfigLoader.load(file);

        assertFalse(config.modules().containsKey("unknownModule"));
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
