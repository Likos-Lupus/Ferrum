//! The batch AABB ray clip (ADR-0019).
//!
//! Replicates `AABB.clip(Iterable, from, to, pos)` exactly: the shared `scaleReference`, the carried
//! last direction, and the `clipPoint` slab test in the `X, Y, Z` order. Input boxes are already in
//! world coordinates; the caller applies the block-position offset.

/// The vanilla `AABB.EPSILON`.
pub const EPSILON: f64 = 1.0e-7;

/// The "no hit" direction marker.
pub const DIRECTION_NONE: u8 = 0xFF;

/// Vanilla `Direction` ordinals.
pub const DOWN: u8 = 0;
pub const UP: u8 = 1;
pub const NORTH: u8 = 2;
pub const SOUTH: u8 = 3;
pub const WEST: u8 = 4;
pub const EAST: u8 = 5;

/// The clip result mirroring the surviving `scaleReference` and direction.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ClipResult {
    pub found: bool,
    pub direction: u8,
    pub scale: f64,
    pub box_index: u32,
}

/// Clips the ray `from -> to` against every box, mirroring `AABB.clip(Iterable, ...)`.
pub fn clip(from: [f64; 3], to: [f64; 3], boxes: &[[f64; 6]]) -> ClipResult {
    let mut scale = 1.0f64;
    let mut direction: Option<u8> = None;
    let mut box_index = 0u32;
    let dx = to[0] - from[0];
    let dy = to[1] - from[1];
    let dz = to[2] - from[2];

    for (index, data) in boxes.iter().enumerate() {
        let min_x = data[0];
        let min_y = data[1];
        let min_z = data[2];
        let max_x = data[3];
        let max_y = data[4];
        let max_z = data[5];

        let hit_x = if dx > EPSILON {
            clip_point(
                &mut scale, dx, dy, dz, min_x, min_y, max_y, min_z, max_z, from[0], from[1],
                from[2],
            )
        } else if dx < -EPSILON {
            clip_point(
                &mut scale, dx, dy, dz, max_x, min_y, max_y, min_z, max_z, from[0], from[1],
                from[2],
            )
        } else {
            false
        };
        if hit_x {
            direction = Some(if dx > 0.0 { WEST } else { EAST });
            box_index = index as u32;
        }

        let hit_y = if dy > EPSILON {
            clip_point(
                &mut scale, dy, dz, dx, min_y, min_z, max_z, min_x, max_x, from[1], from[2],
                from[0],
            )
        } else if dy < -EPSILON {
            clip_point(
                &mut scale, dy, dz, dx, max_y, min_z, max_z, min_x, max_x, from[1], from[2],
                from[0],
            )
        } else {
            false
        };
        if hit_y {
            direction = Some(if dy > 0.0 { DOWN } else { UP });
            box_index = index as u32;
        }

        let hit_z = if dz > EPSILON {
            clip_point(
                &mut scale, dz, dx, dy, min_z, min_x, max_x, min_y, max_y, from[2], from[0],
                from[1],
            )
        } else if dz < -EPSILON {
            clip_point(
                &mut scale, dz, dx, dy, max_z, min_x, max_x, min_y, max_y, from[2], from[0],
                from[1],
            )
        } else {
            false
        };
        if hit_z {
            direction = Some(if dz > 0.0 { NORTH } else { SOUTH });
            box_index = index as u32;
        }
    }

    match direction {
        Some(direction) => ClipResult {
            found: true,
            direction,
            scale,
            box_index,
        },
        None => ClipResult {
            found: false,
            direction: DIRECTION_NONE,
            scale,
            box_index: 0,
        },
    }
}

#[allow(clippy::too_many_arguments)]
fn clip_point(
    scale: &mut f64,
    da: f64,
    db: f64,
    dc: f64,
    point: f64,
    min_b: f64,
    max_b: f64,
    min_c: f64,
    max_c: f64,
    from_a: f64,
    from_b: f64,
    from_c: f64,
) -> bool {
    let s = (point - from_a) / da;
    let pb = from_b + s * db;
    let pc = from_c + s * dc;
    if 0.0 < s
        && s < *scale
        && min_b - EPSILON < pb
        && pb < max_b + EPSILON
        && min_c - EPSILON < pc
        && pc < max_c + EPSILON
    {
        *scale = s;
        true
    } else {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ray_hits_a_single_box() {
        let result = clip(
            [0.5, 0.5, -1.0],
            [0.5, 0.5, 1.0],
            &[[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]],
        );
        assert!(result.found);
        assert_eq!(result.box_index, 0);
        assert_eq!(result.direction, NORTH);
        assert!((result.scale - 0.5).abs() < 1e-12);
    }

    #[test]
    fn parallel_axis_misses() {
        let result = clip(
            [0.5, 2.0, -1.0],
            [0.5, 2.0, 1.0],
            &[[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]],
        );
        assert!(!result.found);
    }

    #[test]
    fn origin_inside_is_not_a_hit() {
        // AABB.clip requires 0 < s, so a box containing the origin yields no hit.
        let result = clip(
            [0.5, 0.5, 0.5],
            [0.5, 0.5, 2.0],
            &[[0.0, 0.0, 0.0, 1.0, 1.0, 1.0]],
        );
        assert!(!result.found);
    }

    #[test]
    fn nearest_box_wins() {
        let result = clip(
            [0.5, 0.5, -2.0],
            [0.5, 0.5, 4.0],
            &[
                [0.0, 0.0, 1.0, 1.0, 1.0, 2.0],
                [0.0, 0.0, 0.0, 1.0, 1.0, 1.0],
            ],
        );
        assert!(result.found);
        assert_eq!(result.box_index, 1);
        assert_eq!(result.direction, NORTH);
    }
}
