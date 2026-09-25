package top.likoslupus.ferrum.noise.mixin;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private state of {@link PerlinNoise} for descriptor serialization.
 */
@Mixin(PerlinNoise.class)
public interface PerlinNoiseAccessor {

    @Accessor("noiseLevels")
    ImprovedNoise[] ferrum$noiseLevels();

    @Accessor("firstOctave")
    int ferrum$firstOctave();

    @Accessor("amplitudes")
    DoubleList ferrum$amplitudes();

    @Accessor("lowestFreqInputFactor")
    double ferrum$lowestFreqInputFactor();

    @Accessor("lowestFreqValueFactor")
    double ferrum$lowestFreqValueFactor();

}
