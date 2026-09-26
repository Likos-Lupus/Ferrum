package top.likoslupus.ferrum.collide;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;

import static org.junit.jupiter.api.Assertions.*;

class CollideOptionsTest {

    @Test
    void defaultsEnableBothPaths() {
        var options = CollideOptions.defaults();
        assertTrue(options.clip());
        assertTrue(options.sweep());
        assertEquals(8, options.minBoxes());
        assertEquals(4, options.minShapes());
    }

    @Test
    void optionsFallBackWhenAbsent() {
        assertEquals(
                CollideOptions.defaults(),
                CollideOptions.from(new ModuleSettings(true))
        );
    }

    @Test
    void minimumsMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollideOptions(true, true, 0, 4)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new CollideOptions(true, true, 8, 0)
        );
    }

}
