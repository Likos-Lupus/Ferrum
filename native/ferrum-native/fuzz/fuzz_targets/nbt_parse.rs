#![no_main]

use libfuzzer_sys::fuzz_target;

use ferrum::nbt::limits::Limits;
use ferrum::nbt::parser;

// Parsing arbitrary bytes must never panic or read out of bounds; both wire forms are exercised.
fuzz_target!(|data: &[u8]| {
    let _ = parser::parse(data, Limits::default(), false);
    let _ = parser::parse(data, Limits::default(), true);
});
