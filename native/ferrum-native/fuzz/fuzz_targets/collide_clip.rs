#![no_main]

use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let mut output = vec![0u8; 64];
    let mut written = 0usize;
    unsafe {
        ferrum::collide::ferrum_collide_aabb_clip(
            data.as_ptr(),
            data.len(),
            output.as_mut_ptr(),
            output.len(),
            &mut written,
        );
    }
});
