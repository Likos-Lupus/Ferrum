//! Test-only native entry points.
//!
//! These are compiled only with the `test-hooks` feature and exist so Java-side tests can exercise
//! the PANIC status mapping and the circuit breaker with a real native failure.

#[cfg(feature = "test-hooks")]
use crate::guard::guard;

/// Always panics inside the guard, surfacing as [`FERRUM_ERR_PANIC`](crate::abi::FERRUM_ERR_PANIC).
#[cfg(feature = "test-hooks")]
#[unsafe(no_mangle)]
pub extern "C" fn ferrum_selftest_panic() -> i32 {
    guard(|| panic!("ferrum test-hooks: intentional panic"))
}
