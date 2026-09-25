package top.likoslupus.ferrum.light.mixin;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import org.jspecify.annotations.Nullable;

/**
 * Read/write access to the section storage for the block-light batch commit.
 */
@Mixin(LayerLightSectionStorage.class)
public interface LayerLightSectionStorageAccessor {

    @Invoker("storingLightForSection")
    boolean ferrum$storingLightForSection(long sectionNode);

    @Invoker("getDataLayer")
    @Nullable DataLayer ferrum$getDataLayer(long sectionNode, boolean updating);

    @Invoker("getDataLayerToWrite")
    @Nullable DataLayer ferrum$getDataLayerToWrite(long sectionNode);

    @Invoker("lightOnInSection")
    boolean ferrum$lightOnInSection(long sectionNode);

    @Invoker("hasInconsistencies")
    boolean ferrum$hasInconsistencies();

    @Invoker("markNewInconsistencies")
    void ferrum$markNewInconsistencies(LightEngine<?, ?> engine);

    @Invoker("swapSectionMap")
    void ferrum$swapSectionMap();

    @Accessor("changedSections")
    LongSet ferrum$changedSections();

    @Accessor("sectionsAffectedByLightUpdates")
    LongSet ferrum$sectionsAffectedByLightUpdates();

}
