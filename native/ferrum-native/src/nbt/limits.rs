//! Parse limits derived from the ABI `FerrumLimits`.

use crate::abi::FerrumLimits;

/// Effective limits for a single parse.
#[derive(Clone, Copy, Debug)]
pub struct Limits {
    pub max_total_bytes: u64,
    pub max_depth: u32,
    pub max_nodes: u32,
    pub max_array_length: u32,
    pub max_string_encoded_bytes: u32,
}

impl Limits {
    /// Builds limits from the ABI struct.
    pub fn from_abi(abi: &FerrumLimits) -> Self {
        Self {
            max_total_bytes: abi.max_total_bytes,
            max_depth: abi.max_depth,
            max_nodes: abi.max_nodes,
            max_array_length: abi.max_array_length,
            max_string_encoded_bytes: abi.max_string_encoded_bytes,
        }
    }
}

impl Default for Limits {
    fn default() -> Self {
        Self {
            max_total_bytes: u64::MAX,
            max_depth: 512,
            max_nodes: u32::MAX,
            max_array_length: u32::MAX,
            max_string_encoded_bytes: 0xFFFF,
        }
    }
}
