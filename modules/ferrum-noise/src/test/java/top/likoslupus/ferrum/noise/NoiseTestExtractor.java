package top.likoslupus.ferrum.noise;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

import java.io.ByteArrayOutputStream;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

/**
 * Test-only descriptor extractor.
 *
 * <p>Mixin accessors are only active inside the game runtime, so JUnit-based differential and
 * golden tests read the same private fields through reflection instead. The emitted format is
 * identical to {@link NoiseDescriptorWriter}, which is the production path validated in-game.
 */
final class NoiseTestExtractor {

    private static final byte[] MAGIC = {'F', 'B', 'N', 'S'};
    private static final byte VERSION = 1;
    private static final byte KIND_IMPROVED = 1;
    private static final byte KIND_PERLIN = 2;
    private static final byte KIND_NORMAL = 3;

    private NoiseTestExtractor() {
    }

    static byte[] normal(NormalNoise normal) {
        var out = new ByteArrayOutputStream();
        writeHeader(out, KIND_NORMAL);
        writeDouble(out, doubleField(normal, "valueFactor"));
        perlinBody(out, (PerlinNoise) field(normal, "first"));
        perlinBody(out, (PerlinNoise) field(normal, "second"));
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

    private static double doubleField(Object target, String name) {
        return (Double) field(target, name);
    }

    private static void perlinBody(ByteArrayOutputStream out, PerlinNoise perlin) {
        writeInt(out, intField(perlin, "firstOctave"));
        writeDouble(out, doubleField(perlin, "lowestFreqInputFactor"));
        writeDouble(out, doubleField(perlin, "lowestFreqValueFactor"));
        var amplitudes = (DoubleList) field(perlin, "amplitudes");
        writeInt(out, amplitudes.size());
        IntStream.range(0, amplitudes.size())
                .forEach(index ->
                        writeDouble(out, amplitudes.getDouble(index))
                );
        var levels = (ImprovedNoise[]) field(perlin, "noiseLevels");
        IntStream.range(0, amplitudes.size())
                .filter(index -> amplitudes.getDouble(index) != 0.0)
                .forEach(index -> improvedBody(out, levels[index]));
    }

    private static Object field(Object target, String name) {
        try {
            var declared = target.getClass().getDeclaredField(name);
            declared.setAccessible(true);
            return requireNonNull(declared.get(target), name);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "cannot read field " + name + " from " + target.getClass().getName(),
                    exception
            );
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static int intField(Object target, String name) {
        return (Integer) field(target, name);
    }

    private static void improvedBody(ByteArrayOutputStream out, ImprovedNoise improved) {
        writeDouble(out, improved.xo);
        writeDouble(out, improved.yo);
        writeDouble(out, improved.zo);
        var permutation = (byte[]) field(improved, "p");
        if (permutation.length != 256) {
            throw new IllegalStateException("unexpected permutation length: " + permutation.length);
        }
        out.writeBytes(permutation);
    }

    static byte[] perlin(PerlinNoise perlin) {
        var out = new ByteArrayOutputStream();
        writeHeader(out, KIND_PERLIN);
        perlinBody(out, perlin);
        return out.toByteArray();
    }

    static byte[] improved(ImprovedNoise improved) {
        var out = new ByteArrayOutputStream();
        writeHeader(out, KIND_IMPROVED);
        improvedBody(out, improved);
        return out.toByteArray();
    }

}
