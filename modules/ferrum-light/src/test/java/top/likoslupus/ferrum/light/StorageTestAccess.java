package top.likoslupus.ferrum.light;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import top.likoslupus.ferrum.light.mixin.LayerLightSectionStorageAccessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

/**
 * Test-only {@link LayerLightSectionStorageAccessor} backed by reflection.
 */
final class StorageTestAccess implements LayerLightSectionStorageAccessor {

    private final LayerLightSectionStorage<?> storage;

    StorageTestAccess(LayerLightSectionStorage<?> storage) {
        this.storage = storage;
    }

    @Override
    public boolean ferrum$storingLightForSection(long sectionNode) {
        return (Boolean) invoke(
                "storingLightForSection",
                new Class<?>[]{long.class},
                sectionNode
        );
    }

    @Override
    public @Nullable DataLayer ferrum$getDataLayer(long sectionNode, boolean updating) {
        return (DataLayer) invoke(
                "getDataLayer",
                new Class<?>[]{long.class, boolean.class},
                sectionNode,
                updating
        );
    }

    @Override
    public @Nullable DataLayer ferrum$getDataLayerToWrite(long sectionNode) {
        return (DataLayer) invoke(
                "getDataLayerToWrite",
                new Class<?>[]{long.class},
                sectionNode
        );
    }

    @Override
    public boolean ferrum$lightOnInSection(long sectionNode) {
        return (Boolean) invoke(
                "lightOnInSection",
                new Class<?>[]{long.class},
                sectionNode
        );
    }

    @Override
    public boolean ferrum$hasInconsistencies() {
        return (Boolean) invoke("hasInconsistencies", new Class<?>[0]);
    }

    @Override
    public void ferrum$markNewInconsistencies(LightEngine<?, ?> engine) {
        invoke("markNewInconsistencies", new Class<?>[]{LightEngine.class}, engine);
    }

    @Override
    public void ferrum$swapSectionMap() {
        invoke("swapSectionMap", new Class<?>[0]);
    }

    @Override
    public LongSet ferrum$changedSections() {
        return (LongSet) field("changedSections");
    }

    @Override
    public LongSet ferrum$sectionsAffectedByLightUpdates() {
        return (LongSet) field("sectionsAffectedByLightUpdates");
    }

    private Object field(String name) {
        Class<?> type = storage.getClass();
        while (type != null) {
            try {
                var declared = type.getDeclaredField(name);
                declared.setAccessible(true);
                return declared.get(storage);
            } catch (NoSuchFieldException exception) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("cannot read field " + name, exception);
            }
        }
        throw new IllegalStateException("field not found: " + name);
    }

    private Object invoke(String name, Class<?>[] types, Object... arguments) {
        Class<?> type = storage.getClass();
        while (type != null) {
            try {
                var method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(storage, arguments);
            } catch (NoSuchMethodException exception) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("cannot invoke " + name, exception);
            }
        }
        throw new IllegalStateException("method not found: " + name);
    }

}
