package top.likoslupus.ferrum.runtime.ffm;

import top.likoslupus.ferrum.runtime.NativeRuntimeState;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Owns a loaded Ferrum native library and the FFM handles needed for the ABI self-check.
 *
 * <p>Use {@link #tryLoad(Path)} to obtain an instance. Loading never throws: any failure is
 * reported through {@link #state()} so callers can fall back to the Java path.
 */
public final class NativeRuntime implements AutoCloseable {

    private static final int EXPECTED_ABI = 1;
    private static final long SELFTEST_INPUT = 0x5EED_1234L;

    private final @Nullable Arena arena;
    private final @Nullable MethodHandle abiVersion;
    private final @Nullable MethodHandle selftest;
    private final NativeRuntimeState state;
    private final @Nullable String reason;

    private NativeRuntime(
            @Nullable Arena arena,
            @Nullable MethodHandle abiVersion,
            @Nullable MethodHandle selftest,
            NativeRuntimeState state,
            @Nullable String reason
    ) {
        this.arena = arena;
        this.abiVersion = abiVersion;
        this.selftest = selftest;
        this.state = state;
        this.reason = reason;
    }

    /**
     * Attempts to load the native library and run the ABI self-check.
     *
     * @param library the library file to load
     *
     * @return a runtime whose {@link #state()} is {@link NativeRuntimeState#AVAILABLE} on success
     */
    public static NativeRuntime tryLoad(Path library) {
        var arena = Arena.ofShared();
        try {
            var lookup = SymbolLookup.libraryLookup(library, arena);
            var linker = Linker.nativeLinker();
            var abi = linker.downcallHandle(
                    lookup.find("ferrum_abi_version").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT)
            );
            var selftest = linker.downcallHandle(
                    lookup.find("ferrum_selftest_checksum").orElseThrow(),
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_LONG,
                            ValueLayout.ADDRESS
                    )
            );
            var version = (int) abi.invokeExact();
            if (version != EXPECTED_ABI) {
                arena.close();
                return failed(
                        NativeRuntimeState.ABI_MISMATCH,
                        "abi=" + version
                );
            }

            var output = arena.allocate(ValueLayout.JAVA_LONG);
            var status = (int) selftest.invokeExact(SELFTEST_INPUT, output);
            if (status != 0) {
                arena.close();
                return failed(
                        NativeRuntimeState.SELFTEST_FAILED,
                        "status=" + status
                );
            }

            return new NativeRuntime(
                    arena,
                    abi,
                    selftest,
                    NativeRuntimeState.AVAILABLE,
                    null
            );
        } catch (RuntimeException | LinkageError throwable) {
            arena.close();
            return failed(
                    NativeRuntimeState.LOAD_FAILED,
                    throwable.getClass().getSimpleName()
            );
        } catch (Throwable throwable) {
            arena.close();
            return failed(
                    NativeRuntimeState.SELFTEST_FAILED,
                    throwable.getClass().getSimpleName()
            );
        }
    }

    private static NativeRuntime failed(
            NativeRuntimeState state,
            @Nullable String reason
    ) {
        return new NativeRuntime(
                null,
                null,
                null,
                state,
                reason
        );
    }

    public NativeRuntimeState state() {
        return state;
    }

    public boolean isAvailable() {
        return state.isAvailable();
    }

    public @Nullable String reason() {
        return reason;
    }

    public int abiVersion() {
        var handle = require(abiVersion);
        try {
            return (int) handle.invokeExact();
        } catch (Throwable throwable) {
            throw new IllegalStateException("ferrum_abi_version failed", throwable);
        }
    }

    private MethodHandle require(@Nullable MethodHandle handle) {
        if (handle == null || !state.isAvailable()) {
            throw new IllegalStateException("native runtime not available: " + state);
        }
        return handle;
    }

    public long selftest(long input) {
        var handle = require(selftest);
        var memory = Objects.requireNonNull(arena, "arena");
        var output = memory.allocate(ValueLayout.JAVA_LONG);
        try {
            var status = (int) handle.invokeExact(input, output);
            if (status != 0) {
                throw new IllegalStateException("ferrum_selftest_checksum status=" + status);
            }
            return output.get(ValueLayout.JAVA_LONG, 0L);
        } catch (Throwable throwable) {
            throw new IllegalStateException("ferrum_selftest_checksum failed", throwable);
        }
    }

    @Override
    public void close() {
        if (arena != null) {
            arena.close();
        }
    }

}
