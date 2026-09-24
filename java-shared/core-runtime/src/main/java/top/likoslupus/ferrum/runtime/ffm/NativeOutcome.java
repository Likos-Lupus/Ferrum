package top.likoslupus.ferrum.runtime.ffm;

import org.jspecify.annotations.Nullable;

/**
 * The result of a single native call.
 *
 * @param status the decoded status
 * @param value  the value produced on success, or {@code null} when the call writes into caller
 *               buffers or failed
 * @param <T>    the produced value type
 */
public record NativeOutcome<T>(
        NativeStatus status,
        @Nullable T value
) {

    /**
     * Creates a successful outcome.
     *
     * @param value the produced value, or {@code null} for in-place calls
     * @param <T>   the value type
     *
     * @return an {@link NativeStatus#OK} outcome
     */
    public static <T> NativeOutcome<T> ok(@Nullable T value) {
        return new NativeOutcome<>(NativeStatus.OK, value);
    }

    /**
     * Creates a failed outcome with no value.
     *
     * @param status the failure status
     * @param <T>    the value type
     *
     * @return a failed outcome
     */
    public static <T> NativeOutcome<T> failure(NativeStatus status) {
        return new NativeOutcome<>(status, null);
    }

    /**
     * Returns whether the call succeeded.
     *
     * @return {@code true} when {@link #status()} is {@link NativeStatus#OK}
     */
    public boolean isOk() {
        return status.isOk();
    }

}
