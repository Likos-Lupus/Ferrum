package top.likoslupus.ferrum.light;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A minimal in-memory {@link LightChunkGetter} for the light differential tests.
 *
 * <p>The {@link LightChunk} is a dynamic proxy so the test does not depend on the loader-specific
 * {@code BlockGetter} method set; the block-light engine only reads {@code getBlockState} and the
 * section geometry.
 */
final class FakeLightChunkGetter implements LightChunkGetter {

    private final LightChunk chunk;

    FakeLightChunkGetter(Map<Long, BlockState> states) {
        this.chunk = proxyChunk(states);
    }

    @SuppressWarnings("NullAway")
    private static LightChunk proxyChunk(Map<Long, BlockState> states) {
        var handler = (InvocationHandler) (_, method, arguments) -> switch (method.getName()) {
            case "getBlockState" -> states.getOrDefault(
                    ((BlockPos) arguments[0]).asLong(),
                    Blocks.AIR.defaultBlockState()
            );
            case "getFluidState" -> Blocks.AIR.defaultBlockState().getFluidState();
            case "getBlockEntity",
                 "getSkyLightSources",
                 "findBlockLightSources" -> null;
            case "getMinBuildHeight",
                 "getMinY" -> -64;
            case "getMinSectionY" -> -4;
            case "getSectionsCount" -> 24;
            case "getHeight" -> 384;
            case "getMaxBuildHeight" -> 320;
            default -> defaultValue(method.getReturnType());
        };
        return (LightChunk) Proxy.newProxyInstance(
                FakeLightChunkGetter.class.getClassLoader(),
                new Class<?>[]{LightChunk.class},
                handler
        );
    }

    private static @Nullable Object defaultValue(Class<?> type) {
        return type.isPrimitive()
                ?
                switch (type) {
                    case Class<?> c when c == boolean.class -> false;
                    case Class<?> c when c == long.class -> 0L;
                    case Class<?> c when c == double.class -> 0.0;
                    case Class<?> c when c == float.class -> 0.0f;
                    case Class<?> c when c == char.class -> (char) 0;
                    default -> 0;
                }
                : null;
    }

    @Override
    public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
        return chunk;
    }

    @Override
    public BlockGetter getLevel() {
        return EmptyBlockGetter.INSTANCE;
    }

}
