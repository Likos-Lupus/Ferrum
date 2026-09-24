package top.likoslupus.ferrum.runtime.scratch;

import org.junit.jupiter.api.Test;

import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import static org.junit.jupiter.api.Assertions.*;

class NativeScratchTest {

    @Test
    void growsToTheRequestedCapacity() {
        try (var scratch = NativeScratch.current()) {
            var small = scratch.bytes(16L);
            assertTrue(small.byteSize() >= 16L);
            assertEquals(0L, scratch.growthCount());

            var large = scratch.bytes(1L << 16);
            assertTrue(large.byteSize() >= (1L << 16));
            assertTrue(scratch.growthCount() >= 1L);
            assertNotSame(small, large);
        }
    }

    @Test
    void buffersAreReadableAndWritable() {
        try (var scratch = NativeScratch.current()) {
            var ints = scratch.ints(4L);
            ints.setAtIndex(ValueLayout.JAVA_INT, 0L, 123);
            assertEquals(123, ints.getAtIndex(ValueLayout.JAVA_INT, 0L));

            var doubles = scratch.doubles(2L);
            doubles.setAtIndex(ValueLayout.JAVA_DOUBLE, 1L, 2.5d);
            assertEquals(2.5d, doubles.getAtIndex(ValueLayout.JAVA_DOUBLE, 1L));
        }
    }

    @Test
    void currentIsPerThread() throws InterruptedException {
        try (var main = NativeScratch.current()) {
            var other = new AtomicReference<@Nullable NativeScratch>();
            var thread = new Thread(() -> {
                var scratch = NativeScratch.current();
                other.set(scratch);
                scratch.close();
            });
            thread.start();
            thread.join();

            assertNotSame(main, other.get());
        }
    }

    @Test
    void closedScratchRejectsFurtherUse() {
        var scratch = NativeScratch.current();
        scratch.close();

        assertTrue(scratch.isClosed());
        assertThrows(
                IllegalStateException.class,
                () -> scratch.bytes(1L)
        );
    }

}
