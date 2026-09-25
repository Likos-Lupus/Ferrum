//! The versioned noise descriptor and its parser.
//!
//! A descriptor is a compact little-endian blob produced by the Java adapter from the live vanilla
//! noise objects. Every scalar is copied verbatim, including the derived frequency/value factors, so
//! the Rust side never re-derives them with a potentially different `pow` implementation.
//!
//! Format (little-endian):
//!
//! ```text
//! magic   [4]  "FBNS"
//! version u8   1
//! kind    u8   1 = Improved, 2 = Perlin, 3 = Normal
//! body         kind dependent
//! ```
//!
//! `Improved`: `xo f64, yo f64, zo f64, permutation[256]`.
//! `Perlin`  : `firstOctave i32, lowestFreqInputFactor f64, lowestFreqValueFactor f64,
//!              levelCount u32, amplitudes f64[levelCount], then one Improved body for every
//!              non-zero amplitude`.
//! `Normal`  : `valueFactor f64, first Perlin body, second Perlin body`.

use crate::abi::{FERRUM_ERR_LIMIT_EXCEEDED, FERRUM_ERR_MALFORMED_INPUT};

use super::improved::ImprovedNoise;
use super::normal::NormalField;
use super::perlin::PerlinField;

/// The descriptor magic, chosen to be distinct from the NBT arena magic.
pub const MAGIC: [u8; 4] = *b"FBNS";
/// The descriptor format version understood by this build.
pub const VERSION: u8 = 1;

const KIND_IMPROVED: u8 = 1;
const KIND_PERLIN: u8 = 2;
const KIND_NORMAL: u8 = 3;

/// The maximum number of octave levels accepted from a descriptor.
pub const MAX_LEVELS: u32 = 256;

/// A parsed noise field ready for batch evaluation.
pub enum NoiseField {
    Improved(ImprovedNoise),
    Perlin(PerlinField),
    Normal(NormalField),
}

impl NoiseField {
    /// Evaluates the field at one coordinate.
    #[inline]
    pub fn value(&self, x: f64, y: f64, z: f64) -> f64 {
        match self {
            Self::Improved(field) => field.value(x, y, z),
            Self::Perlin(field) => field.value(x, y, z),
            Self::Normal(field) => field.value(x, y, z),
        }
    }
}

/// Parses a descriptor, returning a status code on failure.
pub fn parse(bytes: &[u8]) -> Result<NoiseField, i32> {
    let mut reader = Reader::new(bytes);

    if reader.read_bytes(4)? != MAGIC {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }
    if reader.read_u8()? != VERSION {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    match reader.read_u8()? {
        KIND_IMPROVED => Ok(NoiseField::Improved(read_improved(&mut reader)?)),
        KIND_PERLIN => Ok(NoiseField::Perlin(read_perlin(&mut reader)?)),
        KIND_NORMAL => {
            let value_factor = reader.read_f64()?;
            let first = read_perlin(&mut reader)?;
            let second = read_perlin(&mut reader)?;
            Ok(NoiseField::Normal(NormalField {
                value_factor,
                first,
                second,
            }))
        }
        _ => Err(FERRUM_ERR_MALFORMED_INPUT),
    }
}

fn read_improved(reader: &mut Reader<'_>) -> Result<ImprovedNoise, i32> {
    let xo = reader.read_f64()?;
    let yo = reader.read_f64()?;
    let zo = reader.read_f64()?;
    let permutation = reader.read_bytes(256)?;
    let mut table = [0u8; 256];
    table.copy_from_slice(permutation);
    Ok(ImprovedNoise {
        xo,
        yo,
        zo,
        permutation: table,
    })
}

fn read_perlin(reader: &mut Reader<'_>) -> Result<PerlinField, i32> {
    // Consumed for format completeness; evaluation uses the stored factors only.
    let _first_octave = reader.read_i32()?;
    let lowest_freq_input_factor = reader.read_f64()?;
    let lowest_freq_value_factor = reader.read_f64()?;
    let level_count = reader.read_u32()?;
    if level_count > MAX_LEVELS {
        return Err(FERRUM_ERR_LIMIT_EXCEEDED);
    }

    let mut amplitudes = Vec::with_capacity(level_count as usize);
    for _ in 0..level_count {
        amplitudes.push(reader.read_f64()?);
    }

    let mut levels = Vec::with_capacity(level_count as usize);
    for amplitude in &amplitudes {
        if *amplitude != 0.0 {
            levels.push(Some(read_improved(reader)?));
        } else {
            levels.push(None);
        }
    }

    Ok(PerlinField {
        lowest_freq_input_factor,
        lowest_freq_value_factor,
        amplitudes,
        levels,
    })
}

/// A bounds-checked little-endian cursor over a descriptor.
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

    fn read_u32(&mut self) -> Result<u32, i32> {
        let bytes = self.read_bytes(4)?;
        Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn read_i32(&mut self) -> Result<i32, i32> {
        Ok(self.read_u32()? as i32)
    }

    fn read_f64(&mut self) -> Result<f64, i32> {
        let bytes = self.read_bytes(8)?;
        Ok(f64::from_le_bytes([
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
        ]))
    }
}
