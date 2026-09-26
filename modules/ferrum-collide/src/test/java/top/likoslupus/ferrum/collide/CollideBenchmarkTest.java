package top.likoslupus.ferrum.collide;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exploratory collide kernel benchmark. WSL2 numbers are not an enablement gate; the controlled
 * environment measures the end-to-end scenario before the {@code COLLIDE} feature bit is decided.
 */
@Tag("benchmark")
class CollideBenchmarkTest {

    private static final int WARMUP = 50;
    private static final int ITERATIONS = 200;

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
        System.setProperty("ferrum.test.collide.force", "true");
    }

    @AfterAll
    static void resetRuntime() {
        System.clearProperty("ferrum.test.collide.force");
        FerrumRuntime.instance().reset();
    }

    @Test
    void clipKernel() {
        var boxes = IntStream.range(0, 256)
                .mapToObj(index -> new AABB(
                        index,
                        0,
                        0,
                        index + 0.75,
                        1,
                        1
                ))
                .collect(Collectors.toCollection(ArrayList::new));
        var from = new Vec3(-1.0, 0.5, 0.5);
        var to = new Vec3(400.0, 0.5, 0.5);
        var world = boxes.stream()
                .map(box -> box.move(BlockPos.ZERO))
                .collect(Collectors.toCollection(() -> new ArrayList<>(boxes.size())));

        IntStream.range(0, WARMUP)
                .forEach(_ -> {
                    CollideReference.clip(boxes, from, to, BlockPos.ZERO);
                    NativeCollide.clip(CollideBlob.clipInput(world, from, to));
                });

        var vanillaStart = System.nanoTime();
        IntStream.range(0, ITERATIONS)
                .forEach(_ ->
                        CollideReference.clip(boxes, from, to, BlockPos.ZERO)
                );
        var vanillaNs = (System.nanoTime() - vanillaStart) / ITERATIONS;

        var nativeStart = System.nanoTime();
        IntStream.range(0, ITERATIONS)
                .mapToObj(_ -> CollideBlob.clipInput(world, from, to))
                .forEach(NativeCollide::clip);
        var nativeNs = (System.nanoTime() - nativeStart) / ITERATIONS;

        System.out.printf(
                "collide clip boxes=%d vanillaNs=%d nativeNs=%d ratio=%.3f%n",
                boxes.size(),
                vanillaNs,
                nativeNs,
                (double) vanillaNs / nativeNs
        );
    }

    @Test
    void sweepKernel() {
        var shapes = IntStream.range(0, 256)
                .mapToObj(index -> CollideReference.union(List.of(
                        new AABB(
                                index,
                                0,
                                0,
                                index + 0.75,
                                1,
                                1
                        )
                )))
                .collect(Collectors.toCollection(ArrayList::new));
        var box = new AABB(-1.5, 0.0, -0.5, -0.5, 1.0, 0.5);
        var movement = new Vec3(300.0, 0.0, 0.0);

        IntStream.range(0, WARMUP)
                .forEach(_ -> {
                    CollideReference.sweep(movement, box, shapes);
                    nativeSweep(movement, box, shapes);
                });

        var vanillaStart = System.nanoTime();
        IntStream.range(0, ITERATIONS)
                .forEach(_ ->
                        CollideReference.sweep(movement, box, shapes)
                );
        var vanillaNs = (System.nanoTime() - vanillaStart) / ITERATIONS;

        var nativeStart = System.nanoTime();
        IntStream.range(0, ITERATIONS)
                .forEach(_ ->
                        nativeSweep(movement, box, shapes)
                );
        var nativeNs = (System.nanoTime() - nativeStart) / ITERATIONS;

        System.out.printf(
                "collide sweep shapes=%d vanillaNs=%d nativeNs=%d ratio=%.3f%n",
                shapes.size(),
                vanillaNs,
                nativeNs,
                (double) vanillaNs / nativeNs
        );
    }

    private static void nativeSweep(Vec3 movement, AABB box, List<VoxelShape> shapes) {
        var serialized = shapes.stream()
                .map(TestShapeExtractor::describe)
                .collect(Collectors.toCollection(() -> new ArrayList<>(shapes.size())));
        var axisOrder = new byte[]{1, 0, 2};
        NativeCollide.sweep(CollideBlob.sweepInput(movement, box, axisOrder, serialized));
    }

}
