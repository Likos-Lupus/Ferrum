package top.likoslupus.ferrum.palette;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the palette option projection.
 */
class PaletteOptionsTest {

    @Test
    void defaultsUseTwoHundredFiftySix() {
        assertEquals(256, PaletteOptions.defaults().minValues());
    }

    @Test
    void optionsOverrideTheThreshold() {
        var minValues = Objects.requireNonNull(
                DataFormats.json().readTree("{\"minValues\":1024}").get("minValues")
        );
        var settings = new ModuleSettings(
                true,
                0,
                Map.of("minValues", minValues)
        );
        assertEquals(1024, PaletteOptions.from(settings).minValues());
    }

    @Test
    void absentOptionFallsBack() {
        var settings = new ModuleSettings(true);
        assertEquals(256, PaletteOptions.from(settings).minValues());
    }

    @Test
    void negativeThresholdIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaletteOptions(-1)
        );
    }

}
