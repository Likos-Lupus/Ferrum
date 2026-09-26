package top.likoslupus.ferrum.collide;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.data.DataFormats;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Generates the collide golden corpus from the vanilla reference engines.
 *
 * <p>Each case writes an input blob (`.in`) and the observable vanilla result (`.expected`): found
 * /direction / hit location for the clip, or the resolved movement for the sweep. The Rust tests
 * replay the input and compare against the expected bytes, which makes the corpus a cross-arch
 * regression anchor. Disabled by default; the {@code generateCollideGolden} task enables it.
 */
class CollideGoldenGeneratorTest {

    @Test
    void generateCorpus() throws IOException {
        assumeTrue(
                Boolean.getBoolean("ferrum.generateCollideGolden"),
                "collide golden generation is disabled"
        );
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var directory = Path.of(System.getProperty("ferrum.collide.golden.dir"));
        Files.createDirectories(directory);

        var manifest = new ArrayList<Entry>();
        for (var clipCase : clipCases()) {
            var input = CollideBlob.clipInput(
                    clipCase.boxes(),
                    clipCase.from(),
                    clipCase.to()
            );
            var expected = clipExpected(
                    CollideReference.clip(
                            clipCase.boxes(),
                            clipCase.from(),
                            clipCase.to(),
                            BlockPos.ZERO
                    )
            );
            write(directory, clipCase.name(), input, expected, "clip", manifest);
        }
        for (var sweepCase : sweepCases()) {
            var serialized = sweepCase.shapes().stream()
                    .map(TestShapeExtractor::describe)
                    .collect(Collectors.toCollection(
                            () -> new ArrayList<>(sweepCase.shapes().size())
                    ));
            var axisOrder = Math.abs(sweepCase.movement().x) < Math.abs(sweepCase.movement().z)
                    ? new byte[]{1, 2, 0}
                    : new byte[]{1, 0, 2};
            var input = CollideBlob.sweepInput(
                    sweepCase.movement(),
                    sweepCase.box(),
                    axisOrder,
                    serialized
            );
            var expected = sweepExpected(CollideReference.sweep(
                    sweepCase.movement(),
                    sweepCase.box(),
                    sweepCase.shapes()
            ));
            write(directory, sweepCase.name(), input, expected, "sweep", manifest);
        }

        Files.writeString(
                directory.resolve("manifest.json"),
                DataFormats.json().writeValueAsString(manifest)
        );
    }

    private static List<ClipCase> clipCases() {
        var unit = new AABB(0, 0, 0, 1, 1, 1);
        var second = new AABB(2, 0, 0, 3, 1, 1);
        var thin = new AABB(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);
        return List.of(
                new ClipCase(
                        "clip-forward", List.of(unit, second),
                        new Vec3(-1, 0.5, 0.5), new Vec3(4, 0.5, 0.5)
                ),
                new ClipCase(
                        "clip-backward", List.of(unit, second),
                        new Vec3(4, 0.5, 0.5), new Vec3(-1, 0.5, 0.5)
                ),
                new ClipCase(
                        "clip-parallel-miss", List.of(unit),
                        new Vec3(0.5, 2.0, 0.5), new Vec3(0.5, 2.0, 4.0)
                ),
                new ClipCase(
                        "clip-origin-inside", List.of(unit),
                        new Vec3(0.5, 0.5, 0.5), new Vec3(0.5, 0.5, 2.0)
                ),
                new ClipCase(
                        "clip-zero-delta", List.of(unit),
                        new Vec3(0.5, 0.5, -1.0), new Vec3(0.5, 0.5, -1.0)
                ),
                new ClipCase(
                        "clip-tiny-delta", List.of(unit),
                        new Vec3(0.5, 0.5, -1.0), new Vec3(0.5, 0.5, -1.0 + 1.0e-9)
                ),
                new ClipCase(
                        "clip-nested", List.of(thin, unit),
                        new Vec3(-1, 0.5, 0.5), new Vec3(2, 0.5, 0.5)
                ),
                new ClipCase(
                        "clip-diagonal", List.of(unit),
                        new Vec3(-1, -1, -1), new Vec3(2, 2, 2)
                ),
                new ClipCase(
                        "clip-tie-earlier-wins", List.of(unit, unit),
                        new Vec3(-1, 0.5, 0.5), new Vec3(2, 0.5, 0.5)
                )
        );
    }

    @SuppressWarnings("EnumOrdinal")
    private static byte[] clipExpected(@Nullable BlockHitResult hit) {
        var buffer = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        putHeader(buffer, "FBCR");
        if (hit == null) {
            buffer.put((byte) 0);
            buffer.put((byte) 0xFF);
            buffer.putShort((short) 0);
            buffer.putDouble(0.0);
            buffer.putDouble(0.0);
            buffer.putDouble(0.0);
        } else {
            var location = hit.getLocation();
            buffer.put((byte) 1);
            buffer.put((byte) hit.getDirection().ordinal());
            buffer.putShort((short) 0);
            buffer.putDouble(location.x);
            buffer.putDouble(location.y);
            buffer.putDouble(location.z);
        }
        return buffer.array();
    }

    private static void write(
            Path directory,
            String name,
            byte[] input,
            byte[] expected,
            String kind,
            List<Entry> manifest
    ) throws IOException {
        Files.write(directory.resolve(name + ".in"), input);
        Files.write(directory.resolve(name + ".expected"), expected);
        manifest.add(new Entry(name, kind, sha256(input), sha256(expected)));
    }

    private static List<SweepCase> sweepCases() {
        var cube = CollideReference.union(List.of(
                new AABB(0, 0, 0, 1, 1, 1)
        ));
        var step = CollideReference.union(List.of(
                new AABB(0, 0, 0, 1, 1, 1),
                new AABB(2, 0, 0, 3, 1, 1)
        ));
        var fence = CollideReference.union(List.of(
                new AABB(0, 0, 0.375, 1, 1.5, 0.625),
                new AABB(0.375, 0, 0, 0.625, 1.5, 1)
        ));
        return List.of(
                new SweepCase(
                        "sweep-wall",
                        new Vec3(2.0, 0.0, 0.0),
                        new AABB(-1.5, 0.0, -0.5, -0.5, 1.0, 0.5),
                        List.of(cube)
                ),
                new SweepCase(
                        "sweep-fall",
                        new Vec3(0.0, -2.0, 0.0),
                        new AABB(-0.5, 2.5, -0.5, 0.5, 3.5, 0.5),
                        List.of(cube)
                ),
                new SweepCase(
                        "sweep-step",
                        new Vec3(2.0, 0.0, 0.0),
                        new AABB(-1.5, 0.0, -0.5, -0.5, 1.0, 0.5),
                        List.of(step)
                ),
                new SweepCase(
                        "sweep-negative-x",
                        new Vec3(-2.0, 0.0, 0.0),
                        new AABB(3.5, 0.0, -0.5, 4.5, 1.0, 0.5),
                        List.of(cube)
                ),
                new SweepCase(
                        "sweep-diagonal",
                        new Vec3(1.5, -1.0, 2.5),
                        new AABB(-1.0, 1.5, -1.0, 0.0, 2.5, 0.0),
                        List.of(step)
                ),
                new SweepCase(
                        "sweep-fence",
                        new Vec3(2.0, 0.0, 3.0),
                        new AABB(-1.5, 0.0, -1.5, -0.5, 1.8, -0.5),
                        List.of(fence)
                ),
                new SweepCase(
                        "sweep-no-shapes",
                        new Vec3(1.0, 2.0, 3.0),
                        new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                        List.of()
                ),
                new SweepCase(
                        "sweep-zero-movement",
                        new Vec3(0.0, 0.0, 0.0),
                        new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                        List.of(cube)
                ),
                new SweepCase(
                        "sweep-tiny-movement",
                        new Vec3(1.0e-9, 0.0, 0.0),
                        new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
                        List.of(cube)
                )
        );
    }

    private static byte[] sweepExpected(Vec3 resolved) {
        var buffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        putHeader(buffer, "FBCE");
        buffer.putDouble(resolved.x);
        buffer.putDouble(resolved.y);
        buffer.putDouble(resolved.z);
        return buffer.array();
    }

    private static void putHeader(ByteBuffer buffer, String magic) {
        buffer.put(magic.getBytes(StandardCharsets.US_ASCII));
        buffer.put((byte) 1);
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ClipCase(
            String name,
            List<AABB> boxes,
            Vec3 from,
            Vec3 to
    ) {

    }

    private record SweepCase(
            String name,
            Vec3 movement,
            AABB box,
            List<VoxelShape> shapes
    ) {

    }

    private record Entry(
            String name,
            String kind,
            String inputSha256,
            String expectedSha256
    ) {

    }

}
