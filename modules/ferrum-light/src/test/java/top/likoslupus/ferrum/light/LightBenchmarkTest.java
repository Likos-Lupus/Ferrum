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

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static java.util.Objects.requireNonNull;

/**
 * Exploratory block-light batch benchmark. WSL2 numbers are not an enablement gate; the controlled
 * environment measures the end-to-end scenario before the {@code LIGHT} feature bit is decided.
 */
@Tag("benchmark")
class LightBenchmarkTest {

    private static final int WARMUP = 20;
    private static final int ITERATIONS = 50;

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
    void blockLightKernel() {
        var states = scene();
        IntStream.range(0, WARMUP)
                .forEach(_ -> {
                    assertVanilla(states);
                    runNative(states);
                });

        var vanillaNs = Long.MAX_VALUE;
        var snapshotNs = Long.MAX_VALUE;
        var nativeNs = Long.MAX_VALUE;
        for (var index = 0; index < ITERATIONS; index++) {
            var vanillaEngine = createEngine(states);
            var start = System.nanoTime();
            vanillaEngine.runLightUpdates();
            vanillaNs = Math.min(vanillaNs, System.nanoTime() - start);

            var nativeEngine = createEngine(states);
            var accessor = new LightEngineTestAccess(nativeEngine);
            var storage = new StorageTestAccess(accessor.ferrum$storage());
            var snapshotStart = System.nanoTime();
            var input = requireNonNull(
                    LightBatch.build(
                            accessor,
                            storage,
                            accessor.ferrum$chunkSource(),
                            512
                    )
            );
            snapshotNs = Math.min(snapshotNs, System.nanoTime() - snapshotStart);
            var runStart = System.nanoTime();
            var output = requireNonNull(NativeLight.run(input));
            nativeNs = Math.min(nativeNs, System.nanoTime() - runStart);
            LightBatch.apply(nativeEngine, accessor, storage, output);
        }

        System.out.printf(
                "light-kernel,vanillaNs=%d,snapshotNs=%d,nativeNs=%d,ratio=%.3f%n",
                vanillaNs,
                snapshotNs,
                nativeNs,
                (double) vanillaNs / nativeNs
        );
    }

    private static Map<Long, BlockState> scene() {
        var states = new HashMap<Long, BlockState>();
        for (var x = 0; x < 16; x++) {
            for (var z = 0; z < 16; z++) {
                states.put(BlockPos.asLong(x, 0, z), Blocks.STONE.defaultBlockState());
            }
        }
        for (var x = 2; x < 16; x += 4) {
            for (var z = 2; z < 16; z += 4) {
                states.put(BlockPos.asLong(x, 1, z), Blocks.GLOWSTONE.defaultBlockState());
            }
        }
        for (var y = 1; y <= 4; y++) {
            for (var x = 0; x < 16; x++) {
                states.put(BlockPos.asLong(x, y, 8), Blocks.STONE.defaultBlockState());
            }
        }
        return states;
    }

    private static void assertVanilla(Map<Long, BlockState> states) {
        createEngine(states).runLightUpdates();
    }

    private static void runNative(Map<Long, BlockState> states) {
        var engine = createEngine(states);
        var accessor = new LightEngineTestAccess(engine);
        var storage = new StorageTestAccess(accessor.ferrum$storage());
        var input = requireNonNull(
                LightBatch.build(
                        accessor,
                        storage,
                        accessor.ferrum$chunkSource(),
                        512
                )
        );
        var output = requireNonNull(NativeLight.run(input));
        LightBatch.apply(engine, accessor, storage, output);
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

}
