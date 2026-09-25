# F-055 palette remap spike

- Status: Include — a production fused remap is proposed (see ADR-0016)
- Date: 2026-09-24
- Module: FerrumPalette
- Harness: `PaletteRemapSpikeTest` (`paletteRemapSpike` Gradle task); interim runner, not JMH

## Question

Vanilla remaps packed palette ids when a section's palette grows or shrinks
(`PalettedContainer.onResize` / `reencodeContents`, and the older `swapPalette`). Is there enough
value in a native remap to justify a production fast path, and is the value in fusing
`unpack -> map -> pack` rather than in the existing composed symbols?

## Method

- Platform: WSL2 x86_64, glibc; Temurin JDK 25.0.3; Rust 1.98 release build (LTO).
- Oracle: Java `SimpleBitStorage` (`PaletteReference.remap`), i.e. the real game class.
- Four paths measured per case, best of 100 samples after 20 warmups:
    1. Java baseline: `SimpleBitStorage` unpack, map, pack.
    2. Composed native ABI: `ferrum_palette_unpack` -> Java map -> `ferrum_palette_pack`, including
       scratch copies and two FFM crossings.
    3. Fused Rust kernel only: `remap_fused`, no FFM (separate Rust bench).
    4. Fused test-hook path through FFM: `ferrum_test_palette_remap`, including FFM and copies. This
       is the primary Include/Defer input.
- The fused symbol is compiled only with the `test-hooks` cargo feature, is absent from the ABI
  header, is never advertised through feature bits, and is bound only by the spike harness.

## Correctness

- Every fused result is asserted byte-for-byte equal to the Java oracle for all nine spike cases.
- The committed remap corpus (`native/ferrum-native/tests/golden/palette-remap/`, 6 cases) is
  checked byte-for-byte by `tests/palette_remap.rs`; Rust property tests also compare the fused
  kernel against a composed reference for widths 1..12 and sizes up to 4096. All pass.

## Measured cost (release, one run)

Ratios are Java-baseline time / path time; higher is better.

| Case (bitsIn-bitsOut, size) | Java ns | Composed ns | Fused FFM ns | Composed vs Java | Fused vs Java |
|-----------------------------|--------:|------------:|-------------:|-----------------:|--------------:|
| 4-5, 4096                   |  27 852 |      34 906 |       18 695 |            0.798 |     **1.490** |
| 5-4, 4096                   |  27 582 |      29 496 |       12 333 |            0.935 |     **2.236** |
| 4-8, 4096                   |  12 905 |      34 786 |       11 601 |            0.371 |     **1.112** |
| 8-4, 4096                   |  11 973 |      24 857 |        9 448 |            0.482 |     **1.267** |
| 5-5, 4096                   |  12 504 |      26 299 |        8 476 |            0.475 |     **1.475** |
| 3-4, 4096                   |  11 953 |      24 406 |        8 416 |            0.490 |     **1.420** |
| 6-5, 4096                   |  12 173 |      24 106 |       10 169 |            0.505 |     **1.197** |
| 4-8, 257                    |     801 |       3 697 |        3 286 |            0.217 |         0.244 |
| 8-4, 257                    |     811 |       3 737 |        3 406 |            0.217 |         0.238 |

Kernel-only (Rust, no FFM), ratios fused/composed:

| bitsIn-bitsOut | fused ns | composed ns | speedup |
|----------------|---------:|------------:|--------:|
| 4-5            |    4 568 |       6 422 |    1.41 |
| 5-4            |    4 559 |       6 843 |    1.50 |
| 4-8            |    7 253 |       6 582 |    0.91 |
| 8-4            |    4 609 |       6 813 |    1.48 |
| 5-5            |    4 598 |       9 227 |    2.01 |

## Interpretation

- The **composed** native path is always slower than Java (0.22x-0.94x): materializing the
  intermediate `int[]` and paying two FFM crossings costs more than it saves.
- The **fused FFM** path beats Java for section-sized cases (1.11x-2.24x) and loses badly for small
  cases (0.24x), so the value is specifically in a single-pass fused kernel with one FFM crossing,
  and it is size-sensitive.
- Kernel-only results confirm the fused kernel wins in four of five width pairs, but the FFM results
  show a narrower win than the kernel alone; the FFM-inclusive fused result is decisive.

## Recommendation

**Include.** Pursue a production fused palette remap, defined by ADR-0016 and tracked as a follow-up
task. The production path must:

- keep the fast path behind the palette feature bit and an eligibility threshold (the spike loses
  below section scale; enable only after JMH and an in-game `PalettedContainer` measurement clear
  the M3 gate);
- own a stable ABI symbol, separate from the temporary `ferrum_test_palette_remap` hook;
- preserve the composed and Java paths as fallbacks.

The temporary test hook stays strictly test-only and will be removed or reworked when the production
symbol lands.
