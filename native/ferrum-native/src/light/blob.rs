//! Parsing and serialization for the block-light batch blob (ADR-0018).

use std::collections::VecDeque;

use crate::abi::{FERRUM_ERR_MALFORMED_INPUT, FERRUM_ERR_UNSUPPORTED};

use super::engine::{Batch, CELLS, MAX_PALETTE, MAX_QUEUE, MAX_SECTIONS, Node, Property, Section};

/// Input blob magic (`FBLT`).
pub const MAGIC_IN: [u8; 4] = *b"FBLT";
/// Output blob magic (`FBLO`).
pub const MAGIC_OUT: [u8; 4] = *b"FBLO";
/// The blob format version understood by this build.
pub const VERSION: u8 = 1;

const OUTPUT_HEADER_SIZE: usize = 16;
const OUTPUT_SECTION_SIZE: usize = 16 + 2048;

/// Parses an input blob into a batch.
pub fn parse(bytes: &[u8]) -> Result<Batch, i32> {
    let mut reader = Reader::new(bytes);
    if reader.read_bytes(4)? != MAGIC_IN {
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

    let section_count = reader.read_u32()? as usize;
    let palette_count = reader.read_u32()? as usize;
    let decrease_count = reader.read_u32()? as usize;
    let increase_count = reader.read_u32()? as usize;
    let check_count = reader.read_u32()? as usize;
    let reserved2 = reader.read_u32()?;
    if reserved2 != 0 {
        return Err(FERRUM_ERR_UNSUPPORTED);
    }
    if section_count > MAX_SECTIONS
        || palette_count > MAX_PALETTE
        || decrease_count > MAX_QUEUE
        || increase_count > MAX_QUEUE
        || check_count > MAX_QUEUE
    {
        return Err(crate::abi::FERRUM_ERR_LIMIT_EXCEEDED);
    }

    let mut palette = Vec::with_capacity(palette_count);
    for _ in 0..palette_count {
        let opacity = reader.read_u8()?;
        let emission = reader.read_u8()?;
        let empty_shape = reader.read_u8()?;
        let reserved = reader.read_u8()?;
        if reserved != 0 || empty_shape > 1 || opacity == 0 || opacity > 15 || emission > 15 {
            return Err(FERRUM_ERR_MALFORMED_INPUT);
        }
        palette.push(Property {
            opacity,
            emission,
            empty_shape: empty_shape != 0,
        });
    }

    let mut sections = Vec::with_capacity(section_count);
    for _ in 0..section_count {
        let x = reader.read_i32()?;
        let y = reader.read_i32()?;
        let z = reader.read_i32()?;
        let reserved = reader.read_i32()?;
        let default_level = reader.read_u8()?;
        let has_data = reader.read_u8()?;
        let light_on = reader.read_u8()?;
        let reserved2 = reader.read_u8()?;
        if reserved != 0 || reserved2 != 0 || has_data > 1 || light_on > 1 || default_level > 15 {
            return Err(FERRUM_ERR_MALFORMED_INPUT);
        }

        let props_bytes = reader.read_bytes(CELLS * 2)?;
        let (pairs, _) = props_bytes.as_chunks::<2>();
        let mut props = Vec::with_capacity(CELLS);
        for chunk in pairs {
            let id = u16::from_le_bytes(*chunk);
            if id as usize >= palette_count {
                return Err(FERRUM_ERR_MALFORMED_INPUT);
            }
            props.push(id);
        }

        let levels = if has_data != 0 {
            let bytes = reader.read_bytes(CELLS)?;
            if bytes.iter().any(|value| *value > 15) {
                return Err(FERRUM_ERR_MALFORMED_INPUT);
            }
            bytes.to_vec()
        } else {
            vec![default_level; CELLS]
        };

        sections.push(Section {
            x,
            y,
            z,
            light_on: light_on != 0,
            props,
            levels,
            changed: false,
        });
    }

    let mut decreases = VecDeque::with_capacity(decrease_count);
    for _ in 0..decrease_count {
        let node = Node {
            x: reader.read_i32()?,
            y: reader.read_i32()?,
            z: reader.read_i32()?,
        };
        decreases.push_back((node, reader.read_u64()?));
    }

    let mut increases = VecDeque::with_capacity(increase_count);
    for _ in 0..increase_count {
        let node = Node {
            x: reader.read_i32()?,
            y: reader.read_i32()?,
            z: reader.read_i32()?,
        };
        increases.push_back((node, reader.read_u64()?));
    }

    let mut checks = Vec::with_capacity(check_count);
    for _ in 0..check_count {
        checks.push(Node {
            x: reader.read_i32()?,
            y: reader.read_i32()?,
            z: reader.read_i32()?,
        });
    }

    if reader.position != bytes.len() {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    Batch::new(palette, sections, decreases, increases, checks)
}

/// Returns the number of bytes the output blob requires.
pub fn required_output_size(batch: &Batch) -> usize {
    let changed = batch
        .sections
        .iter()
        .filter(|section| section.changed)
        .count();
    OUTPUT_HEADER_SIZE + changed * OUTPUT_SECTION_SIZE
}

/// Writes the output blob into `out`, returning the bytes written.
///
/// The caller must have checked the capacity with [`required_output_size`].
pub fn write_output(batch: &Batch, processed: u32, out: &mut [u8]) -> usize {
    let mut writer = Writer::new(out);
    writer.put_bytes(&MAGIC_OUT);
    writer.put_u8(VERSION);
    writer.put_u8(0);
    writer.put_u16(0);
    let changed: Vec<&Section> = batch
        .sections
        .iter()
        .filter(|section| section.changed)
        .collect();
    writer.put_u32(changed.len() as u32);
    writer.put_u32(processed);
    for section in changed {
        writer.put_i32(section.x);
        writer.put_i32(section.y);
        writer.put_i32(section.z);
        writer.put_i32(0);
        let mut packed = [0u8; 2048];
        for (cell, level) in section.levels.iter().enumerate() {
            let value = *level & 15;
            let byte = cell >> 1;
            let shift = (cell & 1) * 4;
            packed[byte] |= value << shift;
        }
        writer.put_bytes(&packed);
    }
    writer.position
}

/// A bounds-checked little-endian cursor over bytes.
struct Reader<'a> {
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

    fn read_i32(&mut self) -> Result<i32, i32> {
        Ok(self.read_u32()? as i32)
    }

    fn read_u64(&mut self) -> Result<u64, i32> {
        let bytes = self.read_bytes(8)?;
        Ok(u64::from_le_bytes([
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
        ]))
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

    fn put_i32(&mut self, value: i32) {
        self.put_bytes(&value.to_le_bytes());
    }
}
