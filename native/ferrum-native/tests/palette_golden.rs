use std::fs;
use std::path::{Path, PathBuf};

use ferrum::palette::{layout, pack, unpack};

fn read_meta(path: &Path) -> Option<(u32, usize)> {
    let text = fs::read_to_string(path).ok()?;
    let mut bits = None;
    let mut size = None;
    for line in text.lines() {
        if let Some(value) = line.strip_prefix("bits=") {
            bits = value.trim().parse().ok();
        }
        if let Some(value) = line.strip_prefix("size=") {
            size = value.trim().parse().ok();
        }
    }
    Some((bits?, size?))
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

/// Verifies the Java-authored `SimpleBitStorage` corpus: unpacking reproduces the values and
/// packing reproduces the raw words byte-for-byte.
#[test]
fn java_golden_corpus_matches() {
    let directory = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/golden/palette");
    if !directory.is_dir() {
        eprintln!("palette golden corpus not present; skipping");
        return;
    }

    let mut checked = 0;
    for entry in fs::read_dir(&directory).expect("read golden dir") {
        let path = entry.expect("entry").path();
        if path.extension().and_then(|value| value.to_str()) != Some("raw") {
            continue;
        }
        let name = path
            .file_stem()
            .and_then(|value| value.to_str())
            .expect("file stem")
            .to_string();
        let (bits, size) = read_meta(&directory.join(format!("{name}.meta")))
            .unwrap_or_else(|| panic!("missing meta for {name}"));

        let raw = read_u64_le(&path);
        let values = read_u32_le(&directory.join(format!("{name}.values")));
        assert_eq!(values.len(), size, "value count for {name}");
        assert_eq!(
            raw.len(),
            layout::required_longs(size, bits).expect("valid bits"),
            "word count for {name}"
        );

        let mut decoded = vec![0u32; size];
        assert_eq!(
            unpack::unpack(&raw, bits, size, &mut decoded),
            0,
            "unpack {name}"
        );
        assert_eq!(decoded, values, "unpack mismatch for {name}");

        let mut repacked = vec![0u64; raw.len()];
        assert_eq!(pack::pack(&values, bits, &mut repacked), 0, "pack {name}");
        assert_eq!(repacked, raw, "pack mismatch for {name}");
        checked += 1;
    }

    assert!(checked > 0, "palette golden corpus was empty");
}
