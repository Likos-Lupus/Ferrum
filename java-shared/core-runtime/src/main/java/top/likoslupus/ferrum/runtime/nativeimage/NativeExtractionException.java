package top.likoslupus.ferrum.runtime.nativeimage;

import java.io.Serial;

/**
 * Signals that a native library could not be verified or extracted.
 *
 * <p>This is a clean fallback condition: callers report it as an explainable reason and continue
 * on the Java path.
 */
public class NativeExtractionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NativeExtractionException(String message) {
        super(message);
    }

    public NativeExtractionException(String message, Throwable cause) {
        super(message, cause);
    }

}
