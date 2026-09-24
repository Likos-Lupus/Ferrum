//! FerrumPalette native entry points.
//!
//! Not implemented yet: the exported symbols exist so the ABI surface is stable, but every call
//! returns [`FERRUM_ERR_UNSUPPORTED`](crate::abi::FERRUM_ERR_UNSUPPORTED).

use crate::abi::FERRUM_ERR_UNSUPPORTED;
use crate::guard::guard;

/// Unpacks packed palette values into one `u32` per value.
///
/// # Safety
///
/// `_data` must be valid for `_data_len` elements, `_out_values` must be valid for `_out_len`
/// elements, and `_bits`/`_value_count` must describe the same value set.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_palette_unpack(
    _data: *const u64,
    _data_len: usize,
    _bits: u32,
    _value_count: usize,
    _out_values: *mut u32,
    _out_len: usize,
) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}

/// Packs one `u32` per value into the palette bit layout.
///
/// # Safety
///
/// `_values` must be valid for `_value_count` elements and `_out_data` must be valid for `_out_len`
/// elements.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_palette_pack(
    _values: *const u32,
    _value_count: usize,
    _bits: u32,
    _out_data: *mut u64,
    _out_len: usize,
) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}
