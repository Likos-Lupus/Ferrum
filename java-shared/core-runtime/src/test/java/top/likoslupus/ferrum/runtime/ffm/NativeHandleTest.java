package top.likoslupus.ferrum.runtime.ffm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class NativeHandleTest {

    @Test
    void destroysExactlyOnce() {
        var destroyCalls = new AtomicInteger();
        var handle = NativeHandle.of(
                42L,
                _ -> destroyCalls.incrementAndGet()
        );

        assertEquals(42L, handle.value());
        assertFalse(handle.isClosed());

        handle.close();
        handle.close();

        assertEquals(1, destroyCalls.get());
        assertTrue(handle.isClosed());
    }

    @Test
    void rejectsZeroValue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeHandle.of(
                        0L,
                        _ -> {
                        }
                )
        );
    }

    @Test
    void passesTheValueToTheDestroyer() {
        var seen = new AtomicLong();
        var handle = NativeHandle.of(7L, seen::set);

        handle.close();

        assertEquals(7L, seen.get());
    }

}
