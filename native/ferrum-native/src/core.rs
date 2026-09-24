use std::mem::size_of;
use std::ptr;

use crate::abi::{
    FERRUM_ABI_VERSION, FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK,
    FerrumBuildInfo,
};
use crate::guard::guard;

/// Feature bits advertised by this build.
///
/// A bit is set only once the corresponding module is actually implemented and passes its
/// correctness gates. Until then the symbols exist as stubs returning
/// [`FERRUM_ERR_UNSUPPORTED`](crate::abi::FERRUM_ERR_UNSUPPORTED) and no bit is advertised.
pub const FERRUM_FEATURE_BITS: u64 = 0;

fn git_commit() -> [u8; 20] {
    let mut commit = [0u8; 20];
    let Some(hex) = option_env!("FERRUM_GIT_COMMIT") else {
        return commit;
    };
    let bytes = hex.as_bytes();
    if bytes.len() != 40 {
        return commit;
    }
    for (index, slot) in commit.iter_mut().enumerate() {
        let (Some(high), Some(low)) = (hex_val(bytes[index * 2]), hex_val(bytes[index * 2 + 1]))
        else {
            return [0u8; 20];
        };
        *slot = (high << 4) | low;
    }
    commit
}

fn hex_val(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        b'A'..=b'F' => Some(byte - b'A' + 10),
        _ => None,
    }
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
            git_commit: git_commit(),
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
