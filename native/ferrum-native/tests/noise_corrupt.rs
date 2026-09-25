//! Malformed-descriptor tests: corrupted input must be reported, never panic or read out of bounds.

mod noise_common;

use noise_common::{KIND_PERLIN, header, improved_descriptor, perlin_descriptor};

use ferrum::noise::ferrum_noise_create;

fn status(descriptor: &[u8]) -> i32 {
    let mut handle = 0u64;
    // SAFETY: `descriptor` is a valid slice and `handle` is valid for one write.
    unsafe { ferrum_noise_create(descriptor.as_ptr(), descriptor.len(), &mut handle) }
}

#[test]
fn empty_descriptor_is_malformed() {
    assert_eq!(status(&[]), -3);
}

#[test]
fn bad_magic_is_malformed() {
    let mut descriptor = perlin_descriptor(&noise_common::PerlinSpec {
        first_octave: 0,
        lowest_freq_input_factor: 1.0,
        lowest_freq_value_factor: 1.0,
        amplitudes: vec![],
        levels: vec![],
    });
    descriptor[0] = b'X';
    assert_eq!(status(&descriptor), -3);
}

#[test]
fn bad_version_is_malformed() {
    let mut descriptor = header(KIND_PERLIN);
    descriptor[4] = 9;
    assert_eq!(status(&descriptor), -3);
}

#[test]
fn bad_kind_is_malformed() {
    let descriptor = header(99);
    assert_eq!(status(&descriptor), -3);
}

#[test]
fn truncated_improved_is_malformed() {
    let mut descriptor = improved_descriptor(&noise_common::ImprovedSpec::identity());
    descriptor.truncate(descriptor.len() - 1);
    assert_eq!(status(&descriptor), -3);
}

#[test]
fn too_many_levels_is_a_limit_error() {
    let mut descriptor = header(KIND_PERLIN);
    descriptor.extend_from_slice(&0i32.to_le_bytes());
    descriptor.extend_from_slice(&1.0f64.to_le_bytes());
    descriptor.extend_from_slice(&1.0f64.to_le_bytes());
    descriptor.extend_from_slice(&257u32.to_le_bytes());
    assert_eq!(status(&descriptor), -4);
}

#[test]
fn null_descriptor_with_length_is_invalid_argument() {
    let mut handle = 0u64;
    // SAFETY: a null descriptor with a non-zero length is the case under test.
    let code = unsafe { ferrum_noise_create(std::ptr::null(), 4, &mut handle) };
    assert_eq!(code, -1);
}
