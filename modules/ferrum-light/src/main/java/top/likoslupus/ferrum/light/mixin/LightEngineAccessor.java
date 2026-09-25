package top.likoslupus.ferrum.light.mixin;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Field access to the light engine's pending work for the block-light batch.
 */
@Mixin(LightEngine.class)
public interface LightEngineAccessor {

    @Accessor("blockNodesToCheck")
    LongOpenHashSet ferrum$blockNodesToCheck();

    @Accessor("decreaseQueue")
    LongArrayFIFOQueue ferrum$decreaseQueue();

    @Accessor("increaseQueue")
    LongArrayFIFOQueue ferrum$increaseQueue();

    @Accessor("chunkSource")
    LightChunkGetter ferrum$chunkSource();

    @Accessor("storage")
    LayerLightSectionStorage<?> ferrum$storage();

}
