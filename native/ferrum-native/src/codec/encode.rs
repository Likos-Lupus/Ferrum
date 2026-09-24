//! Encoding of lz4-java `LZ4Block` streams.
//!
//! The encoder mirrors `LZ4BlockOutputStream`: fixed-size blocks of at most 64 KiB (or the ceiling
//! implied by the requested level), a raw block whenever compression does not shrink the payload,
//! an XXH32 checksum of the *decompressed* block, and a zero-length terminator block.

use lz4_flex::block::{compress_into, get_maximum_output_size};
use xxhash_rust::xxh32::xxh32;

use crate::abi::{
    FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INTERNAL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK,
};
use crate::arith::checked_add;
use crate::codec::framing::{
    CHECKSUM_MASK, COMPRESSION_METHOD_LZ4, COMPRESSION_METHOD_RAW, DEFAULT_BLOCK_SIZE,
    DEFAULT_SEED, HEADER_LEN, MAGIC, MAGIC_LEN, write_u32_le,
};

/// Encodes `src` into an lz4-java `LZ4Block` stream.
///
/// `level` is the lz4-java token nibble (0..=15); Minecraft passes the nibble derived from the
/// default 64 KiB block size (6). On success `written` receives the encoded size. When `dst` is too
/// small, `written` receives the required capacity and [`FERRUM_ERR_BUFFER_TOO_SMALL`] is returned.
pub fn encode_stream(src: &[u8], dst: &mut [u8], level: i32, written: &mut usize) -> i32 {
    if !(0..=0x0F).contains(&level) {
        *written = 0;
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    let nibble = level as u8;
    let block_size = block_size_for(nibble);

    let required = match required_size(src.len(), block_size) {
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

    let mut input = 0usize;
    let mut out = 0usize;
    while input < src.len() {
        let original_len = (src.len() - input).min(block_size);
        let block = &src[input..input + original_len];
        let checksum = xxh32(block, DEFAULT_SEED) & CHECKSUM_MASK;
        let max_compressed = get_maximum_output_size(original_len);

        let payload_at = out + HEADER_LEN;
        let compressed_len =
            match compress_into(block, &mut dst[payload_at..payload_at + max_compressed]) {
                Ok(value) => value,
                Err(_) => {
                    *written = 0;
                    return FERRUM_ERR_INTERNAL;
                }
            };

        let (method, payload_len) = if compressed_len >= original_len {
            dst[payload_at..payload_at + original_len].copy_from_slice(block);
            (COMPRESSION_METHOD_RAW, original_len)
        } else {
            (COMPRESSION_METHOD_LZ4, compressed_len)
        };

        write_header(
            dst,
            out,
            method,
            nibble,
            payload_len,
            original_len,
            checksum,
        );
        out = payload_at + payload_len;
        input += original_len;
    }

    write_header(dst, out, COMPRESSION_METHOD_RAW, nibble, 0, 0, 0);
    out += HEADER_LEN;

    *written = out;
    FERRUM_OK
}

/// The block size for a token nibble: the nibble ceiling, capped at Minecraft's 64 KiB default.
fn block_size_for(nibble: u8) -> usize {
    let ceiling = 1usize << (10 + u32::from(nibble & 0x0F));
    ceiling.min(DEFAULT_BLOCK_SIZE)
}

/// The worst-case encoded size for `src_len` bytes split into `block_size` blocks.
fn required_size(src_len: usize, block_size: usize) -> Result<usize, i32> {
    let mut total = 0usize;
    let mut remaining = src_len;
    while remaining > 0 {
        let original_len = remaining.min(block_size);
        let payload = original_len.max(get_maximum_output_size(original_len));
        total = checked_add(total, checked_add(HEADER_LEN, payload)?)?;
        remaining -= original_len;
    }
    checked_add(total, HEADER_LEN)
}

fn write_header(
    dst: &mut [u8],
    at: usize,
    method: u8,
    nibble: u8,
    compressed_len: usize,
    original_len: usize,
    checksum: u32,
) {
    dst[at..at + MAGIC_LEN].copy_from_slice(&MAGIC);
    dst[at + MAGIC_LEN] = method | (nibble & 0x0F);
    write_u32_le(dst, at + MAGIC_LEN + 1, compressed_len as u32);
    write_u32_le(dst, at + MAGIC_LEN + 5, original_len as u32);
    write_u32_le(dst, at + MAGIC_LEN + 9, checksum);
}
