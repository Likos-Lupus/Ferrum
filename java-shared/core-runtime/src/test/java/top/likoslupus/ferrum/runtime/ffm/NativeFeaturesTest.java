package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.api.ModuleId;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NativeFeaturesTest {

    @Test
    void coreIsAlwaysSupported() {
        assertTrue(NativeFeatures.isSupported(0L, ModuleId.CORE));
    }

    @Test
    void optionalModulesRequireTheirBit() {
        assertTrue(NativeFeatures.isSupported(1L, ModuleId.NBT));
        assertFalse(NativeFeatures.isSupported(1L, ModuleId.NOISE));
        assertFalse(NativeFeatures.isSupported(0L, ModuleId.NBT));
    }

    @Test
    void decodeReturnsAdvertisedModules() {
        var modules = NativeFeatures.decode((1L << 0) | (1L << 3));
        assertEquals(Set.of(ModuleId.NBT, ModuleId.NOISE), modules);
    }

    @Test
    void noBitsMeansNoOptionalModules() {
        assertTrue(NativeFeatures.decode(0L).isEmpty());
    }

}
