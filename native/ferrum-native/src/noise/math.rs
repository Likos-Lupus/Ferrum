//! Exact replicas of the vanilla math used by the noise leaf kernels.
//!
//! The kernels must reproduce `net.minecraft.world.level.levelgen.synth` bit for bit (ADR-0006), so
//! every helper here mirrors the corresponding `Mth` / `SimplexNoise` operation in evaluation
//! order. No fast-math, no reassociation, and no fused multiply-add is used.

/// The 16 gradient rows of `SimplexNoise.GRADIENT`, in declaration order.
pub const GRADIENT: [[i32; 3]; 16] = [
    [1, 1, 0],
    [-1, 1, 0],
    [1, -1, 0],
    [-1, -1, 0],
    [1, 0, 1],
    [-1, 0, 1],
    [1, 0, -1],
    [-1, 0, -1],
    [0, 1, 1],
    [0, -1, 1],
    [0, 1, -1],
    [0, -1, -1],
    [1, 1, 0],
    [0, -1, 1],
    [-1, 1, 0],
    [0, -1, -1],
];

/// The wrap period used by `PerlinNoise.wrap`, from the float literal `3.3554432E7F`.
pub const WRAP_PERIOD: f64 = 3.3554432E7_f32 as f64;

/// `SimplexNoise.dot`.
#[inline]
pub fn dot(gradient: [i32; 3], x: f64, y: f64, z: f64) -> f64 {
    gradient[0] as f64 * x + gradient[1] as f64 * y + gradient[2] as f64 * z
}

/// `Mth.floor(double)`, truncated to `i32` exactly like a Java `(int)` cast.
#[inline]
pub fn floor(value: f64) -> i32 {
    value.floor() as i32
}

/// `Mth.lfloor(double)`, truncated to `i64` exactly like a Java `(long)` cast.
#[inline]
pub fn lfloor(value: f64) -> i64 {
    value.floor() as i64
}

/// `Mth.lerp`.
#[inline]
pub fn lerp(alpha: f64, start: f64, end: f64) -> f64 {
    start + alpha * (end - start)
}

/// `Mth.lerp2`.
#[inline]
pub fn lerp2(alpha1: f64, alpha2: f64, x00: f64, x10: f64, x01: f64, x11: f64) -> f64 {
    lerp(alpha2, lerp(alpha1, x00, x10), lerp(alpha1, x01, x11))
}

/// `Mth.lerp3`.
#[inline]
#[allow(clippy::too_many_arguments)]
pub fn lerp3(
    alpha1: f64,
    alpha2: f64,
    alpha3: f64,
    x000: f64,
    x100: f64,
    x010: f64,
    x110: f64,
    x001: f64,
    x101: f64,
    x011: f64,
    x111: f64,
) -> f64 {
    lerp(
        alpha3,
        lerp2(alpha1, alpha2, x000, x100, x010, x110),
        lerp2(alpha1, alpha2, x001, x101, x011, x111),
    )
}

/// `Mth.smoothstep`.
#[inline]
pub fn smoothstep(value: f64) -> f64 {
    value * value * value * (value * (value * 6.0 - 15.0) + 10.0)
}

/// `PerlinNoise.wrap`.
#[inline]
pub fn wrap(value: f64) -> f64 {
    value - lfloor(value / WRAP_PERIOD + 0.5) as f64 * WRAP_PERIOD
}

/// The `(double) 1.0E-7F` constant used by the legacy `ImprovedNoise` fudge branch.
pub const SHIFT_UP_EPSILON: f64 = 1.0E-7_f32 as f64;

/// The `NormalNoise.INPUT_FACTOR` constant.
pub const INPUT_FACTOR: f64 = 1.0181268882175227;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn smoothstep_matches_vanilla_anchors() {
        assert_eq!(smoothstep(0.0), 0.0);
        assert_eq!(smoothstep(1.0), 1.0);
        assert_eq!(smoothstep(0.5), 0.5);
    }

    #[test]
    fn wrap_wraps_at_the_period() {
        assert_eq!(wrap(0.0), 0.0);
        assert_eq!(wrap(1.0), 1.0);
        assert_eq!(wrap(WRAP_PERIOD), 0.0);
        assert_eq!(wrap(WRAP_PERIOD + 1.0), 1.0);
        assert_eq!(wrap(-1.0), -1.0);
    }

    #[test]
    fn lerp_interpolates_endpoints() {
        assert_eq!(lerp(0.0, 3.0, 7.0), 3.0);
        assert_eq!(lerp(1.0, 3.0, 7.0), 7.0);
        assert_eq!(lerp(0.5, 0.0, 1.0), 0.5);
    }

    #[test]
    fn floor_matches_java_truncation() {
        assert_eq!(floor(-0.5), -1);
        assert_eq!(floor(2.9), 2);
        assert_eq!(lfloor(2.9), 2);
        assert_eq!(lfloor(-2.1), -3);
    }
}
