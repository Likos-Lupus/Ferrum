#![deny(unsafe_op_in_unsafe_fn)]

pub mod abi;
mod core;

pub mod codec;
pub mod collide;
pub mod light;
pub mod nbt;
pub mod noise;
pub mod palette;
pub mod path;

pub use abi::{FerrumBuildInfo, FerrumLimits};
pub use core::{
    ferrum_abi_version, ferrum_build_info, ferrum_feature_bits, ferrum_selftest_checksum,
};
