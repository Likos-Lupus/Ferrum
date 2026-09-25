package top.likoslupus.ferrum.light;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import top.likoslupus.ferrum.light.mixin.LightEngineAccessor;

import java.lang.reflect.Field;

/**
 * Test-only {@link LightEngineAccessor} backed by reflection.
 *
 * <p>Mixin accessors are only active inside the game runtime, so JUnit tests read the engine's
 * private fields directly. The production hook uses the same interface methods.
 */
final class LightEngineTestAccess implements LightEngineAccessor {

    private final LightEngine<?, ?> engine;

    LightEngineTestAccess(LightEngine<?, ?> engine) {
        this.engine = engine;
    }

    @Override
    public LongOpenHashSet ferrum$blockNodesToCheck() {
        return (LongOpenHashSet) field("blockNodesToCheck");
    }

    @Override
    public LongArrayFIFOQueue ferrum$decreaseQueue() {
        return (LongArrayFIFOQueue) field("decreaseQueue");
    }

    @Override
    public LongArrayFIFOQueue ferrum$increaseQueue() {
        return (LongArrayFIFOQueue) field("increaseQueue");
    }

    @Override
    public LightChunkGetter ferrum$chunkSource() {
        return (LightChunkGetter) field("chunkSource");
    }

    @Override
    public LayerLightSectionStorage<?> ferrum$storage() {
        return (LayerLightSectionStorage<?>) field("storage");
    }

    private Object field(String name) {
        Class<?> type = engine.getClass();
        while (type != null) {
            try {
                var declared = type.getDeclaredField(name);
                declared.setAccessible(true);
                return declared.get(engine);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("cannot read field " + name, exception);
            }
        }
        throw new IllegalStateException("field not found: " + name);
    }

}
