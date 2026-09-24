# NBT kernel benchmark: interim result

- Status: No-Go for default enablement (inconclusive evidence)
- Date: 2026-09-24
- Module: FerrumNbt
- Harness: `NbtKernelBenchmarkTest` (`nbtBenchmark` Gradle task); interim runner, not JMH

## Build and environment

- Platform: Windows Subsystem for Linux 2, x86_64, glibc
- JDK: Temurin 25.0.3
- Native: `cargo build --release` (LTO, codegen-units = 1), ABI 1
- Corpus: synthetic `CompoundTag` (ints, longs, strings, doubles), named and any wire forms
- Protocol: 20 warmup + 100 timed iterations per size, minimum observed time; single JVM

## Observed results (release, one run)

| Document bytes | Form  | Vanilla ns | Native ns | Vanilla / Native |
|---------------:|-------|-----------:|----------:|-----------------:|
|          1 157 | named |     24 917 |    98 796 |             0.25 |
|         24 888 | named |    237 027 |   150 124 |         **1.58** |
|         99 183 | named |    522 316 |   605 502 |             0.86 |
|          1 157 | any   |     20 419 |    33 173 |             0.62 |
|         24 888 | any   |     79 691 |   147 428 |             0.54 |
|         99 183 | any   |    348 357 |   604 661 |             0.58 |

## Interpretation

- The named result at 24 888 bytes is above the 1.25x kernel gate, but the small and large points
  are below 1.0x, and the logically near-identical any-form points disagree, so the measurements are
  dominated by JIT/GC noise rather than a stable crossover.
- The native parse plus Java materialization performs two passes (native parse into the arena, then
  a Java walk to rebuild `Tag` objects), whereas vanilla parses directly into the tree. The arena
  copy and the per-field materialization are the likely costs.
- The end-to-end chunk scenario has not been measured, so the second (end-to-end) gate is unmet.

## Decision

Keep the NBT feature bit clear. The fast path stays available but inactive by default; the vanilla
path is used. The behaviour is exercised by the `@Tag("native")` differential tests.

## Next steps

1. Replace the interim runner with JMH and take P50/P95/P99 across sizes and both wire forms.
2. Measure the in-game chunk load/save scenario (1k/10k chunks) with JFR allocations.
3. Only if both gates pass on the supported platform matrix, set the NBT feature bit and record the
   rule via a new ADR.
