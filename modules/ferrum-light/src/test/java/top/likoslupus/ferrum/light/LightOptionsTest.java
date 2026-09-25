package top.likoslupus.ferrum.light;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LightOptionsTest {

    @Test
    void defaultsUseTheKernelSectionLimit() {
        assertEquals(
                512,
                LightOptions.defaults().maxSections()
        );
    }

    @Test
    void optionsFallBackWhenAbsent() {
        assertEquals(
                512,
                LightOptions.from(new ModuleSettings(true)).maxSections()
        );
    }

    @Test
    void maxSectionsMustBePositive() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LightOptions(0)
        );
    }

}
