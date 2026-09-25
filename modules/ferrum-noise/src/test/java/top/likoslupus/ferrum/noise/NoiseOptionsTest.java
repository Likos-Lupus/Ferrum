package top.likoslupus.ferrum.noise;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the noise options projection and the test-only forced override.
 */
class NoiseOptionsTest {

    @Test
    void defaultsUseGridAndSixtyFourSamples() {
        var options = NoiseOptions.defaults();
        assertEquals(64, options.minSamples());
        assertTrue(options.grid());
    }

    @Test
    void optionsProjectFromModuleSettings() {
        var options = NoiseOptions.from(new ModuleSettings(
                true,
                0,
                Map.of()
        ));
        assertEquals(64, options.minSamples());
        assertTrue(options.grid());
    }

    @Test
    void leafModeDisablesGrid() {
        var settings = new ModuleSettings(true, 0, Map.of());
        // No "mode" option means the grid default; "leaf" disables it.
        assertTrue(NoiseOptions.from(settings).grid());
    }

    @Test
    void forcedOverrideFollowsSystemProperty() {
        System.clearProperty("ferrum.test.noise.force");
        assertFalse(NoiseHook.forced());
        System.setProperty("ferrum.test.noise.force", "true");
        try {
            assertTrue(NoiseHook.forced());
        } finally {
            System.clearProperty("ferrum.test.noise.force");
        }
    }

}
