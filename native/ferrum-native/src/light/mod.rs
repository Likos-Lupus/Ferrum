//! FerrumLight native entry point: the block-light batch (ADR-0018).
//!
//! The kernel is stateless. One call parses a self-describing snapshot blob, runs the vanilla
//! two-phase block-light BFS over the flattened sections, and writes the changed sections back as a
//! blob. Any unsupported, malformed, or over-limit input is rejected before the kernel mutates
//! anything; the Java side then runs the vanilla batch.

mod blob;
mod engine;

use core::slice;

use crate::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use crate::guard::guard;

/// Runs one block-light batch.
///
/// `in` is an `FBLT` versioned blob; `out` receives an `FBLO` blob. On
/// [`FERRUM_ERR_BUFFER_TOO_SMALL`](crate::abi::FERRUM_ERR_BUFFER_TOO_SMALL) `out_written` receives
/// the required size so the caller can reallocate and retry once.
///
/// # Safety
///
/// `in` must be valid for `in_len` bytes (or null when `in_len` is zero), `out` must be valid for
/// `out_cap` bytes (or null when `out_cap` is zero), and `out_written` must be valid for one write.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_light_block_batch(
    input: *const u8,
    input_len: usize,
    output: *mut u8,
    output_cap: usize,
    out_written: *mut usize,
) -> i32 {
    guard(|| {
        if out_written.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if input.is_null() && input_len != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if output.is_null() && output_cap != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        // SAFETY: the caller guarantees `input` is valid for `input_len` bytes.
        let bytes: &[u8] = if input_len == 0 {
            &[]
        } else {
            unsafe { slice::from_raw_parts(input, input_len) }
        };

        let mut batch = match blob::parse(bytes) {
            Ok(batch) => batch,
            Err(status) => return status,
        };

        let processed = batch.run();
        let required = blob::required_output_size(&batch);
        if output_cap < required {
            // SAFETY: `out_written` is non-null and valid for one write.
            unsafe { out_written.write(required) };
            return FERRUM_ERR_BUFFER_TOO_SMALL;
        }

        // SAFETY: `output` is valid for `output_cap` bytes and `output_cap >= required`.
        let out = unsafe { slice::from_raw_parts_mut(output, output_cap) };
        let written = blob::write_output(&batch, processed.min(u64::from(u32::MAX)) as u32, out);
        // SAFETY: `out_written` is non-null and valid for one write.
        unsafe { out_written.write(written) };
        FERRUM_OK
    })
}
