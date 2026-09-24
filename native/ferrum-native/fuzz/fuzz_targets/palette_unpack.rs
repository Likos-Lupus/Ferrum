#![no_main]

use libfuzzer_sys::fuzz_target;

use ferrum::palette::{layout, unpack};

// Arbitrary widths, counts, and words must never panic or read out of bounds.
fuzz_target!(|data: &[u8]| {
    if data.len() < 8 {
        return;
    }

    let bits = u32::from_le_bytes([data[0], data[1], data[2], data[3]]);
    let size = u32::from_le_bytes([data[4], data[5], data[6], data[7]]) as usize;
    if !layout::is_valid_bits(bits) {
        return;
    }

    let count = size.min(1 << 16);
    let words: Vec<u64> = data[8..]
        .as_chunks::<8>()
        .0
        .iter()
        .map(|chunk| u64::from_le_bytes(*chunk))
        .collect();
    let mut out = vec![0u32; count];
    let _ = unpack::unpack(&words, bits, count, &mut out);
});
