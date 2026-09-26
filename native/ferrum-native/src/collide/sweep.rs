//! The batched voxel-shape sweep (ADR-0019).
//!
//! Replicates `Shapes.collide` + `VoxelShape.collideX`, including `Mth.binarySearch` index finding
//! and `DiscreteVoxelShape.isFullWide` under the `AxisCycle` transform, and applies the axes in the
//! adapter-supplied order exactly as `Entity.collideWithShapes` does.

/// The vanilla shape EPSILON.
pub const EPSILON: f64 = 1.0e-7;

/// A serialized `VoxelShape`: per-axis coordinate arrays plus an occupancy bitset.
#[derive(Clone, Debug)]
pub struct Shape {
    pub sx: usize,
    pub sy: usize,
    pub sz: usize,
    pub xs: Vec<f64>,
    pub ys: Vec<f64>,
    pub zs: Vec<f64>,
    pub full: Vec<u64>,
}

impl Shape {
    fn size(&self, axis: usize) -> usize {
        match axis {
            0 => self.sx,
            1 => self.sy,
            _ => self.sz,
        }
    }

    fn coord(&self, axis: usize, index: usize) -> f64 {
        match axis {
            0 => self.xs[index],
            1 => self.ys[index],
            _ => self.zs[index],
        }
    }

    fn is_full(&self, x: usize, y: usize, z: usize) -> bool {
        let index = x + self.sx * (y + self.sy * z);
        self.full[index >> 6] & (1u64 << (index & 63)) != 0
    }

    fn is_empty(&self) -> bool {
        self.full.iter().all(|word| *word == 0)
    }

    fn is_full_wide(&self, x: i64, y: i64, z: i64) -> bool {
        if x < 0 || y < 0 || z < 0 {
            return false;
        }
        let (x, y, z) = (x as usize, y as usize, z as usize);
        if x >= self.sx || y >= self.sy || z >= self.sz {
            false
        } else {
            self.is_full(x, y, z)
        }
    }
}

#[derive(Clone, Copy, PartialEq)]
enum Cycle {
    None,
    Forward,
    Backward,
}

impl Cycle {
    fn inverse(self) -> Cycle {
        match self {
            Cycle::None => Cycle::None,
            Cycle::Forward => Cycle::Backward,
            Cycle::Backward => Cycle::Forward,
        }
    }

    fn cycle_axis(self, axis: usize) -> usize {
        match self {
            Cycle::None => axis,
            Cycle::Forward => (axis + 1) % 3,
            Cycle::Backward => (axis + 2) % 3,
        }
    }

    fn cycle_coord(self, x: i64, y: i64, z: i64, axis: usize) -> i64 {
        match self {
            Cycle::None => [x, y, z][axis],
            Cycle::Forward => [z, x, y][axis],
            Cycle::Backward => [y, z, x][axis],
        }
    }
}

fn between(from: usize, to: usize) -> Cycle {
    match (to + 3 - from) % 3 {
        0 => Cycle::None,
        1 => Cycle::Forward,
        _ => Cycle::Backward,
    }
}

/// Sweeps `moving` by `movement` against the shapes, returning the resolved movement.
pub fn sweep(
    moving: &[f64; 6],
    movement: &[f64; 3],
    axis_order: &[u8; 3],
    shapes: &[Shape],
) -> [f64; 3] {
    let mut resolved = [0.0f64; 3];
    for &axis in axis_order {
        let a = axis as usize;
        let distance = movement[a];
        if distance != 0.0 {
            let box_moved = box_move(moving, &resolved);
            resolved[a] = collide_axis(a, &box_moved, shapes, distance);
        }
    }
    resolved
}

fn box_move(moving: &[f64; 6], delta: &[f64; 3]) -> [f64; 6] {
    [
        moving[0] + delta[0],
        moving[1] + delta[1],
        moving[2] + delta[2],
        moving[3] + delta[0],
        moving[4] + delta[1],
        moving[5] + delta[2],
    ]
}

fn box_min(moving: &[f64; 6], axis: usize) -> f64 {
    moving[axis]
}

fn box_max(moving: &[f64; 6], axis: usize) -> f64 {
    moving[axis + 3]
}

/// `Shapes.collide(axis, moving, shapes, distance)`.
fn collide_axis(axis: usize, moving: &[f64; 6], shapes: &[Shape], distance: f64) -> f64 {
    let mut resolved = distance;
    for shape in shapes {
        if resolved.abs() < EPSILON {
            return 0.0;
        }
        resolved = shape_collide(axis, moving, shape, resolved);
    }
    resolved
}

/// `VoxelShape.collide(axis, moving, distance)` = `collideX(AxisCycle.between(axis, X), ...)`.
fn shape_collide(axis: usize, moving: &[f64; 6], shape: &Shape, distance: f64) -> f64 {
    if shape.is_empty() {
        return distance;
    }
    if distance.abs() < EPSILON {
        return 0.0;
    }

    let transform = between(axis, 0);
    let inverse = transform.inverse();
    let a_axis = inverse.cycle_axis(0);
    let b_axis = inverse.cycle_axis(1);
    let c_axis = inverse.cycle_axis(2);

    let max_a = box_max(moving, a_axis);
    let min_a = box_min(moving, a_axis);
    let a_min = find_index(shape, a_axis, min_a + EPSILON);
    let a_max = find_index(shape, a_axis, max_a - EPSILON);
    let b_min = 0.max(find_index(shape, b_axis, box_min(moving, b_axis) + EPSILON));
    let b_max = (shape.size(b_axis) as i64)
        .min(find_index(shape, b_axis, box_max(moving, b_axis) - EPSILON) + 1);
    let c_min = 0.max(find_index(shape, c_axis, box_min(moving, c_axis) + EPSILON));
    let c_max = (shape.size(c_axis) as i64)
        .min(find_index(shape, c_axis, box_max(moving, c_axis) - EPSILON) + 1);
    let a_size = shape.size(a_axis) as i64;

    let mut distance = distance;
    if distance > 0.0 {
        let mut a = a_max + 1;
        while a < a_size {
            let mut b = b_min;
            while b < b_max {
                let mut c = c_min;
                while c < c_max {
                    if is_full_wide(shape, inverse, a, b, c) {
                        let new_distance = shape.coord(a_axis, a as usize) - max_a;
                        if new_distance >= -EPSILON {
                            distance = distance.min(new_distance);
                        }
                        return distance;
                    }
                    c += 1;
                }
                b += 1;
            }
            a += 1;
        }
    } else if distance < 0.0 {
        let mut a = a_min - 1;
        while a >= 0 {
            let mut b = b_min;
            while b < b_max {
                let mut c = c_min;
                while c < c_max {
                    if is_full_wide(shape, inverse, a, b, c) {
                        let new_distance = shape.coord(a_axis, (a + 1) as usize) - min_a;
                        if new_distance <= EPSILON {
                            distance = distance.max(new_distance);
                        }
                        return distance;
                    }
                    c += 1;
                }
                b += 1;
            }
            a -= 1;
        }
    }

    distance
}

fn is_full_wide(shape: &Shape, transform: Cycle, x: i64, y: i64, z: i64) -> bool {
    shape.is_full_wide(
        transform.cycle_coord(x, y, z, 0),
        transform.cycle_coord(x, y, z, 1),
        transform.cycle_coord(x, y, z, 2),
    )
}

/// `Mth.binarySearch(0, size + 1, i -> coord < get(axis, i)) - 1`.
fn find_index(shape: &Shape, axis: usize, coord: f64) -> i64 {
    let mut from = 0i64;
    let mut len = shape.size(axis) as i64 + 1;
    while len > 0 {
        let half = len / 2;
        let middle = from + half;
        if coord < shape.coord(axis, middle as usize) {
            len = half;
        } else {
            from = middle + 1;
            len -= half + 1;
        }
    }
    from - 1
}

#[cfg(test)]
mod tests {
    use super::*;

    fn full_cube() -> Shape {
        Shape {
            sx: 1,
            sy: 1,
            sz: 1,
            xs: vec![0.0, 1.0],
            ys: vec![0.0, 1.0],
            zs: vec![0.0, 1.0],
            full: vec![1],
        }
    }

    #[test]
    fn falling_box_lands_on_the_ground() {
        let moving = [-0.5, 2.5, -0.5, 0.5, 3.5, 0.5];
        let resolved = sweep(&moving, &[0.0, -2.0, 0.0], &[1, 0, 2], &[full_cube()]);
        assert!((resolved[1] - (-1.5)).abs() < 1e-9);
    }

    #[test]
    fn box_resting_on_the_ground_cannot_move_down() {
        let moving = [-0.5, 1.0, -0.5, 0.5, 2.0, 0.5];
        let resolved = sweep(&moving, &[0.0, -1.0, 0.0], &[1, 0, 2], &[full_cube()]);
        assert!(resolved[1].abs() < 1e-9);
    }

    #[test]
    fn horizontal_box_stops_before_a_wall() {
        let moving = [-1.5, 0.0, -0.5, -0.5, 1.0, 0.5];
        let resolved = sweep(&moving, &[2.0, 0.0, 0.0], &[1, 0, 2], &[full_cube()]);
        assert!((resolved[0] - 0.5).abs() < 1e-9);
    }

    #[test]
    fn no_shapes_passes_through() {
        let moving = [0.0, 0.0, 0.0, 1.0, 1.0, 1.0];
        let resolved = sweep(&moving, &[1.0, 2.0, 3.0], &[1, 0, 2], &[]);
        assert_eq!(resolved, [1.0, 2.0, 3.0]);
    }
}
