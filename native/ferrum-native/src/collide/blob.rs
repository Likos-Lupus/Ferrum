//! Parsing and serialization for the collide batch blobs (ADR-0019).

use crate::abi::{FERRUM_ERR_LIMIT_EXCEEDED, FERRUM_ERR_MALFORMED_INPUT, FERRUM_ERR_UNSUPPORTED};

use super::aabb_clip::{self, ClipResult};
use super::sweep::Shape;

/// Input blob magic for the AABB clip (`FBCA`).
pub const MAGIC_CLIP_IN: [u8; 4] = *b"FBCA";
/// Output blob magic for the AABB clip (`FBCO`).
pub const MAGIC_CLIP_OUT: [u8; 4] = *b"FBCO";
/// Input blob magic for the sweep (`FBCS`).
pub const MAGIC_SWEEP_IN: [u8; 4] = *b"FBCS";
/// Output blob magic for the sweep (`FBCT`).
pub const MAGIC_SWEEP_OUT: [u8; 4] = *b"FBCT";

/// The blob format version understood by this build.
pub const VERSION: u8 = 1;

/// The byte size of an `FBCO` output blob.
pub const CLIP_OUTPUT_SIZE: usize = 24;
/// The byte size of an `FBCT` output blob.
pub const SWEEP_OUTPUT_SIZE: usize = 32;

const MAX_BOXES: usize = 4096;
const MAX_SHAPES: usize = 4096;
const MAX_DIM: usize = 64;

/// A parsed `FBCA` clip input.
pub struct ClipInput {
    pub from: [f64; 3],
    pub to: [f64; 3],
    pub boxes: Vec<[f64; 6]>,
}

/// A parsed `FBCS` sweep input.
pub struct SweepInput {
    pub moving: [f64; 6],
    pub movement: [f64; 3],
    pub axis_order: [u8; 3],
    pub shapes: Vec<Shape>,
}

/// Parses an `FBCA` clip input blob.
pub fn parse_clip(bytes: &[u8]) -> Result<ClipInput, i32> {
    let mut reader = Reader::new(bytes);
    read_header(&mut reader, &MAGIC_CLIP_IN)?;

    let from = reader.read_f64_3()?;
    let to = reader.read_f64_3()?;
    let box_count = reader.read_u32()? as usize;
    if box_count > MAX_BOXES {
        return Err(FERRUM_ERR_LIMIT_EXCEEDED);
    }

    let mut boxes = Vec::with_capacity(box_count);
    for _ in 0..box_count {
        boxes.push(reader.read_f64_6()?);
    }

    if reader.position != bytes.len() {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    Ok(ClipInput { from, to, boxes })
}

/// Parses an `FBCS` sweep input blob.
pub fn parse_sweep(bytes: &[u8]) -> Result<SweepInput, i32> {
    let mut reader = Reader::new(bytes);
    read_header(&mut reader, &MAGIC_SWEEP_IN)?;

    let moving = reader.read_f64_6()?;
    let movement = reader.read_f64_3()?;
    let axis_order = [reader.read_u8()?, reader.read_u8()?, reader.read_u8()?];
    let _reserved = reader.read_u8()?;
    if !is_axis_permutation(axis_order) {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    let shape_count = reader.read_u32()? as usize;
    if shape_count > MAX_SHAPES {
        return Err(FERRUM_ERR_LIMIT_EXCEEDED);
    }

    let mut shapes = Vec::with_capacity(shape_count);
    for _ in 0..shape_count {
        let sx = reader.read_u16()? as usize;
        let sy = reader.read_u16()? as usize;
        let sz = reader.read_u16()? as usize;
        let reserved = reader.read_u16()?;
        if reserved != 0 || sx > MAX_DIM || sy > MAX_DIM || sz > MAX_DIM {
            return Err(FERRUM_ERR_MALFORMED_INPUT);
        }

        let mut xs = Vec::with_capacity(sx + 1);
        for _ in 0..=sx {
            xs.push(reader.read_f64()?);
        }
        let mut ys = Vec::with_capacity(sy + 1);
        for _ in 0..=sy {
            ys.push(reader.read_f64()?);
        }
        let mut zs = Vec::with_capacity(sz + 1);
        for _ in 0..=sz {
            zs.push(reader.read_f64()?);
        }

        let cells = sx
            .checked_mul(sy)
            .and_then(|value| value.checked_mul(sz))
            .ok_or(FERRUM_ERR_LIMIT_EXCEEDED)?;
        let bit_bytes = cells.div_ceil(8);
        let packed = reader.read_bytes(bit_bytes)?;
        if cells % 8 != 0 && bit_bytes > 0 {
            let used = cells % 8;
            let mask = (1u8 << used) - 1;
            if packed[bit_bytes - 1] & !mask != 0 {
                return Err(FERRUM_ERR_MALFORMED_INPUT);
            }
        }
        let mut full = vec![0u64; bit_bytes.div_ceil(8)];
        for (index, byte) in packed.iter().enumerate() {
            full[index / 8] |= u64::from(*byte) << ((index % 8) * 8);
        }

        shapes.push(Shape {
            sx,
            sy,
            sz,
            xs,
            ys,
            zs,
            full,
        });
    }

    if reader.position != bytes.len() {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    Ok(SweepInput {
        moving,
        movement,
        axis_order,
        shapes,
    })
}

/// Writes an `FBCO` clip output blob.
pub fn write_clip(result: &ClipResult, out: &mut [u8]) -> usize {
    let mut writer = Writer::new(out);
    writer.put_bytes(&MAGIC_CLIP_OUT);
    writer.put_u8(VERSION);
    writer.put_u8(0);
    writer.put_u16(0);
    writer.put_u8(u8::from(result.found));
    writer.put_u8(result.direction);
    writer.put_u16(0);
    writer.put_f64(result.scale);
    writer.put_u32(result.box_index);
    writer.position
}

/// Writes an `FBCT` sweep output blob.
pub fn write_sweep(resolved: &[f64; 3], out: &mut [u8]) -> usize {
    let mut writer = Writer::new(out);
    writer.put_bytes(&MAGIC_SWEEP_OUT);
    writer.put_u8(VERSION);
    writer.put_u8(0);
    writer.put_u16(0);
    for value in resolved {
        writer.put_f64(*value);
    }
    writer.position
}

/// Computes the clip output size for any result.
pub fn clip_output_size() -> usize {
    CLIP_OUTPUT_SIZE
}

/// Computes the sweep output size.
pub fn sweep_output_size() -> usize {
    SWEEP_OUTPUT_SIZE
}

/// Convenience used by tests: the clip result for a parsed input.
pub fn clip(input: &ClipInput) -> ClipResult {
    aabb_clip::clip(input.from, input.to, &input.boxes)
}

fn read_header(reader: &mut Reader<'_>, magic: &[u8; 4]) -> Result<(), i32> {
    if reader.read_bytes(4)? != magic {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }
    let version = reader.read_u8()?;
    let flags = reader.read_u8()?;
    let reserved = reader.read_u16()?;
    if version != VERSION {
        return Err(FERRUM_ERR_UNSUPPORTED);
    }
    if flags != 0 || reserved != 0 {
        return Err(FERRUM_ERR_UNSUPPORTED);
    }
    Ok(())
}

fn is_axis_permutation(order: [u8; 3]) -> bool {
    let mut seen = [false; 3];
    for value in order {
        if value > 2 || seen[value as usize] {
            return false;
        }
        seen[value as usize] = true;
    }
    true
}

/// A bounds-checked little-endian cursor over bytes.
pub struct Reader<'a> {
    bytes: &'a [u8],
    position: usize,
}

impl<'a> Reader<'a> {
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, position: 0 }
    }

    fn read_bytes(&mut self, length: usize) -> Result<&'a [u8], i32> {
        let end = self
            .position
            .checked_add(length)
            .ok_or(FERRUM_ERR_MALFORMED_INPUT)?;
        if end > self.bytes.len() {
            return Err(FERRUM_ERR_MALFORMED_INPUT);
        }
        let slice = &self.bytes[self.position..end];
        self.position = end;
        Ok(slice)
    }

    fn read_u8(&mut self) -> Result<u8, i32> {
        Ok(self.read_bytes(1)?[0])
    }

    fn read_u16(&mut self) -> Result<u16, i32> {
        let bytes = self.read_bytes(2)?;
        Ok(u16::from_le_bytes([bytes[0], bytes[1]]))
    }

    fn read_u32(&mut self) -> Result<u32, i32> {
        let bytes = self.read_bytes(4)?;
        Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn read_u64(&mut self) -> Result<u64, i32> {
        let bytes = self.read_bytes(8)?;
        Ok(u64::from_le_bytes([
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
        ]))
    }

    fn read_f64(&mut self) -> Result<f64, i32> {
        Ok(f64::from_bits(self.read_u64()?))
    }

    fn read_f64_3(&mut self) -> Result<[f64; 3], i32> {
        Ok([self.read_f64()?, self.read_f64()?, self.read_f64()?])
    }

    fn read_f64_6(&mut self) -> Result<[f64; 6], i32> {
        Ok([
            self.read_f64()?,
            self.read_f64()?,
            self.read_f64()?,
            self.read_f64()?,
            self.read_f64()?,
            self.read_f64()?,
        ])
    }
}

/// A minimal little-endian writer.
struct Writer<'a> {
    bytes: &'a mut [u8],
    position: usize,
}

impl<'a> Writer<'a> {
    fn new(bytes: &'a mut [u8]) -> Self {
        Self { bytes, position: 0 }
    }

    fn put_bytes(&mut self, value: &[u8]) {
        let end = self.position + value.len();
        self.bytes[self.position..end].copy_from_slice(value);
        self.position = end;
    }

    fn put_u8(&mut self, value: u8) {
        self.bytes[self.position] = value;
        self.position += 1;
    }

    fn put_u16(&mut self, value: u16) {
        self.put_bytes(&value.to_le_bytes());
    }

    fn put_u32(&mut self, value: u32) {
        self.put_bytes(&value.to_le_bytes());
    }

    fn put_f64(&mut self, value: f64) {
        self.put_bytes(&value.to_bits().to_le_bytes());
    }
}
