use ferrum::palette::{layout, pack, unpack};

/// A small deterministic xorshift so the property checks are reproducible.
struct Rng(u64);

impl Rng {
    fn next(&mut self) -> u64 {
        let mut state = self.0;
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        self.0 = state;
        state
    }

    fn value(&mut self, mask: u64) -> u32 {
        (self.next() & mask) as u32
    }
}

fn reference_word(values: &[u32], bits: u32, word: usize) -> u64 {
    let per = layout::values_per_long(bits);
    let mut packed = 0u64;
    for slot in 0..per {
        let index = word * per + slot;
        if index >= values.len() {
            break;
        }
        packed |= u64::from(values[index]) << (slot * bits as usize);
    }
    packed
}

#[test]
fn pack_then_unpack_round_trips_for_every_width() {
    let mut rng = Rng(0x1234_5678_9ABC_DEF0);
    for bits in 1..=32u32 {
        let mask = layout::mask(bits);
        for value_count in [0usize, 1, 2, 3, 7, 31, 32, 33, 64, 4096] {
            let values: Vec<u32> = (0..value_count).map(|_| rng.value(mask)).collect();
            let required = layout::required_longs(value_count, bits).expect("valid bits");
            let mut packed = vec![0u64; required];
            assert_eq!(
                pack::pack(&values, bits, &mut packed),
                0,
                "pack failed for bits {bits}, size {value_count}"
            );

            let mut decoded = vec![0u32; value_count];
            assert_eq!(
                unpack::unpack(&packed, bits, value_count, &mut decoded),
                0,
                "unpack failed for bits {bits}, size {value_count}"
            );
            assert_eq!(
                decoded, values,
                "round trip mismatch bits {bits} size {value_count}"
            );
        }
    }
}

#[test]
fn pack_matches_the_word_by_word_reference() {
    let mut rng = Rng(0xDEAD_BEEF_CAFE_1234);
    for bits in 1..=32u32 {
        let mask = layout::mask(bits);
        let value_count = 4096;
        let values: Vec<u32> = (0..value_count).map(|_| rng.value(mask)).collect();
        let required = layout::required_longs(value_count, bits).expect("valid bits");
        let mut packed = vec![0u64; required];
        assert_eq!(pack::pack(&values, bits, &mut packed), 0);

        for (word, actual) in packed.iter().enumerate() {
            assert_eq!(
                *actual,
                reference_word(&values, bits, word),
                "word {word} mismatch bits {bits}"
            );
        }
    }
}

#[test]
fn unused_high_bits_stay_zero() {
    for bits in 1..=32u32 {
        let per = layout::values_per_long(bits);
        if per * bits as usize == 64 {
            continue;
        }
        let value_count = per;
        let values = vec![layout::mask(bits) as u32; value_count];
        let mut packed = vec![0u64; 1];
        assert_eq!(pack::pack(&values, bits, &mut packed), 0);
        let used = per * bits as usize;
        let unused_mask = !((1u64 << used) - 1);
        assert_eq!(
            packed[0] & unused_mask,
            0,
            "unused bits set for bits {bits}"
        );
    }
}

#[test]
fn tail_word_only_covers_remaining_values() {
    let mut rng = Rng(0x0F0F_0F0F_1234_5678);
    for bits in 1..=32u32 {
        let per = layout::values_per_long(bits);
        let mask = layout::mask(bits);
        let value_count = per + per / 2;
        let values: Vec<u32> = (0..value_count).map(|_| rng.value(mask)).collect();
        let required = layout::required_longs(value_count, bits).expect("valid bits");
        assert_eq!(required, 2);
        let mut packed = vec![0u64; required];
        assert_eq!(pack::pack(&values, bits, &mut packed), 0);

        let remaining = value_count - per;
        let used = remaining * bits as usize;
        let unused_mask = !((1u64 << used) - 1);
        assert_eq!(
            packed[1] & unused_mask,
            0,
            "tail padding set for bits {bits}"
        );

        let mut decoded = vec![0u32; value_count];
        assert_eq!(unpack::unpack(&packed, bits, value_count, &mut decoded), 0);
        assert_eq!(decoded, values);
    }
}

#[test]
fn empty_value_set_is_ok() {
    for bits in 1..=32u32 {
        let mut packed: [u64; 0] = [];
        assert_eq!(pack::pack(&[], bits, &mut packed), 0);
        let mut decoded: [u32; 0] = [];
        assert_eq!(unpack::unpack(&[], bits, 0, &mut decoded), 0);
    }
}
