//! Decoding of lz4-java `LZ4Block` streams.
//!
//! Decoding is a two-pass operation. The first pass walks the block headers and sums the declared
//! decompressed sizes, which both bounds the work and lets the caller size its destination buffer
//! (this is also the decompression-bomb guard: a stream that declares more than the caller's
//! capacity is rejected before any decompression happens). The second pass decodes each block and
//! verifies its XXH32 checksum, so a successful decode is guaranteed to reproduce the original
//! bytes.

use lz4_flex::block::decompress_into;
use xxhash_rust::xxh32::xxh32;

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_MALFORMED_INPUT, FERRUM_OK};
use crate::arith::checked_add;
use crate::codec::framing::{
    CHECKSUM_MASK, COMPRESSION_METHOD_LZ4, COMPRESSION_METHOD_RAW, DEFAULT_SEED, HEADER_LEN, MAGIC,
    MAGIC_LEN, block_size_ceiling, read_u32_le,
};

struct BlockHeader {
    method: u8,
    compressed_len: usize,
    original_len: usize,
    checksum: u32,
}

/// Decodes an entire stream into `dst`.
///
/// On success `written` receives the number of decoded bytes. When `dst` is too small, `written`
/// receives the required capacity and [`FERRUM_ERR_BUFFER_TOO_SMALL`] is returned. A malformed
/// stream yields [`FERRUM_ERR_MALFORMED_INPUT`].
pub fn decode_stream(src: &[u8], dst: &mut [u8], written: &mut usize) -> i32 {
    let required = match required_size(src) {
        Ok(value) => value,
        Err(status) => {
            *written = 0;
            return status;
        }
    };
    if required > dst.len() {
        *written = required;
        return FERRUM_ERR_BUFFER_TOO_SMALL;
    }

    let mut pos = 0usize;
    let mut out = 0usize;
    loop {
        let header = match read_header(src, pos) {
            Ok(Some(value)) => value,
            Ok(None) => break,
            Err(status) => {
                *written = 0;
                return status;
            }
        };
        pos += HEADER_LEN;

        let end = match checked_add(out, header.original_len) {
            Ok(value) => value,
            Err(status) => {
                *written = 0;
                return status;
            }
        };
        let payload_end = match checked_add(pos, header.compressed_len) {
            Ok(value) if value <= src.len() => value,
            Ok(_) => {
                *written = 0;
                return FERRUM_ERR_MALFORMED_INPUT;
            }
            Err(status) => {
                *written = 0;
                return status;
            }
        };

        let block = &mut dst[out..end];
        match header.method {
            COMPRESSION_METHOD_RAW => {
                block.copy_from_slice(&src[pos..payload_end]);
            }
            COMPRESSION_METHOD_LZ4 => match decompress_into(&src[pos..payload_end], block) {
                Ok(count) if count == header.original_len => {}
                _ => {
                    *written = 0;
                    return FERRUM_ERR_MALFORMED_INPUT;
                }
            },
            _ => {
                *written = 0;
                return FERRUM_ERR_MALFORMED_INPUT;
            }
        }
        if (xxh32(block, DEFAULT_SEED) & CHECKSUM_MASK) != header.checksum {
            *written = 0;
            return FERRUM_ERR_MALFORMED_INPUT;
        }

        pos = payload_end;
        out = end;
    }

    *written = out;
    FERRUM_OK
}

/// Sums the declared decompressed size of every block, validating each header.
fn required_size(src: &[u8]) -> Result<usize, i32> {
    let mut pos = 0usize;
    let mut total = 0usize;
    loop {
        match read_header(src, pos)? {
            Some(header) => {
                pos = checked_add(pos, HEADER_LEN)?;
                pos = checked_add(pos, header.compressed_len)?;
                total = checked_add(total, header.original_len)?;
            }
            None => return Ok(total),
        }
    }
}

/// Reads and validates the block header at `pos`.
///
/// Returns `Ok(None)` for the terminator block. A stream that ends before its terminator is
/// truncated and rejected, matching `LZ4BlockInputStream` with `stopOnEmptyBlock`.
fn read_header(src: &[u8], pos: usize) -> Result<Option<BlockHeader>, i32> {
    if pos
        .checked_add(HEADER_LEN)
        .is_none_or(|end| end > src.len())
    {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }
    if src[pos..pos + MAGIC_LEN] != MAGIC {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    let token = src[pos + MAGIC_LEN];
    let method = token & 0xF0;
    let nibble = token & 0x0F;
    if method != COMPRESSION_METHOD_RAW && method != COMPRESSION_METHOD_LZ4 {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    let compressed_len = read_u32_le(src, pos + MAGIC_LEN + 1) as usize;
    let original_len = read_u32_le(src, pos + MAGIC_LEN + 5) as usize;
    let checksum = read_u32_le(src, pos + MAGIC_LEN + 9);

    if original_len == 0 && compressed_len == 0 {
        if checksum != 0 {
            return Err(FERRUM_ERR_MALFORMED_INPUT);
        }
        return Ok(None);
    }
    if original_len > block_size_ceiling(nibble)
        || (original_len == 0) != (compressed_len == 0)
        || (method == COMPRESSION_METHOD_RAW && original_len != compressed_len)
    {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    let payload_end =
        checked_add(pos, HEADER_LEN).and_then(|value| checked_add(value, compressed_len))?;
    if payload_end > src.len() {
        return Err(FERRUM_ERR_MALFORMED_INPUT);
    }

    Ok(Some(BlockHeader {
        method,
        compressed_len,
        original_len,
        checksum,
    }))
}
