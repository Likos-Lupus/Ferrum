//! A bit-exact replica of `net.minecraft.world.level.levelgen.synth.PerlinNoise`.

use crate::noise::improved::ImprovedNoise;
use crate::noise::math::wrap;

/// One `PerlinNoise`, holding the octave levels and the precomputed factors.
///
/// `levels[i]` is present exactly when `amplitudes[i] != 0`, matching the vanilla constructor; a
/// missing level still advances the per-octave factors in [`PerlinField::value`].
pub struct PerlinField {
    pub lowest_freq_input_factor: f64,
    pub lowest_freq_value_factor: f64,
    pub amplitudes: Vec<f64>,
    pub levels: Vec<Option<ImprovedNoise>>,
}

impl PerlinField {
    /// Evaluates `getValue(x, y, z)`.
    pub fn value(&self, x: f64, y: f64, z: f64) -> f64 {
        let mut value = 0.0;
        let mut factor = self.lowest_freq_input_factor;
        let mut value_factor = self.lowest_freq_value_factor;
        for index in 0..self.levels.len() {
            if let Some(noise) = &self.levels[index] {
                let noise_value = noise.value(wrap(x * factor), wrap(y * factor), wrap(z * factor));
                value += self.amplitudes[index] * noise_value * value_factor;
            }
            factor *= 2.0;
            value_factor /= 2.0;
        }
        value
    }
}
