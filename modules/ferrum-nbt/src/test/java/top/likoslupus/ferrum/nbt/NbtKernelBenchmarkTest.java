package top.likoslupus.ferrum.nbt;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.io.*;
import java.util.Optional;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kernel crossover benchmark: vanilla parse versus the native parse and materialize path.
 *
 * <p>This is an interim, dependency-free runner that reports the FFM crossover so {@code minBatch}
 * can be fixed. It is tagged {@code benchmark} and excluded from normal test runs; run it with the
 * {@code nbtBenchmark} Gradle task. It does not itself enable the fast path: the advertised feature
 * bit stays clear until the dual gates are met.
 */
@org.junit.jupiter.api.Tag("benchmark")
class NbtKernelBenchmarkTest {

    private static final int WARMUP = 20;
    private static final int ITERATIONS = 100;

    private static @Nullable Object sink;

    @BeforeAll
    static void initializeRuntime() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), library);
        var runtime = FerrumRuntime.instance().nativeRuntime();
        assumeTrue(runtime != null && runtime.isAvailable(), "native runtime unavailable");
    }

    @AfterAll
    static void resetRuntime() {
        FerrumRuntime.instance().reset();
    }

    @Test
    void reportCrossover() throws IOException {
        IO.println(
                "bytes,vanillaNamedNs,nativeNamedNs,ratio,vanillaAnyNs,nativeAnyNs,anyRatio"
        );
        for (var entries : new int[]{40, 800, 3000}) {
            var tag = corpus(entries);
            var named = namedBytes(tag);
            var any = anyBytes(tag);

            var vanillaNamed = measure(() -> {
                try {
                    sink = NbtIo.read(new DataInputStream(new ByteArrayInputStream(named)));
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            var nativeNamed = measure(() ->
                    sink = NativeNbt.parseNamed(named, NbtLimits.forBuffer(named.length))
            );

            var vanillaAny = measure(() -> {
                var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(any));
                try {
                    sink = buffer.readNbt();
                } finally {
                    buffer.release();
                }
            });
            var nativeAny = measure(() -> {
                var result = NativeNbt.parseAny(
                        any,
                        0,
                        any.length,
                        NbtLimits.forBuffer(any.length)
                );
                sink = Optional.ofNullable(result)
                        .map(NativeNbt.AnyResult::tag)
                        .orElse(null);
            });

            System.out.printf(
                    "%d,%d,%d,%.3f,%d,%d,%.3f%n",
                    named.length,
                    vanillaNamed,
                    nativeNamed,
                    ratio(vanillaNamed, nativeNamed),
                    vanillaAny,
                    nativeAny,
                    ratio(vanillaAny, nativeAny)
            );
        }
        if (sink == null) {
            throw new IllegalStateException("benchmark produced no result");
        }
    }

    private static CompoundTag corpus(int entries) {
        var tag = new CompoundTag();
        tag.putString("name", "benchmark");
        IntStream.range(0, entries)
                .forEach(index -> {
                    tag.putInt("i" + index, index);
                    tag.putLong("l" + index, (long) index * 31L);
                    if (index % 4 == 0) {
                        tag.putString("s" + index, "value-" + index);
                    }
                    if (index % 16 == 0) {
                        tag.putDouble("d" + index, index * 0.5);
                    }
                });
        return tag;
    }

    private static byte[] namedBytes(CompoundTag tag) throws IOException {
        var out = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(out));
        return out.toByteArray();
    }

    private static byte[] anyBytes(Tag tag) throws IOException {
        var buffer = Unpooled.buffer();
        try {
            FriendlyByteBuf.writeNbt(buffer, tag);
            var bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static long measure(Runnable action) {
        IntStream.range(0, WARMUP).forEach(_ -> action.run());
        var best = Long.MAX_VALUE;
        for (var index = 0; index < ITERATIONS; index++) {
            var start = System.nanoTime();
            action.run();
            best = Math.min(best, System.nanoTime() - start);
        }
        return best;
    }

    private static double ratio(long vanilla, long candidate) {
        return candidate == 0
                ? 0.0
                : (double) vanilla / (double) candidate;
    }

}
