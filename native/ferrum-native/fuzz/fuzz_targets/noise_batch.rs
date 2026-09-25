#![no_main]

use libfuzzer_sys::fuzz_target;

use ferrum::noise::{ferrum_noise_batch, ferrum_noise_create, ferrum_noise_destroy};

// A field that parsed is evaluated at a handful of coordinates derived from the same bytes; the
// batch must never panic or read out of bounds.
fuzz_target!(|data: &[u8]| {
    let mut handle = 0u64;
    // SAFETY: `data` is a valid slice and `handle` is valid for one write.
    let status = unsafe { ferrum_noise_create(data.as_ptr(), data.len(), &mut handle) };
    if status != 0 {
        return;
    }

    let sample_count = (data.len() % 16) + 1;
    let xs: Vec<f64> = (0..sample_count)
        .map(|index| f64::from(index as i32) * 0.25 - 2.0)
        .collect();
    let ys: Vec<f64> = (0..sample_count)
        .map(|index| f64::from(index as i32) * -0.5)
        .collect();
    let zs: Vec<f64> = (0..sample_count)
        .map(|index| f64::from(index as i32) + 0.125)
        .collect();
    let mut out = vec![0f64; sample_count];
    // SAFETY: all pointers are valid for `sample_count` elements.
    let _ = unsafe {
        ferrum_noise_batch(
            handle,
            xs.as_ptr(),
            ys.as_ptr(),
            zs.as_ptr(),
            out.as_mut_ptr(),
            sample_count,
            0,
        )
    };
    let _ = ferrum_noise_destroy(handle);
});
