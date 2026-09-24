//! FerrumNbt native entry points.
//!
//! Not implemented yet: the exported symbols exist so the ABI surface is stable, but every call
//! returns [`FERRUM_ERR_UNSUPPORTED`](crate::abi::FERRUM_ERR_UNSUPPORTED).

use crate::abi::{FERRUM_ERR_UNSUPPORTED, FerrumLimits};
use crate::guard::guard;

/// Parses an NBT document into the flat arena format.
///
/// # Safety
///
/// `_src` must be valid for `_src_len` bytes, `_limits` must point to a valid [`FerrumLimits`], and
/// `_arena` must be valid for `_arena_cap` bytes. `_root_index` and `_used_or_required` must be
/// valid for writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_nbt_parse(
    _src: *const u8,
    _src_len: usize,
    _limits: *const FerrumLimits,
    _arena: *mut u8,
    _arena_cap: usize,
    _root_index: *mut u32,
    _used_or_required: *mut usize,
) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}

/// Writes NBT from the flat arena format back to the binary encoding.
///
/// # Safety
///
/// `_arena` must be valid for `_arena_len` bytes, `_dst` must be valid for `_dst_cap` bytes, and
/// `_written_or_required` must be valid for writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_nbt_write(
    _arena: *const u8,
    _arena_len: usize,
    _root_index: u32,
    _dst: *mut u8,
    _dst_cap: usize,
    _written_or_required: *mut usize,
) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}
