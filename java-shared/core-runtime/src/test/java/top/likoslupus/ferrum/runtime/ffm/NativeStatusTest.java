package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class NativeStatusTest {

    @Test
    void decodesKnownCodes() {
        assertEquals(NativeStatus.OK, NativeStatus.fromCode(0));
        assertEquals(NativeStatus.BUFFER_TOO_SMALL, NativeStatus.fromCode(-2));
        assertEquals(NativeStatus.ABI_MISMATCH, NativeStatus.fromCode(-6));
        assertEquals(NativeStatus.PANIC, NativeStatus.fromCode(-127));
    }

    @Test
    void unknownCodeMapsToUnknown() {
        assertEquals(NativeStatus.UNKNOWN, NativeStatus.fromCode(42));
    }

    @Test
    void classifiesFatalAndCountingStatuses() {
        assertTrue(NativeStatus.PANIC.isSessionFatal());
        assertTrue(NativeStatus.ABI_MISMATCH.isSessionFatal());
        assertFalse(NativeStatus.INTERNAL.isSessionFatal());

        assertTrue(NativeStatus.INTERNAL.doesCountTowardCircuitBreaker());
        assertFalse(NativeStatus.MALFORMED_INPUT.doesCountTowardCircuitBreaker());
        assertFalse(NativeStatus.OK.doesCountTowardCircuitBreaker());
    }

    @Test
    void everyStatusRoundTripsThroughItsCode() {
        Arrays.stream(NativeStatus.values())
                .filter(status -> status != NativeStatus.UNKNOWN)
                .forEach(status -> assertEquals(
                        status,
                        NativeStatus.fromCode(status.code()),
                        status.name()
                ));
    }

}
