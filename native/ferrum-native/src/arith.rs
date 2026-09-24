//! Checked arithmetic helpers for length and offset computations at the ABI boundary.
//!
//! Every length, multiplication, and offset derived from caller-provided values must go through
//! these helpers. Memory errors are never tolerated; they are reported as status codes.

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_LIMIT_EXCEEDED};

/// Multiplies two lengths, reporting [`FERRUM_ERR_LIMIT_EXCEEDED`] on overflow.
pub fn checked_mul(a: usize, b: usize) -> Result<usize, i32> {
    a.checked_mul(b).ok_or(FERRUM_ERR_LIMIT_EXCEEDED)
}

/// Adds two lengths, reporting [`FERRUM_ERR_LIMIT_EXCEEDED`] on overflow.
pub fn checked_add(a: usize, b: usize) -> Result<usize, i32> {
    a.checked_add(b).ok_or(FERRUM_ERR_LIMIT_EXCEEDED)
}

/// Verifies that `offset + len` stays within `capacity`.
///
/// Returns [`FERRUM_ERR_BUFFER_TOO_SMALL`] when the range does not fit and
/// [`FERRUM_ERR_LIMIT_EXCEEDED`] when the addition itself overflows.
pub fn checked_range(offset: usize, len: usize, capacity: usize) -> Result<(), i32> {
    match offset.checked_add(len) {
        Some(end) if end <= capacity => Ok(()),
        Some(_) => Err(FERRUM_ERR_BUFFER_TOO_SMALL),
        None => Err(FERRUM_ERR_LIMIT_EXCEEDED),
    }
}
