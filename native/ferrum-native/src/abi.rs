pub const FERRUM_ABI_VERSION: u32 = 1;

pub const FERRUM_OK: i32 = 0;
pub const FERRUM_ERR_INVALID_ARGUMENT: i32 = -1;
pub const FERRUM_ERR_BUFFER_TOO_SMALL: i32 = -2;
pub const FERRUM_ERR_MALFORMED_INPUT: i32 = -3;
pub const FERRUM_ERR_LIMIT_EXCEEDED: i32 = -4;
pub const FERRUM_ERR_UNSUPPORTED: i32 = -5;
pub const FERRUM_ERR_ABI_MISMATCH: i32 = -6;
pub const FERRUM_ERR_INTERNAL: i32 = -7;
pub const FERRUM_ERR_PANIC: i32 = -127;

pub const FERRUM_FEATURE_NBT: u64 = 1 << 0;
pub const FERRUM_FEATURE_CODEC_LZ4: u64 = 1 << 1;
pub const FERRUM_FEATURE_PALETTE: u64 = 1 << 2;
pub const FERRUM_FEATURE_NOISE: u64 = 1 << 3;
pub const FERRUM_FEATURE_LIGHT: u64 = 1 << 4;
pub const FERRUM_FEATURE_COLLIDE: u64 = 1 << 5;
pub const FERRUM_FEATURE_PATH: u64 = 1 << 6;

/// An opaque native handle.
///
/// Handles are produced by `create` style APIs, used across many calls, and released exactly once
/// by the matching `destroy` API. The value `0` is reserved to mean "no handle".
pub type FerrumHandle = u64;

/// The reserved null/invalid handle value.
pub const FERRUM_HANDLE_NULL: FerrumHandle = 0;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct FerrumBuildInfo {
    pub struct_size: u32,
    pub abi_version: u32,
    pub feature_bits: u64,
    pub git_commit: [u8; 20],
    pub reserved: [u8; 28],
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct FerrumLimits {
    pub max_total_bytes: u64,
    pub max_depth: u32,
    pub max_nodes: u32,
    pub max_array_length: u32,
    pub max_string_encoded_bytes: u32,
}
