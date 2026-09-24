use std::mem::{offset_of, size_of};

use ferrum::{FerrumBuildInfo, FerrumLimits};

#[test]
fn build_info_layout_is_frozen() {
    assert_eq!(size_of::<FerrumBuildInfo>(), 64);
    assert_eq!(offset_of!(FerrumBuildInfo, struct_size), 0);
    assert_eq!(offset_of!(FerrumBuildInfo, abi_version), 4);
    assert_eq!(offset_of!(FerrumBuildInfo, feature_bits), 8);
    assert_eq!(offset_of!(FerrumBuildInfo, git_commit), 16);
    assert_eq!(offset_of!(FerrumBuildInfo, reserved), 36);
}

#[test]
fn limits_layout_is_frozen() {
    assert_eq!(size_of::<FerrumLimits>(), 24);
    assert_eq!(offset_of!(FerrumLimits, max_total_bytes), 0);
    assert_eq!(offset_of!(FerrumLimits, max_depth), 8);
    assert_eq!(offset_of!(FerrumLimits, max_nodes), 12);
    assert_eq!(offset_of!(FerrumLimits, max_array_length), 16);
    assert_eq!(offset_of!(FerrumLimits, max_string_encoded_bytes), 20);
}
