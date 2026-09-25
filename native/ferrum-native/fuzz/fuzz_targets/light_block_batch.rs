#![no_main]

use libfuzzer_sys::fuzz_target;

use ferrum::light::ferrum_light_block_batch;

// Arbitrary input must never panic or read out of bounds; a well-formed batch may be accepted and
// must produce a consistent output blob.
fuzz_target!(|data: &[u8]| {
    let mut out = vec![0u8; 1 << 16];
    let mut written = 0usize;
    // SAFETY: `data` and `out` are valid slices; `written` is valid for one write.
    let _ = unsafe {
        ferrum_light_block_batch(
            data.as_ptr(),
            data.len(),
            out.as_mut_ptr(),
            out.len(),
            &mut written,
        )
    };
});
