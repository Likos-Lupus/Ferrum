//! `SimpleBitStorage` pack: one `u32` per value to packed `u64` words.

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use crate::palette::layout;

/// Packs `values` into `out`.
///
/// `out` must hold at least `ceil(values.len() / values_per_long(bits))` words. Every value must
/// fit the `bits` width, matching `SimpleBitStorage.set`'s range check. Words are written whole, so
/// the unused high bits of the final word stay zero.
///
/// # Returns
///
/// [`FERRUM_OK`] on success, [`FERRUM_ERR_INVALID_ARGUMENT`] for an invalid width or an
/// out-of-range value, and [`FERRUM_ERR_BUFFER_TOO_SMALL`] when `out` is too small.
pub fn pack(values: &[u32], bits: u32, out: &mut [u64]) -> i32 {
    if !layout::is_valid_bits(bits) {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }

    let per_long = layout::values_per_long(bits);
    let required = values.len().div_ceil(per_long);
    if out.len() < required {
        return FERRUM_ERR_BUFFER_TOO_SMALL;
    }

    let mask = layout::mask(bits);
    let mut index = 0usize;
    for word in &mut out[..required] {
        let in_word = (values.len() - index).min(per_long);
        let mut packed = 0u64;
        for offset in 0..in_word {
            let value = u64::from(values[index + offset]);
            if value & !mask != 0 {
                return FERRUM_ERR_INVALID_ARGUMENT;
            }

            packed |= value << (offset * bits as usize);
        }

        *word = packed;
        index += in_word;
    }

    FERRUM_OK
}
