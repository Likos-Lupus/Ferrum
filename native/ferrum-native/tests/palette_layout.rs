use ferrum::palette::layout;

#[test]
fn valid_bits_is_one_through_thirty_two() {
    assert!(!layout::is_valid_bits(0));
    for bits in 1..=32 {
        assert!(layout::is_valid_bits(bits), "bits {bits} should be valid");
    }
    assert!(!layout::is_valid_bits(33));
    assert!(!layout::is_valid_bits(u32::MAX));
}

#[test]
fn values_per_long_divides_sixty_four() {
    for bits in 1..=32 {
        let per = layout::values_per_long(bits);
        assert_eq!(per, (64 / bits) as usize);
        assert!(per >= 2, "bits {bits} should never exceed half a word");
    }
}

#[test]
fn mask_covers_exactly_bits() {
    for bits in 1..=32u32 {
        let mask = layout::mask(bits);
        assert_eq!(mask.count_ones(), bits);
        assert_eq!(mask, (1u64 << bits) - 1);
    }
    assert_eq!(layout::mask(32), 0xFFFF_FFFF);
}

#[test]
fn required_longs_rounds_up() {
    for bits in 1..=32u32 {
        let per = layout::values_per_long(bits);
        for value_count in [0usize, 1, per - 1, per, per + 1, per * 3] {
            let expected = value_count.div_ceil(per);
            assert_eq!(layout::required_longs(value_count, bits), Some(expected));
        }
    }
    assert_eq!(layout::required_longs(4, 0), None);
    assert_eq!(layout::required_longs(4, 33), None);
}
