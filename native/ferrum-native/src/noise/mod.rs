//! FerrumNoise native entry points.
//!
//! Not implemented yet: the exported symbols exist so the ABI surface is stable, but every call
//! returns [`FERRUM_ERR_UNSUPPORTED`](crate::abi::FERRUM_ERR_UNSUPPORTED).

use crate::abi::{FERRUM_ERR_UNSUPPORTED, FerrumHandle};
use crate::guard::guard;

/// Evaluates a noise field at the given sample coordinates.
///
/// # Safety
///
/// `_xs`, `_ys`, and `_zs` must be valid for `_sample_count` elements, and `_out_values` must be
/// valid for `_sample_count` elements.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_noise_batch(
    _noise_handle: FerrumHandle,
    _xs: *const f64,
    _ys: *const f64,
    _zs: *const f64,
    _out_values: *mut f64,
    _sample_count: usize,
    _flags: u32,
) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}

/// Destroys a noise handle.
#[unsafe(no_mangle)]
pub extern "C" fn ferrum_noise_destroy(_noise_handle: FerrumHandle) -> i32 {
    guard(|| FERRUM_ERR_UNSUPPORTED)
}
