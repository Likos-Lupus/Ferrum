package top.likoslupus.ferrum.noise.mixin;

import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private state of {@link NormalNoise} for descriptor serialization.
 */
@Mixin(NormalNoise.class)
public interface NormalNoiseAccessor {

    @Accessor("first")
    PerlinNoise ferrum$first();

    @Accessor("second")
    PerlinNoise ferrum$second();

    @Accessor("valueFactor")
    double ferrum$valueFactor();

}
