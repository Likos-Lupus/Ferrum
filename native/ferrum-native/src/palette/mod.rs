//! FerrumPalette native entry points: bulk `SimpleBitStorage` pack/unpack (ADR-0015).
//!
//! The layout is the vanilla `valuesPerLong = 64 / bits` packing: least-significant value first,
//! values never crossing a word boundary, and the unused high bits of each word left zero.

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use crate::guard::guard;

pub mod layout;
pub mod pack;
pub mod remap;
pub mod unpack;

/// Unpacks packed palette values into one `u32` per value.
///
/// # Safety
///
/// `data` must be valid for `data_len` `u64` values, `out_values` must be valid for `out_len`
/// `u32` values, and `bits`/`value_count` must describe the same value set.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_palette_unpack(
    data: *const u64,
    data_len: usize,
    bits: u32,
    value_count: usize,
    out_values: *mut u32,
    out_len: usize,
) -> i32 {
    guard(|| {
        if !layout::is_valid_bits(bits) {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let required = value_count.div_ceil(layout::values_per_long(bits));
        if data_len < required {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        if out_len < value_count {
            return FERRUM_ERR_BUFFER_TOO_SMALL;
        }

        if required == 0 {
            return FERRUM_OK;
        }

        if data.is_null() || out_values.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let input = unsafe { std::slice::from_raw_parts(data, required) };
        let output = unsafe { std::slice::from_raw_parts_mut(out_values, value_count) };
        unpack::unpack(input, bits, value_count, output)
    })
}

/// Packs one `u32` per value into the palette bit layout.
///
/// # Safety
///
/// `values` must be valid for `value_count` `u32` values and `out_data` must be valid for `out_len`
/// `u64` values.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_palette_pack(
    values: *const u32,
    value_count: usize,
    bits: u32,
    out_data: *mut u64,
    out_len: usize,
) -> i32 {
    guard(|| {
        if !layout::is_valid_bits(bits) {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let required = value_count.div_ceil(layout::values_per_long(bits));
        if out_len < required {
            return FERRUM_ERR_BUFFER_TOO_SMALL;
        }

        if value_count == 0 {
            return FERRUM_OK;
        }

        if values.is_null() || out_data.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let input = unsafe { std::slice::from_raw_parts(values, value_count) };
        let output = unsafe { std::slice::from_raw_parts_mut(out_data, required) };
        pack::pack(input, bits, output)
    })
}

/// Fused `unpack -> map -> pack` remap, used only by the F-055 spike harness.
///
/// This is a **test/benchmark-only** entry point. It is compiled only with the `test-hooks`
/// feature, is absent from the public ABI header, and is never advertised through feature bits or
/// used by production code.
///
/// # Safety
///
/// `in_data` must be valid for `in_len` `u64` values, `map` for `map_len` `u32` values, `out_data`
/// for `out_len` `u64` values, and `written_or_required` for one `usize` when non-null.
#[cfg(feature = "test-hooks")]
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_test_palette_remap(
    in_data: *const u64,
    in_len: usize,
    bits_in: u32,
    value_count: usize,
    map: *const u32,
    map_len: usize,
    bits_out: u32,
    out_data: *mut u64,
    out_len: usize,
    written_or_required: *mut usize,
) -> i32 {
    guard(|| {
        if !layout::is_valid_bits(bits_in) || !layout::is_valid_bits(bits_out) {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let required_in = value_count.div_ceil(layout::values_per_long(bits_in));
        let required_out = value_count.div_ceil(layout::values_per_long(bits_out));
        if in_len < required_in {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        if out_len < required_out {
            if !written_or_required.is_null() {
                unsafe { *written_or_required = required_out };
            }
            return FERRUM_ERR_BUFFER_TOO_SMALL;
        }

        if value_count == 0 {
            if !written_or_required.is_null() {
                unsafe { *written_or_required = 0 };
            }
            return FERRUM_OK;
        }

        if in_data.is_null() || out_data.is_null() || (map_len > 0 && map.is_null()) {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let input = unsafe { std::slice::from_raw_parts(in_data, required_in) };
        let table = unsafe { std::slice::from_raw_parts(map, map_len) };
        let output = unsafe { std::slice::from_raw_parts_mut(out_data, required_out) };
        let status = remap::remap_fused(input, bits_in, value_count, table, bits_out, output);
        if status == FERRUM_OK && !written_or_required.is_null() {
            unsafe { *written_or_required = required_out };
        }
        status
    })
}
