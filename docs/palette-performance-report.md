# Palette bulk kernel benchmark: interim result

- Status: No-Go for default enablement (PALETTE feature bit stays clear)
- Date: 2026-09-24
- Module: FerrumPalette
- Harness: `PaletteKernelBenchmarkTest` (`paletteBenchmark` Gradle task); interim runner, not JMH

## Environment

- Platform: WSL2 x86_64, glibc; Temurin JDK 25.0.3
- Native: `cargo build --release` (LTO), ABI 1
- Baseline: the live vanilla `SimpleBitStorage` (`unpack(int[])` for unpack; `set` loop for pack)
- Protocol: 10 warmup + 50 timed iterations, minimum observed time; single JVM

## Observed results (release, one run)

Ratios are vanilla / native; higher than 1.0 means native is faster.

| bits |  size | vanilla unpack ns | native unpack ns | unpack ratio | vanilla pack ns | native pack ns | pack ratio |
|-----:|------:|------------------:|-----------------:|-------------:|----------------:|---------------:|-----------:|
|    4 |   256 |             5 200 |           13 065 |        0.398 |           8 887 |         14 136 |      0.629 |
|    4 |  4096 |             8 987 |           11 181 |        0.804 |          18 615 |         13 896 |  **1.340** |
|    4 | 65536 |           157 017 |          304 706 |        0.515 |         143 932 |         87 225 |  **1.650** |
|    5 |   256 |               430 |            4 939 |        0.087 |             621 |          8 226 |      0.075 |
|    5 |  4096 |             9 989 |           14 899 |        0.670 |           8 626 |          8 527 |      1.012 |
|    5 | 65536 |           300 698 |           51 608 |    **5.827** |         133 642 |         60 464 |  **2.210** |
|    8 |   256 |               361 |            4 088 |        0.088 |             601 |          4 839 |      0.124 |
|    8 |  4096 |             3 948 |            6 472 |        0.610 |           8 857 |          7 564 |      1.171 |
|    8 | 65536 |            60 995 |           48 411 |    **1.260** |         137 670 |         61 006 |  **2.257** |

## Interpretation

- The wired in-game path is bulk unpack. At the 4096-entry section size it is currently slower
  (0.61x-0.80x), and it also loses at 256 (0.09x-0.40x). Only the 65 536-value case is faster and
  inconsistently so (0.52x-5.83x), which indicates the run is dominated by noise and by the
  value-count dependence of the vanilla loop.
- Native pack is faster from 4096 upward (1.01x-2.26x) but the pack primitive is not yet wired to a
  game path.
- The end-to-end in-game gate (chunk section load/save and palette growth) has not been measured.

## Decision

Keep the `PALETTE` feature bit clear; the vanilla `SimpleBitStorage` path is used. Correctness is
fully covered by the native-tagged differential tests and the committed golden corpus.

## Next steps

1. Replace the interim runner with JMH and take P50/P95/P99 across widths and sizes.
2. Measure the in-game section unpack path (`PalettedContainer`) with JFR allocations.
3. Only if both gates pass, set the `PALETTE` feature bit and record the rule via a new ADR.
