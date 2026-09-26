//! FerrumCollide native entry points: the batch AABB clip and the batched voxel-shape sweep
//! (ADR-0019).
//!
//! Both kernels are stateless. One call parses a self-describing snapshot blob, runs the vanilla
//! algorithm, and writes a compact result blob. Any unsupported, malformed, or over-limit input is
//! rejected before the kernel produces a result; the Java side then runs the vanilla path.

mod aabb_clip;
mod blob;
mod sweep;

use core::slice;

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use crate::guard::guard;

/// Runs one batch AABB ray clip.
///
/// `in` is an `FBCA` versioned blob; `out` receives an `FBCO` blob. On
/// [`FERRUM_ERR_BUFFER_TOO_SMALL`](crate::abi::FERRUM_ERR_BUFFER_TOO_SMALL) `out_written` receives
/// the required size so the caller can reallocate and retry once.
///
/// # Safety
///
/// `in` must be valid for `in_len` bytes (or null when `in_len` is zero), `out` must be valid for
/// `out_cap` bytes (or null when `out_cap` is zero), and `out_written` must be valid for one write.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_collide_aabb_clip(
    input: *const u8,
    input_len: usize,
    output: *mut u8,
    output_cap: usize,
    out_written: *mut usize,
) -> i32 {
    guard(|| {
        let bytes = match unsafe { read_input(input, input_len) } {
            Ok(bytes) => bytes,
            Err(status) => return status,
        };
        if out_written.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        let parsed = match blob::parse_clip(bytes) {
            Ok(parsed) => parsed,
            Err(status) => return status,
        };

        let required = blob::clip_output_size();
        if output_cap < required {
            // SAFETY: `out_written` is non-null and valid for one write.
            unsafe { out_written.write(required) };
            return FERRUM_ERR_BUFFER_TOO_SMALL;
        }
        if output.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let result = blob::clip(&parsed);
        // SAFETY: `output` is valid for `output_cap` bytes and `output_cap >= required`.
        let out = unsafe { slice::from_raw_parts_mut(output, output_cap) };
        let written = blob::write_clip(&result, out);
        // SAFETY: `out_written` is non-null and valid for one write.
        unsafe { out_written.write(written) };
        FERRUM_OK
    })
}

/// Runs one batched voxel-shape sweep.
///
/// `in` is an `FBCS` versioned blob; `out` receives an `FBCT` blob. On
/// [`FERRUM_ERR_BUFFER_TOO_SMALL`](crate::abi::FERRUM_ERR_BUFFER_TOO_SMALL) `out_written` receives
/// the required size so the caller can reallocate and retry once.
///
/// # Safety
///
/// `in` must be valid for `in_len` bytes (or null when `in_len` is zero), `out` must be valid for
/// `out_cap` bytes (or null when `out_cap` is zero), and `out_written` must be valid for one write.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_collide_sweep(
    input: *const u8,
    input_len: usize,
    output: *mut u8,
    output_cap: usize,
    out_written: *mut usize,
) -> i32 {
    guard(|| {
        let bytes = match unsafe { read_input(input, input_len) } {
            Ok(bytes) => bytes,
            Err(status) => return status,
        };
        if out_written.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        let parsed = match blob::parse_sweep(bytes) {
            Ok(parsed) => parsed,
            Err(status) => return status,
        };

        let required = blob::sweep_output_size();
        if output_cap < required {
            // SAFETY: `out_written` is non-null and valid for one write.
            unsafe { out_written.write(required) };
            return FERRUM_ERR_BUFFER_TOO_SMALL;
        }
        if output.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let resolved = sweep::sweep(
            &parsed.moving,
            &parsed.movement,
            &parsed.axis_order,
            &parsed.shapes,
        );
        // SAFETY: `output` is valid for `output_cap` bytes and `output_cap >= required`.
        let out = unsafe { slice::from_raw_parts_mut(output, output_cap) };
        let written = blob::write_sweep(&resolved, out);
        // SAFETY: `out_written` is non-null and valid for one write.
        unsafe { out_written.write(written) };
        FERRUM_OK
    })
}

/// Validates the input pointer/length pair and returns the borrow.
///
/// # Safety
///
/// `input` must be valid for `input_len` bytes when non-null.
unsafe fn read_input<'a>(input: *const u8, input_len: usize) -> Result<&'a [u8], i32> {
    if input.is_null() {
        return if input_len == 0 {
            Ok(&[])
        } else {
            Err(FERRUM_ERR_INVALID_ARGUMENT)
        };
    }
    // SAFETY: the caller guarantees `input` is valid for `input_len` bytes.
    Ok(unsafe { slice::from_raw_parts(input, input_len) })
}
