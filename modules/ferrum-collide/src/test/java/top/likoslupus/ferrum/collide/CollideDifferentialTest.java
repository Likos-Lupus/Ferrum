package top.likoslupus.ferrum.collide;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Differential test: both collide kernels must reproduce the vanilla engines bit-for-bit.
 *
 * <p>The clip kernel is compared against {@code AABB.clip(Iterable, from, to, pos)} and the sweep
 * kernel against the {@code Entity.collideWithShapes} reference (built from public
 * {@code Shapes.collide} and the axis-step order). The native entry points are called directly.
 */
@Tag("native")
class CollideDifferentialTest {

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
    void clipEdgeCases() {
        var unit = new AABB(0, 0, 0, 1, 1, 1);
        var second = new AABB(2, 0, 0, 3, 1, 1);
        assertClipMatches(
                List.of(unit, second),
                BlockPos.ZERO,
                new Vec3(-1, 0.5, 0.5),
                new Vec3(4, 0.5, 0.5)
        );
        assertClipMatches(
                List.of(unit),
                BlockPos.ZERO,
                new Vec3(0.5, 2.0, 0.5),
                new Vec3(0.5, 2.0, 4.0)
        );
        assertClipMatches(
                List.of(unit, second),
                BlockPos.ZERO,
                new Vec3(4, 0.5, 0.5),
                new Vec3(-1, 0.5, 0.5)
        );
        assertClipMatches(
                List.of(unit),
                BlockPos.ZERO,
                new Vec3(0.5, 0.5, 0.5),
                new Vec3(0.5, 0.5, 2.0)
        );
        assertClipMatches(
                List.of(unit),
                BlockPos.ZERO,
                new Vec3(0.5, 0.5, -1.0),
                new Vec3(0.5, 0.5, -1.0)
        );
        assertClipMatches(
                List.of(unit),
                BlockPos.ZERO,
                new Vec3(0.5, 0.5, -1.0),
                new Vec3(0.5, 0.5, -1.0 + 1.0e-9)
        );
        assertClipMatches(
                List.of(unit),
                new BlockPos(10, 0, 0),
                new Vec3(9, 0.5, 0.5),
                new Vec3(14, 0.5, 0.5)
        );
        assertClipMatches(
                List.of(
                        new AABB(0, 0, 0, 1, 1, 1),
                        new AABB(0, 0, 0, 1, 1, 1)
                ),
                BlockPos.ZERO,
                new Vec3(-1, 0.5, 0.5),
                new Vec3(2, 0.5, 0.5)
        );
    }

    private static void assertClipMatches(
            List<AABB> boxes,
            BlockPos pos,
            Vec3 from,
            Vec3 to
    ) {
        var expected = CollideReference.clip(boxes, from, to, pos);
        var actual = nativeClip(boxes, pos, from, to);
        if (expected == null) {
            assertNull(actual, "native clip should miss like vanilla");
            return;
        }
        assertNotNull(actual, "native clip should hit like vanilla");
        assertEquals(expected.getDirection(), actual.getDirection());
        assertEquals(expected.getBlockPos(), actual.getBlockPos());
        assertSameBits(expected.getLocation().x, actual.getLocation().x);
        assertSameBits(expected.getLocation().y, actual.getLocation().y);
        assertSameBits(expected.getLocation().z, actual.getLocation().z);
    }

    @SuppressWarnings("EnumOrdinal")
    private static @Nullable BlockHitResult nativeClip(
            List<AABB> boxes,
            BlockPos pos,
            Vec3 from,
            Vec3 to
    ) {
        var world = boxes.stream()
                .map(box -> box.move(pos))
                .collect(Collectors.toCollection(() -> new ArrayList<>(boxes.size())));
        var output = NativeCollide.clip(CollideBlob.clipInput(world, from, to));
        assertNotNull(output, "native clip returned no output");
        var outcome = CollideBlob.readClip(output);
        if (!outcome.found()) {
            return null;
        }
        var scale = outcome.scale();
        var hit = from.add(
                (to.x - from.x) * scale,
                (to.y - from.y) * scale,
                (to.z - from.z) * scale
        );
        return new BlockHitResult(
                hit,
                Direction.values()[outcome.direction()],
                pos,
                false
        );
    }

    private static void assertSameBits(double expected, double actual) {
        assertEquals(
                Double.doubleToRawLongBits(expected),
                Double.doubleToRawLongBits(actual)
        );
    }

    @Test
    void sweepEdgeCases() {
        var cube = CollideReference.union(List.of(
                new AABB(0, 0, 0, 1, 1, 1)
        ));
        var step = CollideReference.union(List.of(
                new AABB(0, 0, 0, 1, 1, 1),
                new AABB(2, 0, 0, 3, 1, 1)
        ));
        assertSweepMatches(
                new Vec3(2.0, 0.0, 0.0),
                new AABB(-1.5, 0.0, -0.5, -0.5, 1.0, 0.5),
                List.of(cube)
        );
        assertSweepMatches(
                new Vec3(0.0, -2.0, 0.0),
                new AABB(-0.5, 2.5, -0.5, 0.5, 3.5, 0.5),
                List.of(cube)
        );
        assertSweepMatches(
                new Vec3(2.0, 0.0, 0.0),
                new AABB(-1.5, 0.0, -0.5, -0.5, 1.0, 0.5),
                List.of(step)
        );
        assertSweepMatches(
                new Vec3(-2.0, 0.0, 0.0),
                new AABB(3.5, 0.0, -0.5, 4.5, 1.0, 0.5),
                List.of(cube)
        );
        assertSweepMatches(
                new Vec3(1.5, -1.0, 2.5),
                new AABB(-1.0, 1.5, -1.0, 0.0, 2.5, 0.0),
                List.of(step)
        );
        assertSweepMatches(
                new Vec3(1.0, 2.0, 3.0),
                new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                List.of()
        );
        assertSweepMatches(
                new Vec3(0.0, 0.0, 0.0),
                new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                List.of(cube)
        );
        assertSweepMatches(
                new Vec3(1.0e-9, 0.0, 0.0),
                new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                List.of(cube)
        );
    }

    private static void assertSweepMatches(
            Vec3 movement,
            AABB box,
            List<VoxelShape> shapes
    ) {
        var expected = CollideReference.sweep(movement, box, shapes);
        var serialized = shapes.stream()
                .map(TestShapeExtractor::describe)
                .collect(Collectors.toCollection(() -> new ArrayList<>(shapes.size())));
        var axisOrder = Math.abs(movement.x) < Math.abs(movement.z)
                ? new byte[]{1, 2, 0}
                : new byte[]{1, 0, 2};
        var output = NativeCollide.sweep(
                CollideBlob.sweepInput(movement, box, axisOrder, serialized)
        );
        assertNotNull(output, "native sweep returned no output");
        var resolved = CollideBlob.readSweep(output);
        assertSameBits(expected.x, resolved[0]);
        assertSameBits(expected.y, resolved[1]);
        assertSameBits(expected.z, resolved[2]);
    }

    @Test
    void randomClipMatchesVanilla() {
        var random = new Random(0xC0111DE);
        IntStream.range(0, 500)
                .mapToObj(_ ->
                        randomBoxes(random, 1 + random.nextInt(6))
                )
                .forEach(boxes -> {
                    var pos = new BlockPos(
                            random.nextInt(5) - 2,
                            random.nextInt(5) - 2,
                            random.nextInt(5) - 2
                    );
                    var from = randomVector(random, 3.0);
                    var to = randomVector(random, 3.0);
                    assertClipMatches(boxes, pos, from, to);
                });
    }

    private static List<AABB> randomBoxes(Random random, int count) {
        var boxes = new ArrayList<AABB>(count);
        IntStream.range(0, count)
                .mapToDouble(_ ->
                        -2.0 + random.nextDouble() * 4.0
                )
                .forEach(minX -> {
                    var minY = -2.0 + random.nextDouble() * 4.0;
                    var minZ = -2.0 + random.nextDouble() * 4.0;
                    boxes.add(new AABB(
                            minX,
                            minY,
                            minZ,
                            minX + random.nextDouble() * 2.0,
                            minY + random.nextDouble() * 2.0,
                            minZ + random.nextDouble() * 2.0
                    ));
                });
        return boxes;
    }

    private static Vec3 randomVector(Random random, double scale) {
        return new Vec3(
                (random.nextDouble() * 2.0 - 1.0) * scale,
                (random.nextDouble() * 2.0 - 1.0) * scale,
                (random.nextDouble() * 2.0 - 1.0) * scale
        );
    }

    @Test
    void randomSweepMatchesVanilla() {
        var random = new Random(0x5EEEEEEDL);
        IntStream.range(0, 300)
                .mapToObj(_ ->
                        List.of(CollideReference.union(randomBoxes(
                                random,
                                1 + random.nextInt(4)
                        )))
                )
                .forEach(shapes -> {
                    var box = new AABB(
                            -2.0 + random.nextDouble() * 2.0,
                            -2.0 + random.nextDouble() * 2.0,
                            -2.0 + random.nextDouble() * 2.0,
                            -1.0 + random.nextDouble() * 2.0,
                            -1.0 + random.nextDouble() * 2.0,
                            -1.0 + random.nextDouble() * 2.0
                    );
                    var movement = randomVector(random, 2.5);
                    assertSweepMatches(movement, box, shapes);
                });
    }

}
