#![deny(unsafe_op_in_unsafe_fn)]

pub mod abi;
pub mod arith;
mod core;
pub mod guard;
pub mod handle;
pub mod testhooks;

pub mod codec;
pub mod collide;
pub mod light;
pub mod nbt;
pub mod noise;
pub mod palette;
pub mod path;

pub use abi::{FERRUM_HANDLE_NULL, FerrumBuildInfo, FerrumHandle, FerrumLimits};
pub use core::{
    ferrum_abi_version, ferrum_build_info, ferrum_feature_bits, ferrum_selftest_checksum,
};
