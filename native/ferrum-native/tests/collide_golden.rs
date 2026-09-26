//! Replays the Java-authored collide golden corpus (ADR-0019).
//!
//! Each `.in` file is a versioned input blob; the matching `.expected` file is the observable
//! vanilla result (hit location/direction for the clip, resolved movement for the sweep). The test
//! runs the same input through the native entry point and compares raw bits. If the corpus is
//! absent the test is a no-op so a fresh checkout still builds.

use std::fs;
use std::path::PathBuf;

const CLIP_MAGIC: [u8; 4] = *b"FBCA";
const SWEEP_MAGIC: [u8; 4] = *b"FBCS";
const CLIP_OUT_MAGIC: [u8; 4] = *b"FBCO";
const SWEEP_OUT_MAGIC: [u8; 4] = *b"FBCT";
const CLIP_EXPECTED_MAGIC: [u8; 4] = *b"FBCR";
const SWEEP_EXPECTED_MAGIC: [u8; 4] = *b"FBCE";

#[test]
fn java_golden_corpus_replays() {
    let directory = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/golden/collide");
    if !directory.is_dir() {
        eprintln!("collide golden corpus not present; skipping");
        return;
    }

    let mut checked = 0;
    for entry in fs::read_dir(&directory).expect("read golden dir") {
        let path = entry.expect("entry").path();
        if path.extension().and_then(|value| value.to_str()) != Some("in") {
            continue;
        }
        let name = path
            .file_stem()
            .and_then(|value| value.to_str())
            .expect("file stem")
            .to_string();
        let input = fs::read(&path).expect("read input");
        let expected = fs::read(directory.join(format!("{name}.expected"))).expect("expected");

        if input.starts_with(&CLIP_MAGIC) {
            check_clip(&input, &expected, &name);
        } else if input.starts_with(&SWEEP_MAGIC) {
            check_sweep(&input, &expected, &name);
        } else {
            panic!("unknown collide golden input magic for {name}");
        }
        checked += 1;
    }

    assert!(checked > 0, "collide golden corpus was empty");
}

fn check_clip(input: &[u8], expected: &[u8], name: &str) {
    assert_eq!(
        &expected[0..4],
        &CLIP_EXPECTED_MAGIC,
        "expected magic {name}"
    );

    let from = [read_f64(input, 8), read_f64(input, 16), read_f64(input, 24)];
    let to = [
        read_f64(input, 32),
        read_f64(input, 40),
        read_f64(input, 48),
    ];

    let mut output = vec![0u8; 64];
    let mut written = 0usize;
    let status = unsafe {
        ferrum::collide::ferrum_collide_aabb_clip(
            input.as_ptr(),
            input.len(),
            output.as_mut_ptr(),
            output.len(),
            &mut written,
        )
    };
    assert_eq!(status, 0, "clip status for {name}");
    assert!(written >= 24, "clip output too small for {name}");
    assert_eq!(&output[0..4], &CLIP_OUT_MAGIC, "clip out magic {name}");

    let found = output[8] != 0;
    let direction = output[9];
    let scale = read_f64(&output, 12);

    assert_eq!(expected[8] != 0, found, "clip found mismatch for {name}");
    assert_eq!(expected[9], direction, "clip direction mismatch for {name}");

    if found {
        for axis in 0..3 {
            let location = from[axis] + scale * (to[axis] - from[axis]);
            let expected_location = read_f64(expected, 12 + axis * 8);
            assert_eq!(
                expected_location.to_bits(),
                location.to_bits(),
                "clip location axis {axis} mismatch for {name}"
            );
        }
    }
}

fn check_sweep(input: &[u8], expected: &[u8], name: &str) {
    assert_eq!(
        &expected[0..4],
        &SWEEP_EXPECTED_MAGIC,
        "expected magic {name}"
    );

    let mut output = vec![0u8; 64];
    let mut written = 0usize;
    let status = unsafe {
        ferrum::collide::ferrum_collide_sweep(
            input.as_ptr(),
            input.len(),
            output.as_mut_ptr(),
            output.len(),
            &mut written,
        )
    };
    assert_eq!(status, 0, "sweep status for {name}");
    assert!(written >= 32, "sweep output too small for {name}");
    assert_eq!(&output[0..4], &SWEEP_OUT_MAGIC, "sweep out magic {name}");

    for axis in 0..3 {
        let actual = read_f64(&output, 8 + axis * 8);
        let expected_value = read_f64(expected, 8 + axis * 8);
        assert_eq!(
            expected_value.to_bits(),
            actual.to_bits(),
            "sweep axis {axis} mismatch for {name}"
        );
    }
}

fn read_f64(bytes: &[u8], offset: usize) -> f64 {
    let mut raw = [0u8; 8];
    raw.copy_from_slice(&bytes[offset..offset + 8]);
    f64::from_le_bytes(raw)
}
