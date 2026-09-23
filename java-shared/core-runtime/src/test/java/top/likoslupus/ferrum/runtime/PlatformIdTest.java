package top.likoslupus.ferrum.runtime;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PlatformIdTest {

    @Test
    void eightSupportedTargetsAndUnsupported() {
        var supported = Arrays.stream(PlatformId.values())
                .filter(PlatformId::isSupported)
                .count();

        assertEquals(8L, supported);
        assertFalse(PlatformId.UNSUPPORTED.isSupported());
        assertTrue(PlatformId.LINUX_GLIBC_X86_64.isSupported());
    }

}
