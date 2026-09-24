# LZ4 block-stream benchmark: interim result

- Status: No-Go for default enablement (kernel gate met at >=64 KiB; end-to-end gate unmeasured)
- Date: 2026-09-24
- Module: FerrumCodec
- Harness: `Lz4KernelBenchmarkTest` (`codecBenchmark` Gradle task); interim runner, not JMH

## Build and environment

- Platform: Windows Subsystem for Linux 2, x86_64, glibc
- JDK: Temurin 25.0.3
- Native: `cargo build --release` (LTO, codegen-units = 1), ABI 1
- Corpus: pseudo-random payloads (incompressible), lz4-java `LZ4BlockOutputStream`/
  `LZ4BlockInputStream`
- Protocol: 10 warmup + 50 timed iterations per size, minimum observed time; single JVM

## Observed results (release, one run)

| Payload bytes | Vanilla decode ns | Native decode ns | Decode ratio | Vanilla encode ns | Native encode ns | Encode ratio |
|--------------:|------------------:|-----------------:|-------------:|------------------:|-----------------:|-------------:|
|         4 096 |             6 462 |           21 029 |         0.31 |            16 089 |           23 884 |         0.67 |
|        65 536 |           251 575 |          136 272 |     **1.85** |           441 405 |          145 369 |     **3.04** |
|     1 048 576 |           535 920 |          306 877 |     **1.75** |           875 257 |          401 061 |     **2.18** |

## Interpretation

- At the region-relevant chunk sizes (>=64 KiB) the native kernel clears the 1.25x kernel gate in
  both directions, with encode gains larger than decode gains.
- At 4 KiB the fixed FFM and framing overhead dominates and the native path is a regression, so a
  minimum batch threshold is required before any default enablement.
- The end-to-end region scenario (open a region of mixed chunk sizes, read+write, measure wall time
  and allocations) has not been measured, so the second gate is unmet.

## Decision

Keep the `CODEC_LZ4` feature bit clear. The fast path is available and correct but inactive by
default; regions are decoded and encoded by vanilla `lz4-java`. Interoperability is exercised by the
`@Tag("native")` differential tests in both directions.

## Next steps

1. Replace the interim runner with JMH and take P50/P95/P99 across sizes and both wire forms.
2. Measure the end-to-end region read/write scenario with a representative chunk-size distribution.
3. Derive `minBatch` from the crossover with margin, then set the `CODEC_LZ4` feature bit and record
   the rule in a new ADR.
4. Handle the region compression-selection policy (whether Ferrum may choose LZ4 for new writes) in
   a separate ADR.
