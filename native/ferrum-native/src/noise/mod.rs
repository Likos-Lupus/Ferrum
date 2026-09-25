//! FerrumNoise native entry points.
//!
//! The native kernel evaluates immutable noise fields that the Java adapter serializes from the live
//! vanilla `NormalNoise` / `PerlinNoise` / `ImprovedNoise` objects. A field is created once per
//! world/resource generation, referenced many times through an opaque handle, and destroyed when the
//! generation is replaced. Evaluation is bit-exact with vanilla (ADR-0006).

mod descriptor;
mod improved;
mod math;
mod normal;
mod perlin;

use core::slice;
use std::sync::{Arc, LazyLock};

use crate::abi::{FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK, FerrumHandle};
use crate::guard::guard;
use crate::handle::HandleRegistry;

use descriptor::NoiseField;

/// The process-wide registry of live noise fields.
static REGISTRY: LazyLock<HandleRegistry<Arc<NoiseField>>> = LazyLock::new(HandleRegistry::new);

/// Creates a noise field from a descriptor and writes its handle to `out_handle`.
///
/// # Safety
///
/// `descriptor` must be valid for `descriptor_len` bytes (or null when `descriptor_len` is zero),
/// and `out_handle` must be valid for writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_noise_create(
    descriptor: *const u8,
    descriptor_len: usize,
    out_handle: *mut FerrumHandle,
) -> i32 {
    guard(|| {
        if out_handle.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if descriptor.is_null() && descriptor_len != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        // SAFETY: the caller guarantees `descriptor` is valid for `descriptor_len` bytes.
        let bytes: &[u8] = if descriptor_len == 0 {
            &[]
        } else {
            unsafe { slice::from_raw_parts(descriptor, descriptor_len) }
        };

        match descriptor::parse(bytes) {
            Ok(field) => {
                let handle = REGISTRY.create(Arc::new(field));
                // SAFETY: `out_handle` is non-null and valid for one write.
                unsafe { out_handle.write(handle) };
                FERRUM_OK
            }
            Err(status) => status,
        }
    })
}

/// Evaluates a noise field at `sample_count` coordinates.
///
/// `flags` is reserved and must be zero.
///
/// # Safety
///
/// `xs`, `ys`, and `zs` must each be valid for `sample_count` elements, and `out_values` must be
/// valid for `sample_count` elements.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_noise_batch(
    noise_handle: FerrumHandle,
    xs: *const f64,
    ys: *const f64,
    zs: *const f64,
    out_values: *mut f64,
    sample_count: usize,
    flags: u32,
) -> i32 {
    guard(|| {
        if flags != 0 {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }
        if sample_count == 0 {
            return FERRUM_OK;
        }
        if xs.is_null() || ys.is_null() || zs.is_null() || out_values.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        // SAFETY: the caller guarantees each pointer is valid for `sample_count` elements.
        let xs = unsafe { slice::from_raw_parts(xs, sample_count) };
        // SAFETY: see above.
        let ys = unsafe { slice::from_raw_parts(ys, sample_count) };
        // SAFETY: see above.
        let zs = unsafe { slice::from_raw_parts(zs, sample_count) };
        // SAFETY: see above.
        let out = unsafe { slice::from_raw_parts_mut(out_values, sample_count) };

        // The lock is held only to clone the `Arc`; evaluation happens after it is released so
        // parallel chunk workers do not serialize on the registry.
        let field = REGISTRY.with(noise_handle, Arc::clone);
        match field {
            Some(field) => {
                for index in 0..sample_count {
                    out[index] = field.value(xs[index], ys[index], zs[index]);
                }
                FERRUM_OK
            }
            None => FERRUM_ERR_INVALID_ARGUMENT,
        }
    })
}

/// Destroys a noise handle.
///
/// Returns [`FERRUM_ERR_INVALID_ARGUMENT`](crate::abi::FERRUM_ERR_INVALID_ARGUMENT) when the handle
/// is unknown, stale, or foreign.
#[unsafe(no_mangle)]
pub extern "C" fn ferrum_noise_destroy(noise_handle: FerrumHandle) -> i32 {
    guard(|| {
        if REGISTRY.destroy(noise_handle) {
            FERRUM_OK
        } else {
            FERRUM_ERR_INVALID_ARGUMENT
        }
    })
}

/// Returns the number of live noise handles (diagnostics and leak tests).
pub fn live_handles() -> usize {
    REGISTRY.len()
}
