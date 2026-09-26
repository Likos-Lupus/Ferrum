package top.likoslupus.ferrum.collide;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Serialization for the collide batch blobs (ADR-0019).
 *
 * <p>Little-endian, magic-versioned, self-describing. The layout matches the Rust reader exactly.
 */
final class CollideBlob {

    /** The maximum serialized shape size along any axis. */
    static final int MAX_DIM = 64;
    /** The maximum serialized cell count for one shape. */
    static final long MAX_CELLS = (long) MAX_DIM * MAX_DIM * MAX_DIM;

    private static final byte[] MAGIC_CLIP_IN = ascii("FBCA");
    private static final byte[] MAGIC_CLIP_OUT = ascii("FBCO");
    private static final byte[] MAGIC_SWEEP_IN = ascii("FBCS");
    private static final byte[] MAGIC_SWEEP_OUT = ascii("FBCT");
    private static final byte VERSION = 1;

    private CollideBlob() {
    }

    /**
     * Builds an `FBCA` clip input blob from world-space boxes.
     *
     * @param worldBoxes the boxes already offset by their block position
     * @param from       the ray origin
     * @param to         the ray target
     *
     * @return the blob
     */
    static byte[] clipInput(
            List<? extends AABB> worldBoxes,
            Vec3 from,
            Vec3 to
    ) {
        var buffer = ByteBuffer
                .allocate(60 + worldBoxes.size() * 48)
                .order(ByteOrder.LITTLE_ENDIAN);
        putHeader(buffer, MAGIC_CLIP_IN);
        putVec3(buffer, from);
        putVec3(buffer, to);
        buffer.putInt(worldBoxes.size());
        worldBoxes.forEach(box -> {
            buffer.putDouble(box.minX);
            buffer.putDouble(box.minY);
            buffer.putDouble(box.minZ);
            buffer.putDouble(box.maxX);
            buffer.putDouble(box.maxY);
            buffer.putDouble(box.maxZ);
        });
        return buffer.array();
    }

    private static void putHeader(ByteBuffer buffer, byte[] magic) {
        buffer.put(magic);
        buffer.put(VERSION);
        buffer.put((byte) 0);
        buffer.putShort((short) 0);
    }

    private static void putVec3(ByteBuffer buffer, Vec3 value) {
        buffer.putDouble(value.x);
        buffer.putDouble(value.y);
        buffer.putDouble(value.z);
    }

    /**
     * Reads an `FBCO` clip output blob.
     *
     * @param output the blob
     *
     * @return the decoded outcome
     */
    static ClipOutcome readClip(byte[] output) {
        var buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        checkHeader(buffer, MAGIC_CLIP_OUT);
        var found = buffer.get() != 0;
        var direction = buffer.get() & 0xFF;
        buffer.getShort();
        var scale = buffer.getDouble();
        var boxIndex = buffer.getInt();
        return new ClipOutcome(found, direction, scale, boxIndex);
    }

    private static void checkHeader(ByteBuffer buffer, byte[] magic) {
        var actual = new byte[4];
        buffer.get(actual);
        if (!Arrays.equals(actual, magic)
                || buffer.get() != VERSION
                || buffer.get() != 0
                || buffer.getShort() != 0
        ) {
            throw new IllegalArgumentException("unexpected collide blob header");
        }
    }

    /**
     * Builds an `FBCS` sweep input blob.
     *
     * @param movement  the desired movement
     * @param box       the moving box
     * @param axisOrder the axis processing order (values 0..2)
     * @param shapes    the serialized shape descriptors
     *
     * @return the blob
     */
    static byte[] sweepInput(
            Vec3 movement,
            AABB box,
            byte[] axisOrder,
            List<byte[]> shapes
    ) {
        var size = 88;
        size += shapes.stream().mapToInt(shape -> shape.length).sum();
        var buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        putHeader(buffer, MAGIC_SWEEP_IN);
        putBox(buffer, box);
        putVec3(buffer, movement);
        buffer.put(axisOrder);
        buffer.put((byte) 0);
        buffer.putInt(shapes.size());
        shapes.forEach(buffer::put);
        return buffer.array();
    }

    private static void putBox(ByteBuffer buffer, AABB box) {
        buffer.putDouble(box.minX);
        buffer.putDouble(box.minY);
        buffer.putDouble(box.minZ);
        buffer.putDouble(box.maxX);
        buffer.putDouble(box.maxY);
        buffer.putDouble(box.maxZ);
    }

    /**
     * Reads an `FBCT` sweep output blob.
     *
     * @param output the blob
     *
     * @return the resolved movement
     */
    static double[] readSweep(byte[] output) {
        var buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        checkHeader(buffer, MAGIC_SWEEP_OUT);
        return new double[]{
                buffer.getDouble(),
                buffer.getDouble(),
                buffer.getDouble()
        };
    }

    /**
     * Serializes one voxel shape, or returns {@code null} when it exceeds the kernel limits.
     *
     * @param voxel the discrete occupancy
     * @param xs    the x coordinates
     * @param ys    the y coordinates
     * @param zs    the z coordinates
     *
     * @return the descriptor bytes, or {@code null} when not serializable
     */
    static byte @Nullable [] serializeShape(
            DiscreteVoxelShape voxel,
            DoubleList xs,
            DoubleList ys,
            DoubleList zs
    ) {
        var sx = voxel.getXSize();
        var sy = voxel.getYSize();
        var sz = voxel.getZSize();
        if (sx > MAX_DIM || sy > MAX_DIM || sz > MAX_DIM) {
            return null;
        }

        var cells = (long) sx * sy * sz;
        if (cells > MAX_CELLS) {
            return null;
        }

        var bitBytes = (int) ((cells + 7) / 8);
        var size = 8 + (sx + 1) * 8 + (sy + 1) * 8 + (sz + 1) * 8 + bitBytes;
        var buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) sx);
        buffer.putShort((short) sy);
        buffer.putShort((short) sz);
        buffer.putShort((short) 0);
        putCoords(buffer, xs, sx);
        putCoords(buffer, ys, sy);
        putCoords(buffer, zs, sz);

        var packed = new byte[bitBytes];
        for (var z = 0; z < sz; z++) {
            for (var y = 0; y < sy; y++) {
                for (var x = 0; x < sx; x++) {
                    if (voxel.isFull(x, y, z)) {
                        var index = x + sx * (y + sy * z);
                        packed[index >> 3] |= (byte) (1 << (index & 7));
                    }
                }
            }
        }
        buffer.put(packed);
        return buffer.array();
    }

    private static void putCoords(ByteBuffer buffer, DoubleList coords, int size) {
        IntStream.rangeClosed(0, size)
                .mapToDouble(coords::getDouble)
                .forEach(buffer::putDouble);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * The decoded `FBCO` clip result.
     *
     * @param found     whether a hit was found
     * @param direction the vanilla direction ordinal
     * @param scale     the surviving ray scale
     * @param boxIndex  the box that produced the hit
     */
    record ClipOutcome(
            boolean found,
            int direction,
            double scale,
            int boxIndex
    ) {

    }

}
