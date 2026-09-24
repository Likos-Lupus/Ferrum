package top.likoslupus.ferrum.nbt;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;

import top.likoslupus.ferrum.api.ModuleId;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.ModuleSettings;
import top.likoslupus.ferrum.runtime.ffm.NativeFeatures;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Mixin-facing entry point for the NBT buffer fast path.
 *
 * <p>Eligibility is deliberately conservative: the module must be enabled and advertised, the
 * buffer must be a single readable region within the safe bound, and the parse must produce a
 * compound root. Any other case falls back to vanilla untouched.
 */
public final class NbtHook {

    /**
     * The largest buffer the fast path handles.
     *
     * <p>The input length bounds the native structural caps, and this bound keeps the worst-case
     * decoded accounting well inside vanilla's smallest NBT quota, so the fast path never accepts a
     * document the vanilla {@code NbtAccounter} would have rejected.
     */
    public static final int MAX_SAFE_BUFFER_BYTES = 64 * 1024;

    private NbtHook() {
    }

    /**
     * Attempts to read a compound tag from the buffer through the native kernel.
     *
     * @param buffer the buffer positioned at the tag
     *
     * @return the outcome
     */
    public static ReadOutcome tryReadNbt(ByteBuf buffer) {
        var runtime = FerrumRuntime.instance();
        if (!runtime.isAvailable()
                || !runtime.config().isModuleEnabled(ModuleId.NBT)
                || !NativeFeatures.isSupported(runtime.featureBits(), ModuleId.NBT)
        ) {
            return new ReadOutcome.NotHandled();
        }

        var readable = buffer.readableBytes();
        var settings = runtime.config().modules().get(NbtModule.MODULE_ID);
        var minBatch = Optional.ofNullable(settings)
                .map(ModuleSettings::minBatch)
                .orElse(0);
        if (readable <= 0
                || readable < minBatch
                || readable > MAX_SAFE_BUFFER_BYTES
        ) {
            return new ReadOutcome.NotHandled();
        }

        var readerIndex = buffer.readerIndex();
        var limits = NbtLimits.forBuffer(readable);

        var result = buffer.hasArray()
                ?
                NativeNbt.parseAny(
                        buffer.array(),
                        buffer.arrayOffset() + readerIndex,
                        readable,
                        limits
                )
                : parseCopied(buffer, readerIndex, readable, limits);
        if (result == null) {
            return new ReadOutcome.NotHandled();
        }

        var tag = result.tag();
        if (!(tag instanceof CompoundTag compound)) {
            return new ReadOutcome.NotHandled();
        }

        buffer.readerIndex(readerIndex + result.consumed());
        return new ReadOutcome.Handled(compound);
    }

    private static NativeNbt.@Nullable AnyResult parseCopied(
            ByteBuf buffer,
            int readerIndex,
            int readable,
            NbtLimits limits
    ) {
        var copy = new byte[readable];
        buffer.getBytes(readerIndex, copy);
        return NativeNbt.parseAny(copy, 0, readable, limits);
    }

    /**
     * Result of attempting the native read path.
     */
    public sealed interface ReadOutcome {

        /**
         * The native path produced a compound tag.
         *
         * @param tag the compound tag
         */
        record Handled(CompoundTag tag) implements ReadOutcome {

        }

        /**
         * The native path did not apply; the caller must run the vanilla path.
         */
        record NotHandled() implements ReadOutcome {

        }

    }

}
