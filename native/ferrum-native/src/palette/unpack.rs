//! `SimpleBitStorage` unpack: packed `u64` words to one `u32` per value.

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use crate::palette::layout;

/// Unpacks `value_count` values from `data` into `out`.
///
/// `data` must contain at least `ceil(value_count / values_per_long(bits))` words and `out` at
/// least `value_count` slots; the caller contract is checked before any read or write.
///
/// # Returns
///
/// [`FERRUM_OK`] on success, [`FERRUM_ERR_INVALID_ARGUMENT`] for an invalid width or a short
/// input, and [`FERRUM_ERR_BUFFER_TOO_SMALL`] when `out` is too small.
pub fn unpack(data: &[u64], bits: u32, value_count: usize, out: &mut [u32]) -> i32 {
    if !layout::is_valid_bits(bits) {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    
    let per_long = layout::values_per_long(bits);
    let required = value_count.div_ceil(per_long);
    if data.len() < required {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    
    if out.len() < value_count {
        return FERRUM_ERR_BUFFER_TOO_SMALL;
    }

    let mask = layout::mask(bits);
    let mut index = 0usize;
    for &word in &data[..required] {
        let mut remaining = word;
        let in_word = (value_count - index).min(per_long);
        for slot in &mut out[index..index + in_word] {
            *slot = (remaining & mask) as u32;
            remaining >>= bits;
        }
        index += in_word;
        if index == value_count {
            break;
        }
    }

    FERRUM_OK
}
