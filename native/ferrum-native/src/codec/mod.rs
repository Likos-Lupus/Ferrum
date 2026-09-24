//! FerrumCodec native entry points.
//!
//! Region payloads use the lz4-java `LZ4Block` framing, not the standard LZ4 Frame format. The
//! native kernel decodes and encodes that framing; Minecraft keeps region sectors, chunk lengths,
//! version bytes, and file locks.

mod decode;
mod encode;
pub mod framing;

pub use decode::decode_stream;
pub use encode::encode_stream;

use core::slice;

use crate::abi::FERRUM_ERR_INVALID_ARGUMENT;
use crate::guard::guard;

/// Decompresses an LZ4 block stream.
///
/// # Safety
///
/// `src` must be valid for `src_len` bytes (or null when `src_len` is zero), `dst` must be valid for
/// `dst_cap` bytes (or null when `dst_cap` is zero), and `written_or_required` must be valid for
/// writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_lz4_block_stream_decompress(
    src: *const u8,
    src_len: usize,
    dst: *mut u8,
    dst_cap: usize,
    written_or_required: *mut usize,
) -> i32 {
    guard(|| {
        if written_or_required.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if src.is_null() && src_len != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if dst.is_null() && dst_cap != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        // SAFETY: the caller guarantees `src` is valid for `src_len` bytes.
        let source: &[u8] = if src_len == 0 {
            &[]
        } else {
            unsafe { slice::from_raw_parts(src, src_len) }
        };
        // SAFETY: the caller guarantees `dst` is valid for `dst_cap` bytes.
        let destination: &mut [u8] = if dst_cap == 0 {
            &mut []
        } else {
            unsafe { slice::from_raw_parts_mut(dst, dst_cap) }
        };

        let mut written = 0usize;
        let status = decode_stream(source, destination, &mut written);
        // SAFETY: `written_or_required` is non-null and valid for one write.
        unsafe { written_or_required.write(written) };
        status
    })
}

/// Compresses bytes into an LZ4 block stream.
///
/// `compression_level` is the lz4-java token nibble (0..=15).
///
/// # Safety
///
/// `src` must be valid for `src_len` bytes (or null when `src_len` is zero), `dst` must be valid for
/// `dst_cap` bytes (or null when `dst_cap` is zero), and `written_or_required` must be valid for
/// writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_lz4_block_stream_compress(
    src: *const u8,
    src_len: usize,
    dst: *mut u8,
    dst_cap: usize,
    compression_level: i32,
    written_or_required: *mut usize,
) -> i32 {
    guard(|| {
        if written_or_required.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if src.is_null() && src_len != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if dst.is_null() && dst_cap != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        // SAFETY: the caller guarantees `src` is valid for `src_len` bytes.
        let source: &[u8] = if src_len == 0 {
            &[]
        } else {
            unsafe { slice::from_raw_parts(src, src_len) }
        };
        // SAFETY: the caller guarantees `dst` is valid for `dst_cap` bytes.
        let destination: &mut [u8] = if dst_cap == 0 {
            &mut []
        } else {
            unsafe { slice::from_raw_parts_mut(dst, dst_cap) }
        };

        let mut written = 0usize;
        let status = encode_stream(source, destination, compression_level, &mut written);
        // SAFETY: `written_or_required` is non-null and valid for one write.
        unsafe { written_or_required.write(written) };
        status
    })
}
