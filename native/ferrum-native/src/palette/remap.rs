//! Fused `unpack -> map -> pack` palette remap.
//!
//! The composed alternative is `unpack` into an `int[]`, map in Java, then `pack`; that
//! materializes the whole intermediate value array and pays two FFM crossings. This kernel keeps
//! one source cursor and one target accumulator so no intermediate buffer exists.

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use crate::palette::layout;

/// Remaps `value_count` packed values from `bits_in` to `bits_out` using `map`.
///
/// Each source value `old` is replaced by `map[old]`, which must fit the `bits_out` width; a value
/// whose `old` is outside `map` or whose mapped result overflows `bits_out` is rejected with
/// [`FERRUM_ERR_INVALID_ARGUMENT`].
///
/// # Returns
///
/// [`FERRUM_OK`] on success, [`FERRUM_ERR_INVALID_ARGUMENT`] for an invalid width, a short input,
/// an unmapped value, or a mapped value that does not fit, and [`FERRUM_ERR_BUFFER_TOO_SMALL`]
/// when `out` is too small.
pub fn remap_fused(
    data: &[u64],
    bits_in: u32,
    value_count: usize,
    map: &[u32],
    bits_out: u32,
    out: &mut [u64],
) -> i32 {
    if !layout::is_valid_bits(bits_in) || !layout::is_valid_bits(bits_out) {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }

    let per_in = layout::values_per_long(bits_in);
    let per_out = layout::values_per_long(bits_out);
    let required_in = value_count.div_ceil(per_in);
    let required_out = value_count.div_ceil(per_out);
    
    if data.len() < required_in {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    
    if out.len() < required_out {
        return FERRUM_ERR_BUFFER_TOO_SMALL;
    }
    
    if value_count == 0 {
        return FERRUM_OK;
    }

    let mask_in = layout::mask(bits_in);
    let mask_out = layout::mask(bits_out);

    let mut source = data[0];
    let mut source_word = 0usize;
    let mut source_slot = 0usize;

    let mut target = 0u64;
    let mut target_word = 0usize;
    let mut target_slot = 0usize;

    for _ in 0..value_count {
        let old = (source & mask_in) as usize;
        let mapped = match map.get(old) {
            Some(value) => u64::from(*value),
            None => return FERRUM_ERR_INVALID_ARGUMENT,
        };
        
        if mapped & !mask_out != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        source >>= bits_in;
        source_slot += 1;
        if source_slot == per_in {
            source_word += 1;
            source_slot = 0;
            if source_word < required_in {
                source = data[source_word];
            }
        }

        target |= mapped << (target_slot * bits_out as usize);
        target_slot += 1;
        if target_slot == per_out {
            out[target_word] = target;
            target_word += 1;
            target_slot = 0;
            target = 0;
        }
    }

    if target_slot > 0 {
        out[target_word] = target;
    }

    FERRUM_OK
}
