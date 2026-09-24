use std::mem::size_of;

use ferrum::{
    FerrumBuildInfo, ferrum_abi_version, ferrum_build_info, ferrum_feature_bits,
    ferrum_selftest_checksum,
};

#[test]
fn abi_version_is_one() {
    assert_eq!(ferrum_abi_version(), 1);
}

#[test]
fn build_info_roundtrip() {
    let mut info = FerrumBuildInfo::default();
    let status = unsafe { ferrum_build_info(&mut info, size_of::<FerrumBuildInfo>()) };
    assert_eq!(status, 0);
    assert_eq!(info.abi_version, 1);
    assert_eq!(info.struct_size as usize, size_of::<FerrumBuildInfo>());
    assert_eq!(info.feature_bits, ferrum_feature_bits());
}

#[test]
fn build_info_rejects_small_buffer() {
    let mut info = FerrumBuildInfo::default();
    assert_ne!(unsafe { ferrum_build_info(&mut info, 1) }, 0);
}

#[test]
fn selftest_is_deterministic() {
    let mut first = 0u64;
    let mut second = 0u64;
    assert_eq!(unsafe { ferrum_selftest_checksum(42, &mut first) }, 0);
    assert_eq!(unsafe { ferrum_selftest_checksum(42, &mut second) }, 0);
    assert_eq!(first, second);
}

#[cfg(feature = "test-hooks")]
#[test]
fn panic_is_converted_to_panic_status() {
    assert_eq!(ferrum::testhooks::ferrum_selftest_panic(), -127);
}
