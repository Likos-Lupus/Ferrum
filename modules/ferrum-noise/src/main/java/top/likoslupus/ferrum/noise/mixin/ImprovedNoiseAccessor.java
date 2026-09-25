package top.likoslupus.ferrum.noise.mixin;

import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private permutation table of {@link ImprovedNoise} for descriptor serialization.
 */
@Mixin(ImprovedNoise.class)
public interface ImprovedNoiseAccessor {

    @Accessor("p")
    byte[] ferrum$permutation();

}
