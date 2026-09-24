package top.likoslupus.ferrum.runtime.config;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.api.ModuleId;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FerrumConfigTest {

    @Test
    void defaultsEnableNativeAndMvpModulesOnly() {
        var config = FerrumConfig.defaults();

        assertTrue(config.isNativeEnabled());
        assertTrue(config.isModuleEnabled(ModuleId.CORE));
        assertTrue(config.isModuleEnabled(ModuleId.NBT));
        assertTrue(config.isModuleEnabled(ModuleId.CODEC));
        assertTrue(config.isModuleEnabled(ModuleId.PALETTE));
        assertTrue(config.isModuleEnabled(ModuleId.NOISE));
        assertFalse(config.isModuleEnabled(ModuleId.LIGHT));
        assertFalse(config.isModuleEnabled(ModuleId.COLLIDE));
        assertFalse(config.isModuleEnabled(ModuleId.PATH));
    }

    @Test
    void missingModuleEntryIsDisabled() {
        var config = new FerrumConfig(
                new NativeSettings(
                        true,
                        true,
                        true,
                        false
                ),
                Map.of(
                        "nbt",
                        new ModuleSettings(true)
                )
        );

        assertTrue(config.isModuleEnabled(ModuleId.NBT));
        assertFalse(config.isModuleEnabled(ModuleId.CODEC));
    }

    @Test
    void disabledNativeDisablesEveryModule() {
        var config = new FerrumConfig(
                new NativeSettings(
                        false,
                        true,
                        true,
                        false
                ),
                Map.of(
                        "nbt",
                        new ModuleSettings(true)
                )
        );

        assertFalse(config.isNativeEnabled());
        assertFalse(config.isModuleEnabled(ModuleId.CORE));
        assertFalse(config.isModuleEnabled(ModuleId.NBT));
    }

}
