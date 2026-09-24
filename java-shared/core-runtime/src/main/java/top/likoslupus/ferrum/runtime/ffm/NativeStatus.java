package top.likoslupus.ferrum.runtime.ffm;

import java.util.Arrays;

/**
 * Decoded Ferrum native status codes.
 *
 * <p>The numeric values match {@code FerrumStatus} in the C ABI. Java gates on this enum instead
 * of raw integers so that every call site shares the same fallback and circuit-breaker policy.
 */
public enum NativeStatus {

    OK(0),
    INVALID_ARGUMENT(-1),
    BUFFER_TOO_SMALL(-2),
    MALFORMED_INPUT(-3),
    LIMIT_EXCEEDED(-4),
    UNSUPPORTED(-5),
    ABI_MISMATCH(-6),
    INTERNAL(-7),
    PANIC(-127),
    UNKNOWN(Integer.MIN_VALUE);

    private final int code;

    NativeStatus(int code) {
        this.code = code;
    }

    /**
     * Decodes a status code, mapping unknown values to {@link #UNKNOWN}.
     *
     * @param code the C ABI status code
     *
     * @return the matching status, or {@link #UNKNOWN}
     */
    public static NativeStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * Returns the on-the-wire status code.
     *
     * @return the C ABI status code
     */
    public int code() {
        return code;
    }

    /**
     * Returns whether the call succeeded.
     *
     * @return {@code true} for {@link #OK}
     */
    public boolean isOk() {
        return this == OK;
    }

    /**
     * Returns whether the whole native runtime must be disabled for the session.
     *
     * @return {@code true} for {@link #ABI_MISMATCH} and {@link #PANIC}
     */
    public boolean isSessionFatal() {
        return this == ABI_MISMATCH || this == PANIC;
    }

    /**
     * Returns whether this status counts toward per-module circuit breaking.
     *
     * <p>Malformed external input is never counted: bad data may come from a world or network
     * payload and must not disable an otherwise healthy module.
     *
     * @return {@code true} for {@link #INTERNAL} and {@link #PANIC}
     */
    public boolean doesCountTowardCircuitBreaker() {
        return this == INTERNAL || this == PANIC;
    }

}
