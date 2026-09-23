package top.likoslupus.ferrum.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FerrumCoreTest {

    @Test
    void modIdIsStable() {
        assertEquals("ferrum", FerrumCore.MOD_ID);
    }

}
