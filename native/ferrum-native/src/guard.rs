//! The single no-unwind guard used at the FFI boundary.
//!
//! A panic must never unwind across `extern "C"`. Every exported entry point wraps its body in
//! [`guard`], which converts a panic into [`FERRUM_ERR_PANIC`](crate::abi::FERRUM_ERR_PANIC).

use std::panic::{AssertUnwindSafe, catch_unwind};

use crate::abi::FERRUM_ERR_PANIC;

/// Runs `action`, converting any panic into [`FERRUM_ERR_PANIC`].
pub fn guard<F: FnOnce() -> i32>(action: F) -> i32 {
    catch_unwind(AssertUnwindSafe(action)).unwrap_or(FERRUM_ERR_PANIC)
}
