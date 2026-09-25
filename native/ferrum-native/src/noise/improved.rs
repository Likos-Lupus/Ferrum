//! A bit-exact replica of `net.minecraft.world.level.levelgen.synth.ImprovedNoise`.

use crate::noise::math;
use crate::noise::math::{floor, lerp3, smoothstep};

/// The permutation table and offsets of one `ImprovedNoise` level.
pub struct ImprovedNoise {
    pub xo: f64,
    pub yo: f64,
    pub zo: f64,
    /// The 256 permutation entries, stored as unsigned bytes (`p(x) = table[x & 255] & 0xFF`).
    pub permutation: [u8; 256],
}

impl ImprovedNoise {
    /// Evaluates the three-argument `noise`, equivalent to `noise(x, y, z, 0, 0)`.
    pub fn value(&self, x: f64, y: f64, z: f64) -> f64 {
        self.noise(x, y, z, 0.0, 0.0)
    }

    /// Evaluates the legacy `noise` overload, including the `yScale`/`yFudge` branch.
    pub fn noise(&self, x: f64, y: f64, z: f64, y_scale: f64, y_fudge: f64) -> f64 {
        let x = x + self.xo;
        let y = y + self.yo;
        let z = z + self.zo;
        let xf = floor(x);
        let yf = floor(y);
        let zf = floor(z);
        let xr = x - xf as f64;
        let yr = y - yf as f64;
        let zr = z - zf as f64;

        let yr_fudge = if y_scale != 0.0 {
            let fudge_limit = if y_fudge >= 0.0 && y_fudge < yr {
                y_fudge
            } else {
                yr
            };
            floor(fudge_limit / y_scale + math::SHIFT_UP_EPSILON) as f64 * y_scale
        } else {
            0.0
        };

        self.sample_and_lerp(xf, yf, zf, xr, yr - yr_fudge, zr, yr)
    }

    #[inline]
    fn perm(&self, value: i32) -> i32 {
        self.permutation[(value & 255) as usize] as i32
    }

    #[allow(clippy::too_many_arguments)]
    fn sample_and_lerp(
        &self,
        x: i32,
        y: i32,
        z: i32,
        xr: f64,
        yr: f64,
        zr: f64,
        yr_original: f64,
    ) -> f64 {
        let x0 = self.perm(x);
        let x1 = self.perm(x + 1);
        let xy00 = self.perm(x0 + y);
        let xy01 = self.perm(x0 + y + 1);
        let xy10 = self.perm(x1 + y);
        let xy11 = self.perm(x1 + y + 1);
        let d000 = grad_dot(self.perm(xy00 + z), xr, yr, zr);
        let d100 = grad_dot(self.perm(xy10 + z), xr - 1.0, yr, zr);
        let d010 = grad_dot(self.perm(xy01 + z), xr, yr - 1.0, zr);
        let d110 = grad_dot(self.perm(xy11 + z), xr - 1.0, yr - 1.0, zr);
        let d001 = grad_dot(self.perm(xy00 + z + 1), xr, yr, zr - 1.0);
        let d101 = grad_dot(self.perm(xy10 + z + 1), xr - 1.0, yr, zr - 1.0);
        let d011 = grad_dot(self.perm(xy01 + z + 1), xr, yr - 1.0, zr - 1.0);
        let d111 = grad_dot(self.perm(xy11 + z + 1), xr - 1.0, yr - 1.0, zr - 1.0);
        let x_alpha = smoothstep(xr);
        let y_alpha = smoothstep(yr_original);
        let z_alpha = smoothstep(zr);
        lerp3(
            x_alpha, y_alpha, z_alpha, d000, d100, d010, d110, d001, d101, d011, d111,
        )
    }
}

#[inline]
fn grad_dot(hash: i32, x: f64, y: f64, z: f64) -> f64 {
    math::dot(math::GRADIENT[(hash & 15) as usize], x, y, z)
}
