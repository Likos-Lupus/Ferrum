use std::fs;
use std::path::{Path, PathBuf};

use ferrum::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use ferrum::palette::{layout, pack, remap, unpack};

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
}

fn reference(data: &[u64], bits_in: u32, size: usize, map: &[u32], bits_out: u32) -> Vec<u64> {
    let mut values = vec![0u32; size];
    assert_eq!(unpack::unpack(data, bits_in, size, &mut values), FERRUM_OK);
    let mapped: Vec<u32> = values.iter().map(|value| map[*value as usize]).collect();
    let required = layout::required_longs(size, bits_out).expect("valid bits");
    let mut out = vec![0u64; required];
    assert_eq!(pack::pack(&mapped, bits_out, &mut out), FERRUM_OK);
    out
}

#[test]
fn fused_matches_the_composed_pipeline_for_representative_widths() {
    let mut rng = Rng(0xABCD_1234_5678_9F0E);
    let pairs = [
        (4u32, 5u32),
        (5, 4),
        (4, 8),
        (8, 4),
        (5, 6),
        (8, 8),
        (1, 12),
        (12, 1),
    ];
    for (bits_in, bits_out) in pairs {
        let mask_in = layout::mask(bits_in);
        let mask_out = layout::mask(bits_out);
        let map: Vec<u32> = (0..1usize << bits_in)
            .map(|_| (rng.next() & mask_out) as u32)
            .collect();

        for size in [1usize, 15, 16, 17, 64, 256, 4096] {
            let values: Vec<u32> = (0..size).map(|_| (rng.next() & mask_in) as u32).collect();
            let required_in = layout::required_longs(size, bits_in).expect("valid bits");
            let mut input = vec![0u64; required_in];
            assert_eq!(pack::pack(&values, bits_in, &mut input), FERRUM_OK);

            let expected = reference(&input, bits_in, size, &map, bits_out);
            let mut actual = vec![0u64; expected.len()];
            assert_eq!(
                remap::remap_fused(&input, bits_in, size, &map, bits_out, &mut actual),
                FERRUM_OK,
                "remap failed for {bits_in}->{bits_out} size {size}"
            );
            assert_eq!(
                actual, expected,
                "fused mismatch for {bits_in}->{bits_out} size {size}"
            );
        }
    }
}

#[test]
fn fused_rejects_an_unmapped_value() {
    let input = [0x0000_0000_0000_000Fu64]; // one value 15 at bits 4
    let map = [1u32, 2, 3];
    let mut out = [0u64; 1];
    assert_eq!(
        remap::remap_fused(&input, 4, 1, &map, 4, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
}

#[test]
fn fused_rejects_a_mapped_value_that_does_not_fit() {
    let input = [0x0000_0000_0000_0001u64];
    let map = [4u32];
    let mut out = [0u64; 1];
    assert_eq!(
        remap::remap_fused(&input, 4, 1, &map, 2, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
}

#[test]
fn fused_rejects_small_output_and_invalid_widths() {
    let input = [0u64; 1];
    let map = [0u32; 16];
    let mut out = [0u64; 0];
    assert_eq!(
        remap::remap_fused(&input, 4, 16, &map, 8, &mut out),
        FERRUM_ERR_BUFFER_TOO_SMALL
    );

    let mut out = [0u64; 64];
    assert_eq!(
        remap::remap_fused(&input, 0, 1, &map, 4, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
    assert_eq!(
        remap::remap_fused(&input, 4, 1, &map, 33, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
}

/// Parses a `b<bitsIn>-b<bitsOut>-s<size>` case stem.
fn parse_name(name: &str) -> Option<(u32, u32, usize)> {
    let rest = name.strip_prefix('b')?;
    let (bits_in, rest) = rest.split_once("-b")?;
    let (bits_out, size) = rest.split_once("-s")?;
    Some((
        bits_in.parse().ok()?,
        bits_out.parse().ok()?,
        size.parse().ok()?,
    ))
}

fn read_u64_le(path: &Path) -> Vec<u64> {
    let bytes = fs::read(path).expect("read raw");
    bytes
        .as_chunks::<8>()
        .0
        .iter()
        .map(|chunk| u64::from_le_bytes(*chunk))
        .collect()
}

fn read_u32_le(path: &Path) -> Vec<u32> {
    let bytes = fs::read(path).expect("read values");
    bytes
        .as_chunks::<4>()
        .0
        .iter()
        .map(|chunk| u32::from_le_bytes(*chunk))
        .collect()
}

/// Verifies the Java-authored remap corpus byte-for-byte against the fused kernel.
#[test]
fn java_remap_golden_matches() {
    let directory = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/golden/palette-remap");
    if !directory.is_dir() {
        eprintln!("palette remap golden corpus not present; skipping");
        return;
    }

    let mut checked = 0;
    for entry in fs::read_dir(&directory).expect("read golden dir") {
        let path = entry.expect("entry").path();
        let file_name = path
            .file_name()
            .and_then(|value| value.to_str())
            .unwrap_or("");
        let Some(name) = file_name.strip_suffix(".in.raw") else {
            continue;
        };

        let (bits_in, bits_out, size) =
            parse_name(name).unwrap_or_else(|| panic!("bad remap case name: {name}"));
        let input = read_u64_le(&path);
        let map = read_u32_le(&directory.join(format!("{name}.map")));
        let expected = read_u64_le(&directory.join(format!("{name}.out.raw")));

        let mut actual = vec![0u64; expected.len()];
        assert_eq!(
            remap::remap_fused(&input, bits_in, size, &map, bits_out, &mut actual),
            FERRUM_OK,
            "remap {name}"
        );
        assert_eq!(actual, expected, "fused mismatch for {name}");
        checked += 1;
    }

    assert!(checked > 0, "palette remap golden corpus was empty");
}
