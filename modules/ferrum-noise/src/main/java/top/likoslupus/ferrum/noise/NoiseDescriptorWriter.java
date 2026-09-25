package top.likoslupus.ferrum.noise;

import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import top.likoslupus.ferrum.noise.mixin.ImprovedNoiseAccessor;
import top.likoslupus.ferrum.noise.mixin.NormalNoiseAccessor;
import top.likoslupus.ferrum.noise.mixin.PerlinNoiseAccessor;

import java.io.ByteArrayOutputStream;
import java.util.stream.IntStream;

/**
 * Serializes a live vanilla noise object into the native descriptor (ADR-0017).
 *
 * <p>Every scalar is copied verbatim, including the derived frequency/value factors, so the Rust
 * side never re-derives them with a potentially different `pow` implementation. The descriptor is
 * little-endian and versioned.
 */
public final class NoiseDescriptorWriter {

    private static final byte[] MAGIC = {'F', 'B', 'N', 'S'};
    private static final byte VERSION = 1;
    private static final byte KIND_IMPROVED = 1;
    private static final byte KIND_PERLIN = 2;
    private static final byte KIND_NORMAL = 3;

    private NoiseDescriptorWriter() {
    }

    /**
     * Serializes a {@code NormalNoise}.
     *
     * @param normal the noise field
     *
     * @return the descriptor bytes
     */
    public static byte[] writeNormal(NormalNoise normal) {
        var accessor = (NormalNoiseAccessor) normal;
        var out = new ByteArrayOutputStream();
        writeHeader(out, KIND_NORMAL);
        writeDouble(out, accessor.ferrum$valueFactor());
        writePerlin(out, accessor.ferrum$first());
        writePerlin(out, accessor.ferrum$second());
        return out.toByteArray();
    }

    private static void writeHeader(ByteArrayOutputStream out, byte kind) {
        out.writeBytes(MAGIC);
        out.write(VERSION);
        out.write(kind);
    }

    private static void writeDouble(ByteArrayOutputStream out, double value) {
        var bits = Double.doubleToRawLongBits(value);
        IntStream.range(0, Long.BYTES)
                .map(index -> (int) (bits >>> (8 * index)) & 0xFF)
                .forEach(out::write);
    }

    private static void writePerlin(ByteArrayOutputStream out, PerlinNoise perlin) {
        var accessor = (PerlinNoiseAccessor) perlin;
        writeInt(out, accessor.ferrum$firstOctave());
        writeDouble(out, accessor.ferrum$lowestFreqInputFactor());
        writeDouble(out, accessor.ferrum$lowestFreqValueFactor());
        var amplitudes = accessor.ferrum$amplitudes();
        writeInt(out, amplitudes.size());
        IntStream.range(0, amplitudes.size())
                .forEach(index ->
                        writeDouble(out, amplitudes.getDouble(index))
                );
        var levels = accessor.ferrum$noiseLevels();
        IntStream.range(0, amplitudes.size())
                .filter(index ->
                        amplitudes.getDouble(index) != 0.0
                )
                .forEach(index ->
                        writeImprovedBody(out, levels[index])
                );
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeImprovedBody(ByteArrayOutputStream out, ImprovedNoise improved) {
        writeDouble(out, improved.xo);
        writeDouble(out, improved.yo);
        writeDouble(out, improved.zo);
        var permutation = ((ImprovedNoiseAccessor) (Object) improved).ferrum$permutation();
        if (permutation.length != 256) {
            throw new IllegalStateException("unexpected permutation length: " + permutation.length);
        }
        out.writeBytes(permutation);
    }

    /**
     * Serializes a {@code PerlinNoise}.
     *
     * @param perlin the noise field
     *
     * @return the descriptor bytes
     */
    public static byte[] writePerlin(PerlinNoise perlin) {
        var out = new ByteArrayOutputStream();
        writeHeader(out, KIND_PERLIN);
        writePerlin(out, perlin);
        return out.toByteArray();
    }

    /**
     * Serializes an {@code ImprovedNoise}.
     *
     * @param improved the noise level
     *
     * @return the descriptor bytes
     */
    public static byte[] writeImproved(ImprovedNoise improved) {
        var out = new ByteArrayOutputStream();
        writeHeader(out, KIND_IMPROVED);
        writeImprovedBody(out, improved);
        return out.toByteArray();
    }

}
