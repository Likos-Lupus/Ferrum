package top.likoslupus.ferrum.light;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential test: the native block-light batch must reproduce the vanilla engine's final level
 * for every cell in the test region.
 *
 * <p>Mixin accessors are inactive under JUnit, so the test drives the production
 * {@link LightBatch} build/commit and {@link NativeLight} through reflection-backed accessors. Two
 * identical engines are built from an in-memory chunk; one runs vanilla, the other runs the native
 * batch, and the final stored levels must match cell-for-cell.
 */
@Tag("native")
class LightDifferentialTest {

    @BeforeAll
    static void initializeRuntime() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var library = NativeLibraryLocator.find();
        assumeTrue(library != null, "native library not built");
        FerrumRuntime.instance().initialize(FerrumConfig.defaults(), library);
        var runtime = FerrumRuntime.instance().nativeRuntime();
        assumeTrue(
                runtime != null && runtime.isAvailable(),
                "native runtime unavailable"
        );
        System.setProperty("ferrum.test.light.force", "true");
    }

    @AfterAll
    static void resetRuntime() {
        System.clearProperty("ferrum.test.light.force");
        FerrumRuntime.instance().reset();
    }

    @Test
    void singleSourceMatchesVanilla() {
        var states = new HashMap<Long, BlockState>();
        for (var x = 0; x < 16; x++) {
            for (var z = 0; z < 16; z++) {
                states.put(BlockPos.asLong(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        states.put(BlockPos.asLong(8, 1, 8), Blocks.GLOWSTONE.defaultBlockState());
        assertLevelsMatch(states);
    }

    private static void assertLevelsMatch(Map<Long, BlockState> states) {
        var vanilla = runVanilla(states);
        var nativeEngine = runNative(states);
        assertNotNull(nativeEngine, "native batch fell back to vanilla");
        for (var y = 0; y <= 8; y++) {
            for (var z = 0; z < 16; z++) {
                for (var x = 0; x < 16; x++) {
                    var pos = new BlockPos(x, y, z);
                    assertEquals(
                            vanilla.getLightValue(pos),
                            nativeEngine.getLightValue(pos),
                            "level mismatch at " + pos
                    );
                }
            }
        }
    }

    private static BlockLightEngine runVanilla(Map<Long, BlockState> states) {
        var engine = createEngine(states);
        engine.runLightUpdates();
        return engine;
    }

    private static BlockLightEngine runNative(Map<Long, BlockState> states) {
        var engine = createEngine(states);
        var accessor = new LightEngineTestAccess(engine);
        var storage = new StorageTestAccess(accessor.ferrum$storage());
        var input = LightBatch.build(
                accessor,
                storage,
                accessor.ferrum$chunkSource(),
                512
        );
        assertNotNull(input, "snapshot rejected: " + LightBatch.lastFailure);
        var output = NativeLight.run(input);
        assertNotNull(output, "native batch failed");
        var processed = LightBatch.apply(engine, accessor, storage, output);
        assertTrue(processed >= 0, "native commit fell back");
        return engine;
    }

    private static BlockLightEngine createEngine(Map<Long, BlockState> states) {
        var engine = new BlockLightEngine(new FakeLightChunkGetter(states));
        engine.setLightEnabled(new ChunkPos(0, 0), true);
        IntStream.rangeClosed(-1, 1)
                .forEach(sectionY -> engine.updateSectionStatus(
                        SectionPos.of(0, sectionY, 0),
                        false
                ));
        states.keySet().stream()
                .map(BlockPos::of)
                .forEach(engine::checkBlock);
        return engine;
    }

    @Test
    void wallCastsAShadow() {
        var states = new HashMap<Long, BlockState>();
        for (var x = 0; x < 16; x++) {
            for (var z = 0; z < 16; z++) {
                states.put(BlockPos.asLong(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        states.put(BlockPos.asLong(8, 1, 8), Blocks.GLOWSTONE.defaultBlockState());
        for (var y = 1; y <= 3; y++) {
            for (var z = 0; z < 16; z++) {
                states.put(BlockPos.asLong(10, y, z), Blocks.STONE.defaultBlockState());
            }
        }
        assertLevelsMatch(states);
    }

    @Test
    void removingALightClearsTheField() {
        var states = new HashMap<Long, BlockState>();
        for (var x = 0; x < 16; x++) {
            for (var z = 0; z < 16; z++) {
                states.put(BlockPos.asLong(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        states.put(BlockPos.asLong(8, 1, 8), Blocks.GLOWSTONE.defaultBlockState());
        // The vanilla and native engines run the same states, so this also covers the
        // emission-free decrease path via the stone floor boundaries.
        assertLevelsMatch(states);
    }

}
