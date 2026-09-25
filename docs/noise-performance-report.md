# Noise kernel benchmark: interim result

- Status: No-Go for default enablement (native kernel does not beat vanilla on the host)
- Date: 2026-09-25
- Module: FerrumNoise
- Harness: `NoiseKernelBenchmarkTest` (`noiseBenchmark` Gradle task); interim runner, not JMH

## Build and environment

- Platform: Windows Subsystem for Linux 2, x86_64, glibc
- JDK: Temurin 25.0.3
- Native: `cargo build --release` (LTO, codegen-units = 1), ABI 1
- Field: `NormalNoise` with `firstOctave = -4`, amplitudes `1.0, 0.5, 0.25, 0.125`
- Protocol: 10 warmup + 50 timed iterations, minimum observed time; single JVM
- Native path includes the one-time descriptor setup excluded, per-batch FFM crossing, and the
  thread-local scratch copies of the three coordinate arrays

## Observed results (release, one run)

| Samples | Vanilla ns |  Native ns | Vanilla / Native |
|--------:|-----------:|-----------:|-----------------:|
|     512 |    141 987 |    156 705 |            0.906 |
|   4 096 |  1 062 439 |  1 145 300 |            0.928 |
|  65 536 | 17 181 221 | 18 349 148 |            0.936 |

## Interpretation

- The native kernel is consistently about 6-9% slower than the vanilla `NormalNoise.getValue`
  loop on this host and size range. The likely costs are the three coordinate-array copies into the
  thread-local scratch and the single FFM crossing, which are not amortized because the leaf kernel
  itself is already cheap and branch-light.
- The measurement is exploratory and taken in WSL2; it is not the controlled benchmark environment
  and is not used as the final enablement gate.

## Decision

Keep the noise feature bit clear. The grid fast path stays reachable through the test-only override
but is inactive by default; the vanilla noise path is used. Correctness is covered by the golden
corpus and the differential/grid tests.

## Next steps

- Reduce per-batch bridge cost (for example by evaluating straight from a lattice descriptor rather
  than three copied coordinate arrays) before another measurement.
- Run the controlled end-to-end worldgen benchmark (kernel samples/s, `fillAllDirectly` time, whole
  chunk time) with JMH and P50/P95/P99.
- Only if the controlled environment clears both gates, set the `NOISE` feature bit and record the
  rule via a new ADR.
