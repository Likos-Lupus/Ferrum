package top.likoslupus.ferrum.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureStateTest {

    @Test
    void availableHasNoReasonAndIsNative() {
        var state = FeatureState.available(ModuleId.CORE);
        assertEquals(ModuleStatus.AVAILABLE, state.status());
        assertNull(state.reason());
        assertTrue(state.isNative());
    }

    @Test
    void fallbackCarriesReasonAndIsNotNative() {
        var state = FeatureState.fallback(ModuleId.NOISE, "selftest-failed");
        assertEquals(ModuleStatus.FALLBACK, state.status());
        assertEquals("selftest-failed", state.reason());
        assertFalse(state.isNative());
    }

    @Test
    void disabledCarriesReason() {
        var state = FeatureState.disabled(ModuleId.PATH, "disabled-by-config");
        assertEquals(ModuleStatus.DISABLED, state.status());
        assertEquals("disabled-by-config", state.reason());
    }

}
