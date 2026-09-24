#![no_main]

use libfuzzer_sys::fuzz_target;

use ferrum::palette::{layout, pack};

// Arbitrary widths, counts, and values must never panic or write out of bounds.
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
    let values: Vec<u32> = data[8..]
        .as_chunks::<4>()
        .0
        .iter()
        .map(|chunk| u32::from_le_bytes(*chunk))
        .collect();
    let required = layout::required_longs(count, bits).expect("valid bits");
    let mut out = vec![0u64; required];
    let _ = pack::pack(&values[..values.len().min(count)], bits, &mut out);
});
