package top.likoslupus.ferrum.runtime.ffm;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

import static java.util.Objects.requireNonNull;

/**
 * An opaque native handle with an exactly-once destroy contract.
 *
 * <p>Long-lived native objects are closed explicitly through {@link #close()} on their world or
 * resource lifecycle. A {@link Cleaner} is registered only as a leak backstop for forgotten
 * handles; it is never the normal release path.
 */
public final class NativeHandle implements AutoCloseable {

    private static final Cleaner CLEANER = Cleaner.create();

    private final long value;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Cleaner.Cleanable cleanable;

    private NativeHandle(long value, LongConsumer destroyer) {
        this.value = value;
        this.cleanable = CLEANER.register(
                this,
                new DestroyAction(destroyer, value)
        );
    }

    /**
     * Creates a handle wrapper.
     *
     * @param value     the non-zero native handle value
     * @param destroyer the native destroy function, called exactly once
     *
     * @return the handle wrapper
     */
    public static NativeHandle of(long value, LongConsumer destroyer) {
        if (value == 0L) {
            throw new IllegalArgumentException("native handle value must be non-zero");
        }

        requireNonNull(destroyer, "destroyer");
        return new NativeHandle(value, destroyer);
    }

    /**
     * Returns the opaque native value.
     *
     * @return the native handle value
     */
    public long value() {
        return value;
    }

    /**
     * Returns whether the handle has been closed.
     *
     * @return {@code true} after {@link #close()}
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cleanable.clean();
        }
    }

    private record DestroyAction(
            LongConsumer destroyer,
            long value
    ) implements Runnable {

        @Override
        public void run() {
            destroyer.accept(value);
        }

    }

}
