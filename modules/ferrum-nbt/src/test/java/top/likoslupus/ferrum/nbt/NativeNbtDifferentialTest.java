package top.likoslupus.ferrum.nbt;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential tests: the native parser must materialize a tree equal to the vanilla reference.
 */
@org.junit.jupiter.api.Tag("native")
class NativeNbtDifferentialTest {

    @BeforeAll
    static void initializeRuntime() {
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), library);
        var runtime = FerrumRuntime.instance().nativeRuntime();
        assumeTrue(
                runtime != null && runtime.isAvailable(),
                "native runtime unavailable"
        );
    }

    @AfterAll
    static void resetRuntime() {
        FerrumRuntime.instance().reset();
    }

    @Test
    void namedParseMatchesVanilla() throws IOException {
        var tag = sample();
        var bytes = namedBytes(tag);

        var parsed = NativeNbt.parseNamed(bytes, NbtLimits.forBuffer(bytes.length));

        assertNotNull(parsed);
        assertEquals(tag, parsed);
    }

    static CompoundTag sample() {
        var root = new CompoundTag();
        root.putInt("x", 5);
        root.putString("s", "hi");
        root.putByteArray("ba", new byte[]{1, 2, 3});
        root.putIntArray("ia", new int[]{7, 8, 9});
        root.putLongArray("la", new long[]{1L, 2L});
        root.putString("nul", "\u0000");
        root.putString("two", "café");
        root.putString("three", "日本語");
        root.putString("emoji", "\uD83D\uDE00");

        var list = new ListTag();
        list.add(IntTag.valueOf(1));
        list.add(IntTag.valueOf(2));
        root.put("l", list);

        var nested = new CompoundTag();
        nested.putDouble("d", 3.5);
        nested.put("deep", DoubleTag.valueOf(1.25));
        root.put("c", nested);
        return root;
    }

    static byte[] namedBytes(CompoundTag tag) throws IOException {
        var out = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(out));
        return out.toByteArray();
    }

    @Test
    void anyParseMatchesVanillaAndConsumesEverything() throws IOException {
        var tag = sample();
        var bytes = anyBytes(tag);

        var result = NativeNbt.parseAny(
                bytes,
                0,
                bytes.length,
                NbtLimits.forBuffer(bytes.length)
        );

        assertNotNull(result);
        assertEquals(bytes.length, result.consumed());
        assertEquals(tag, result.tag());
    }

    static byte[] anyBytes(net.minecraft.nbt.Tag tag) throws IOException {
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

    @Test
    void nativeWriteRoundTripsThroughVanilla() throws IOException {
        var tag = sample();
        var named = namedBytes(tag);

        // Re-encode the parsed arena through the native writer and read it back with vanilla.
        var arena = NativeNbt.parseNamedToArena(named, NbtLimits.forBuffer(named.length));
        assertNotNull(arena);
        var rewritten = NativeNbt.writeNamed(arena.arena(), arena.root());
        assertNotNull(rewritten);
        assertEquals(
                tag, NbtIo.read(new DataInputStream(
                        new ByteArrayInputStream(rewritten)))
        );
    }

    @Test
    void malformedInputFallsBack() {
        var truncated = new byte[]{0x0A, 0x00, 0x00, 0x63};

        assertNull(NativeNbt.parseNamed(truncated, NbtLimits.forBuffer(truncated.length)));
    }

}
