use std::mem::size_of;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::ptr;

use crate::abi::{
    FERRUM_ABI_VERSION, FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_ERR_PANIC,
    FERRUM_FEATURE_CODEC_LZ4, FERRUM_FEATURE_COLLIDE, FERRUM_FEATURE_LIGHT, FERRUM_FEATURE_NBT,
    FERRUM_FEATURE_NOISE, FERRUM_FEATURE_PALETTE, FERRUM_FEATURE_PATH, FERRUM_OK, FerrumBuildInfo,
};

pub const FERRUM_FEATURE_BITS: u64 = FERRUM_FEATURE_NBT
    | FERRUM_FEATURE_CODEC_LZ4
    | FERRUM_FEATURE_PALETTE
    | FERRUM_FEATURE_NOISE
    | FERRUM_FEATURE_LIGHT
    | FERRUM_FEATURE_COLLIDE
    | FERRUM_FEATURE_PATH;

fn guard<F: FnOnce() -> i32>(action: F) -> i32 {
    catch_unwind(AssertUnwindSafe(action)).unwrap_or(FERRUM_ERR_PANIC)
}

#[unsafe(no_mangle)]
pub extern "C" fn ferrum_abi_version() -> u32 {
    FERRUM_ABI_VERSION
}

#[unsafe(no_mangle)]
pub extern "C" fn ferrum_feature_bits() -> u64 {
    FERRUM_FEATURE_BITS
}

/// Writes build information into the caller-provided buffer.
///
/// # Safety
///
/// `out` must be valid for writing a `FerrumBuildInfo` and `out_size` must describe that capacity.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_build_info(out: *mut FerrumBuildInfo, out_size: usize) -> i32 {
    guard(|| {
        if out.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        if out_size < size_of::<FerrumBuildInfo>() {
            return FERRUM_ERR_BUFFER_TOO_SMALL;
        }

        let info = FerrumBuildInfo {
            struct_size: size_of::<FerrumBuildInfo>() as u32,
            abi_version: FERRUM_ABI_VERSION,
            feature_bits: FERRUM_FEATURE_BITS,
            git_commit: [0u8; 20],
            reserved: [0u8; 28],
        };

        unsafe {
            ptr::write(out, info);
        }

        FERRUM_OK
    })
}

/// Writes a deterministic selftest checksum into the caller-provided location.
///
/// # Safety
///
/// `output` must be valid for writing a single `u64`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_selftest_checksum(input: u64, output: *mut u64) -> i32 {
    guard(|| {
        if output.is_null() {
            return FERRUM_ERR_INVALID_ARGUMENT;
        }

        let value =
            input.wrapping_mul(0x9E37_79B9_7F4A_7C15).rotate_left(31) ^ 0xC2B2_AE3D_27D4_EB4F;
        unsafe {
            ptr::write(output, value);
        }
        FERRUM_OK
    })
}
