#![no_main]

use libfuzzer_sys::fuzz_target;

use ferrum::codec::decode_stream;

// Decoding arbitrary bytes must never panic or read out of bounds. When the stream declares a
// bounded output size, the decode is also attempted so the block loop is exercised.
fuzz_target!(|data: &[u8]| {
    let mut required = 0usize;
    let _ = decode_stream(data, &mut [], &mut required);
    if required > 0 && required <= 4 * 1024 * 1024 {
        let mut out = vec![0u8; required];
        let mut written = 0usize;
        let _ = decode_stream(data, &mut out, &mut written);
    }
});
