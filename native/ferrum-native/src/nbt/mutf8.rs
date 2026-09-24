//! Modified UTF-8 (Java `DataInput.readUTF` / `DataOutput.writeUTF`) codec.
//!
//! NBT strings are modified UTF-8, not standard UTF-8 (ADR-0002). Decoding yields UTF-16 code
//! units, so lone surrogates survive a round trip. Java's `readUTF` is lenient about overlong
//! encodings and accepts a literal `0x00` byte, which this decoder mirrors.

use crate::nbt::error::NbtError;

/// Decodes modified UTF-8 into UTF-16 code units.
///
/// Returns `Err(NbtError)` for any malformed sequence, matching `UTFDataFormatException`.
pub fn decode(bytes: &[u8]) -> Result<Vec<u16>, NbtError> {
    let mut out = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        let first = bytes[index];
        match first >> 4 {
            0..=7 => {
                out.push(u16::from(first));
                index += 1;
            }
            12 | 13 => {
                if index + 1 >= bytes.len() {
                    return Err(NbtError);
                }
                let second = bytes[index + 1];
                if second & 0xC0 != 0x80 {
                    return Err(NbtError);
                }
                out.push((u16::from(first & 0x1F) << 6) | u16::from(second & 0x3F));
                index += 2;
            }
            14 => {
                if index + 2 >= bytes.len() {
                    return Err(NbtError);
                }
                let second = bytes[index + 1];
                let third = bytes[index + 2];
                if second & 0xC0 != 0x80 || third & 0xC0 != 0x80 {
                    return Err(NbtError);
                }
                out.push(
                    (u16::from(first & 0x0F) << 12)
                        | (u16::from(second & 0x3F) << 6)
                        | u16::from(third & 0x3F),
                );
                index += 3;
            }
            _ => return Err(NbtError),
        }
    }
    Ok(out)
}

/// Returns the encoded length of the code units, or `Err(NbtError)` when it exceeds the 65535 limit.
pub fn encoded_len(units: &[u16]) -> Result<usize, NbtError> {
    let mut length = 0usize;
    for &unit in units {
        let size = if (0x0001..=0x007F).contains(&unit) {
            1
        } else if unit == 0 || (0x0080..=0x07FF).contains(&unit) {
            2
        } else {
            3
        };
        length += size;
        if length > 0xFFFF {
            return Err(NbtError);
        }
    }
    Ok(length)
}

/// Encodes code units as modified UTF-8 into `out`.
///
/// The caller must ensure `out` is at least [`encoded_len`] bytes; otherwise `Err(NbtError)` is returned.
pub fn encode(units: &[u16], out: &mut [u8]) -> Result<usize, NbtError> {
    let mut index = 0;
    for &unit in units {
        if (0x0001..=0x007F).contains(&unit) {
            if index >= out.len() {
                return Err(NbtError);
            }
            out[index] = unit as u8;
            index += 1;
        } else if unit == 0 || (0x0080..=0x07FF).contains(&unit) {
            if index + 2 > out.len() {
                return Err(NbtError);
            }
            out[index] = 0xC0 | (unit >> 6) as u8;
            out[index + 1] = 0x80 | (unit & 0x3F) as u8;
            index += 2;
        } else {
            if index + 3 > out.len() {
                return Err(NbtError);
            }
            out[index] = 0xE0 | (unit >> 12) as u8;
            out[index + 1] = 0x80 | ((unit >> 6) & 0x3F) as u8;
            out[index + 2] = 0x80 | (unit & 0x3F) as u8;
            index += 3;
        }
    }
    Ok(index)
}
