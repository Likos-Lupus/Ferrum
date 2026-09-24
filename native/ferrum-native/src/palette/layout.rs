//! The `SimpleBitStorage` bit layout (ADR-0015).
//!
//! Minecraft packs palette ids into `long[]` words with `valuesPerLong = 64 / bits` values per
//! word, least-significant value first. A value never crosses a word boundary, and when
//! `valuesPerLong * bits != 64` the unused high bits of each word stay zero. The helpers below are
//! the single source of truth for that layout so the unpack, pack, and fused remap kernels agree.

/// The smallest supported value width.
pub const MIN_BITS: u32 = 1;

/// The largest supported value width.
pub const MAX_BITS: u32 = 32;

/// Returns whether `bits` is a width the layout supports.
pub fn is_valid_bits(bits: u32) -> bool {
    (MIN_BITS..=MAX_BITS).contains(&bits)
}

/// Returns the number of values stored in one `u64` for `bits`.
///
/// # Panics
///
/// Panics when `bits` is not a valid width; call [`is_valid_bits`] first.
pub fn values_per_long(bits: u32) -> usize {
    assert!(is_valid_bits(bits), "invalid bits");
    (64 / bits) as usize
}

/// Returns the mask that isolates one value of `bits` width.
///
/// # Panics
///
/// Panics when `bits` is not a valid width; call [`is_valid_bits`] first.
pub fn mask(bits: u32) -> u64 {
    assert!(is_valid_bits(bits), "invalid bits");
    (1u64 << bits) - 1
}

/// Returns the number of `u64` words required to store `value_count` values of the given width, or
/// `None` when `bits` is invalid.
pub fn required_longs(value_count: usize, bits: u32) -> Option<usize> {
    if !is_valid_bits(bits) {
        return None;
    }
    Some(value_count.div_ceil(values_per_long(bits)))
}
