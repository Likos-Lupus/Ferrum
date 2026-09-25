//! A bit-exact replica of `net.minecraft.world.level.levelgen.synth.NormalNoise`.

use crate::noise::math::INPUT_FACTOR;
use crate::noise::perlin::PerlinField;

/// One `NormalNoise`, the sum of two `PerlinNoise` fields scaled by `value_factor`.
pub struct NormalField {
    pub value_factor: f64,
    pub first: PerlinField,
    pub second: PerlinField,
}

impl NormalField {
    /// Evaluates `getValue(x, y, z)`.
    pub fn value(&self, x: f64, y: f64, z: f64) -> f64 {
        let x2 = x * INPUT_FACTOR;
        let y2 = y * INPUT_FACTOR;
        let z2 = z * INPUT_FACTOR;
        (self.first.value(x, y, z) + self.second.value(x2, y2, z2)) * self.value_factor
    }
}
