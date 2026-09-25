package top.likoslupus.ferrum.noise;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import top.likoslupus.ferrum.runtime.FerrumRuntime;
import top.likoslupus.ferrum.runtime.config.FerrumConfig;
import top.likoslupus.ferrum.runtime.ffm.NativeLibraryLocator;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import static java.util.Objects.requireNonNull;

/**
 * Fixed-seed grid differential against a real {@link NoiseChunk}.
 *
 * <p>This is the in-game-shaped correctness gate for the leaf grid path: a {@code NoiseChunk}
 * provides the exact cell coordinates vanilla would use, and the native fill must reproduce the
 * vanilla raw bits for the whole cell. Mixin accessors are inactive under JUnit, so the descriptor
 * comes from the test-side extractor; the hook's coordinate mapping and kernel are the production
 * code.
 */
@Tag("native")
class NoiseGridDifferentialTest {

    private static final long SEED = 123456789L;

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
    }

    @AfterAll
    static void resetRuntime() {
        NoiseHandleCache.closeAll();
        FerrumRuntime.instance().reset();
    }

    @Test
    @SuppressWarnings("UnusedVariable")
    void fixedSeedGridMatchesVanillaRawBits() {
        var lookup = VanillaRegistries.createLookup();
        HolderGetter<NormalNoise.NoiseParameters> noises = lookup.lookupOrThrow(Registries.NOISE);
        var settings = NoiseGeneratorSettings.dummy();
        var randomState = RandomState.create(settings, noises, SEED);
        var noiseSettings = settings.noiseSettings();

        var parameters = new NormalNoise.NoiseParameters(
                -4,
                List.of(1.0, 0.5, 0.25, 0.125)
        );
        var normal = NormalNoise.create(RandomSource.create(SEED), parameters);
        var holder = Holder.direct(parameters);
        var noiseHolder = new DensityFunction.NoiseHolder(holder, normal);
        var leaf = DensityFunctions.noise(holder, 1.0, 1.0)
                .mapAll(new DensityFunction.Visitor() {
                    @Override
                    public DensityFunction apply(DensityFunction function) {
                        return function;
                    }

                    @Override
                    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder ignored) {
                        return noiseHolder;
                    }
                });

        var fluidPicker = (Aquifer.FluidPicker) (_, _, _) -> new Aquifer.FluidStatus(
                0,
                Blocks.AIR.defaultBlockState()
        );
        var cellWidth = noiseSettings.getCellWidth();
        var cellHeight = noiseSettings.getCellHeight();
        var chunk = new NoiseChunk(
                16 / cellWidth,
                randomState,
                0,
                0,
                noiseSettings,
                beardifierMarker(),
                settings,
                fluidPicker,
                Blender.empty()
        );

        var count = cellWidth * cellWidth * cellHeight;
        var vanilla = new double[count];
        leaf.fillArray(vanilla, chunk);

        var handle = NativeNoise.createFromDescriptor(NoiseTestExtractor.normal(normal));
        assertNotEquals(0L, handle, "create failed");
        var nativeOut = new double[count];
        assertTrue(
                NoiseHook.fillWithHandle(handle, 1.0, 1.0, nativeOut, chunk),
                "native grid fill failed"
        );

        IntStream.range(0, count)
                .forEach(index -> assertEquals(
                        Double.doubleToRawLongBits(vanilla[index]),
                        Double.doubleToRawLongBits(nativeOut[index]),
                        "grid raw-bits mismatch at cell index " + index
                ));
        NativeNoise.destroy(handle);
    }

    private static DensityFunctions.BeardifierOrMarker beardifierMarker() {
        try {
            var field = Class
                    .forName("net.minecraft.world.level.levelgen.DensityFunctions$BeardifierMarker")
                    .getField("INSTANCE");
            return (DensityFunctions.BeardifierOrMarker) requireNonNull(field.get(null));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("cannot read BeardifierMarker.INSTANCE", exception);
        }
    }

}
