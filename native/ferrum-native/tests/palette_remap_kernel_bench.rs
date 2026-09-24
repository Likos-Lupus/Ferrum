//! Kernel-only measurement for the F-055 remap spike.
//!
//! Not part of the default suite. Run with:
//! `cargo test --release --test palette_remap_kernel_bench -- --ignored --nocapture`

use std::hint::black_box;
use std::time::Instant;

use ferrum::palette::{layout, pack, remap, unpack};

const WARMUP: usize = 50;
const ITERATIONS: usize = 200;

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

fn best(mut action: impl FnMut()) -> u64 {
    for _ in 0..WARMUP {
        action();
    }
    let mut best = u64::MAX;
    for _ in 0..ITERATIONS {
        let start = Instant::now();
        action();
        best = best.min(start.elapsed().as_nanos() as u64);
    }
    best
}

#[test]
#[ignore = "kernel-only spike measurement; run explicitly"]
fn kernel_only_remap_benchmark() {
    let mut rng = Rng(0x1357_9BDF_2468_ACE0);
    let pairs = [(4u32, 5u32), (5, 4), (4, 8), (8, 4), (5, 5)];
    let size = 4096usize;

    println!("bitsIn,bitsOut,fusedNs,composedNs,ratio");
    for (bits_in, bits_out) in pairs {
        let mask_in = layout::mask(bits_in);
        let mask_out = layout::mask(bits_out);
        let map: Vec<u32> = (0..1usize << bits_in)
            .map(|_| (rng.next() & mask_out) as u32)
            .collect();
        let values: Vec<u32> = (0..size).map(|_| (rng.next() & mask_in) as u32).collect();
        let required_in = layout::required_longs(size, bits_in).expect("valid bits");
        let required_out = layout::required_longs(size, bits_out).expect("valid bits");
        let mut input = vec![0u64; required_in];
        assert_eq!(pack::pack(&values, bits_in, &mut input), 0);

        let fused = best(|| {
            let mut out = vec![0u64; required_out];
            let status = remap::remap_fused(&input, bits_in, size, &map, bits_out, &mut out);
            assert_eq!(status, 0);
            black_box(out);
        });
        let composed = best(|| {
            let mut unpacked = vec![0u32; size];
            assert_eq!(unpack::unpack(&input, bits_in, size, &mut unpacked), 0);
            let mapped: Vec<u32> = unpacked.iter().map(|value| map[*value as usize]).collect();
            let mut out = vec![0u64; required_out];
            assert_eq!(pack::pack(&mapped, bits_out, &mut out), 0);
            black_box(out);
        });

        println!(
            "{bits_in},{bits_out},{fused},{composed},{:.3}",
            composed as f64 / fused as f64
        );
    }
}
