#![no_main]

use libfuzzer_sys::fuzz_target;

use ferrum::noise::{ferrum_noise_create, ferrum_noise_destroy};

// Arbitrary descriptor bytes must never panic; a successfully created field is released again.
fuzz_target!(|data: &[u8]| {
    let mut handle = 0u64;
    // SAFETY: `data` is a valid slice and `handle` is valid for one write.
    let status = unsafe { ferrum_noise_create(data.as_ptr(), data.len(), &mut handle) };
    if status == 0 {
        let _ = ferrum_noise_destroy(handle);
    }
});
