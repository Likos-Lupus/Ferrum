//! FerrumCodec native entry points.
//!
//! Not implemented yet: the exported symbols exist so the ABI surface is stable, but every call
//! returns [`FERRUM_ERR_UNSUPPORTED`](crate::abi::FERRUM_ERR_UNSUPPORTED).

use crate::abi::FERRUM_ERR_UNSUPPORTED;
use crate::guard::guard;

/// Decompresses an LZ4 block stream.
///
/// # Safety
///
/// `_src` must be valid for `_src_len` bytes, `_dst` must be valid for `_dst_cap` bytes, and
/// `_written_or_required` must be valid for writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_lz4_block_stream_decompress(
    _src: *const u8,
    _src_len: usize,
    _dst: *mut u8,
    _dst_cap: usize,
    _written_or_required: *mut usize,
) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}

/// Compresses bytes into an LZ4 block stream.
///
/// # Safety
///
/// `_src` must be valid for `_src_len` bytes, `_dst` must be valid for `_dst_cap` bytes, and
/// `_written_or_required` must be valid for writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_lz4_block_stream_compress(
    _src: *const u8,
    _src_len: usize,
    _dst: *mut u8,
    _dst_cap: usize,
    _compression_level: i32,
    _written_or_required: *mut usize,
) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}
