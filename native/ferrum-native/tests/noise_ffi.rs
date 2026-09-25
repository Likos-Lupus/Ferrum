//! FFI lifecycle tests for the noise handle API.

mod noise_common;

use noise_common::{ImprovedSpec, PerlinSpec, improved_descriptor, perlin_descriptor};

use ferrum::noise::{ferrum_noise_batch, ferrum_noise_create, ferrum_noise_destroy, live_handles};

fn create(descriptor: &[u8]) -> u64 {
    let mut handle = 0u64;
    // SAFETY: `descriptor` is a valid slice and `handle` is valid for one write.
    let status = unsafe { ferrum_noise_create(descriptor.as_ptr(), descriptor.len(), &mut handle) };
    assert_eq!(status, 0, "create failed");
    assert_ne!(handle, 0);
    handle
}

fn perlin() -> Vec<u8> {
    perlin_descriptor(&PerlinSpec {
        first_octave: 0,
        lowest_freq_input_factor: 1.0,
        lowest_freq_value_factor: 1.0,
        amplitudes: vec![1.0],
        levels: vec![Some(ImprovedSpec::identity())],
    })
}

#[test]
fn create_batch_destroy_round_trip() {
    let descriptor = perlin();
    let handle = create(&descriptor);

    let xs = [0.0, 1.0, -1.0, 12.5];
    let ys = [0.0, 0.5, 2.0, -3.25];
    let zs = [0.0, -0.5, 3.0, 0.125];
    let mut out = [0.0f64; 4];
    // SAFETY: all pointers are valid for `4` elements.
    let status = unsafe {
        ferrum_noise_batch(
            handle,
            xs.as_ptr(),
            ys.as_ptr(),
            zs.as_ptr(),
            out.as_mut_ptr(),
            4,
            0,
        )
    };
    assert_eq!(status, 0);
    assert!(out.iter().all(|value| value.is_finite()));

    assert_eq!(ferrum_noise_destroy(handle), 0);
    assert_eq!(ferrum_noise_destroy(handle), -1, "second destroy must fail");
}

#[test]
fn zero_amplitude_perlin_evaluates_to_zero() {
    let descriptor = perlin_descriptor(&PerlinSpec {
        first_octave: 0,
        lowest_freq_input_factor: 1.0,
        lowest_freq_value_factor: 1.0,
        amplitudes: vec![0.0, 0.0],
        levels: vec![None, None],
    });
    let handle = create(&descriptor);

    let xs = [1.0, 2.0];
    let ys = [3.0, 4.0];
    let zs = [5.0, 6.0];
    let mut out = [1.0f64; 2];
    // SAFETY: all pointers are valid for `2` elements.
    let status = unsafe {
        ferrum_noise_batch(
            handle,
            xs.as_ptr(),
            ys.as_ptr(),
            zs.as_ptr(),
            out.as_mut_ptr(),
            2,
            0,
        )
    };
    assert_eq!(status, 0);
    assert_eq!(out, [0.0, 0.0]);

    assert_eq!(ferrum_noise_destroy(handle), 0);
}

#[test]
fn improved_descriptor_is_accepted() {
    let descriptor = improved_descriptor(&ImprovedSpec::identity());
    let handle = create(&descriptor);
    assert_eq!(ferrum_noise_destroy(handle), 0);
}

#[test]
fn unknown_handle_and_flags_are_rejected() {
    let descriptor = perlin();
    let handle = create(&descriptor);

    let xs = [0.0];
    let ys = [0.0];
    let zs = [0.0];
    let mut out = [0.0f64; 1];
    // SAFETY: all pointers are valid for `1` element.
    let flags_status = unsafe {
        ferrum_noise_batch(
            handle,
            xs.as_ptr(),
            ys.as_ptr(),
            zs.as_ptr(),
            out.as_mut_ptr(),
            1,
            1,
        )
    };
    assert_eq!(flags_status, -1);

    // SAFETY: all pointers are valid for `1` element.
    let unknown_status = unsafe {
        ferrum_noise_batch(
            handle + 1,
            xs.as_ptr(),
            ys.as_ptr(),
            zs.as_ptr(),
            out.as_mut_ptr(),
            1,
            0,
        )
    };
    assert_eq!(unknown_status, -1);

    assert_eq!(ferrum_noise_destroy(handle), 0);
}

#[test]
fn create_rejects_null_output() {
    let descriptor = perlin();
    // SAFETY: passing a null output pointer is the case under test.
    let status =
        unsafe { ferrum_noise_create(descriptor.as_ptr(), descriptor.len(), std::ptr::null_mut()) };
    assert_eq!(status, -1);
}

#[test]
fn live_handles_tracks_create_and_destroy() {
    let before = live_handles();
    let descriptor = perlin();
    let handle = create(&descriptor);
    assert_eq!(live_handles(), before + 1);
    assert_eq!(ferrum_noise_destroy(handle), 0);
    assert_eq!(live_handles(), before);
}
