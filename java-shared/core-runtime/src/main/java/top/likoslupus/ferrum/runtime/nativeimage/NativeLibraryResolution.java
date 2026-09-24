package top.likoslupus.ferrum.runtime.nativeimage;

import top.likoslupus.ferrum.runtime.NativeRuntimeState;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Outcome of resolving which native library the runtime should load.
 *
 * <p>A resolution either points at an existing library or carries the fallback state and reason
 * that {@link top.likoslupus.ferrum.runtime.FerrumRuntime} must adopt. Resolution never throws.
 *
 * @param library      the library to load when found, otherwise {@code null}
 * @param sha256       the library checksum when known, otherwise {@code null}
 * @param failureState the runtime state to adopt when resolution failed, otherwise {@code null}
 * @param reason       a human-readable failure reason, otherwise {@code null}
 */
public record NativeLibraryResolution(
        @Nullable Path library,
        @Nullable String sha256,
        @Nullable NativeRuntimeState failureState,
        @Nullable String reason
) {

    public NativeLibraryResolution {
        if ((library == null) == (failureState == null)) {
            throw new IllegalArgumentException("exactly one of library or failureState must be set");
        }
    }

    /**
     * Creates a resolution pointing at a library.
     *
     * @param library the library to load
     * @param sha256  the library checksum, or {@code null} when unknown
     *
     * @return the resolution
     */
    public static NativeLibraryResolution found(Path library, @Nullable String sha256) {
        return new NativeLibraryResolution(
                Objects.requireNonNull(library, "library"),
                sha256,
                null,
                null
        );
    }

    /**
     * Creates a failed resolution.
     *
     * @param failureState the runtime state to adopt
     * @param reason       a human-readable reason
     *
     * @return the resolution
     */
    public static NativeLibraryResolution failed(
            NativeRuntimeState failureState,
            @Nullable String reason
    ) {
        return new NativeLibraryResolution(
                null,
                null,
                Objects.requireNonNull(failureState, "failureState"),
                reason
        );
    }

    /**
     * Returns whether a library was resolved.
     *
     * @return {@code true} when a library is available to load
     */
    public boolean isFound() {
        return library != null;
    }

}
