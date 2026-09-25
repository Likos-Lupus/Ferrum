# Block-light benchmark: exploratory result

- Status: No-Go for default enablement (exploratory)
- Date: 2026-09-25
- Module: FerrumLight
- Harness: `LightBenchmarkTest` (`lightBenchmark` Gradle task); interim runner, not JMH

## Build and environment

- Platform: Windows Subsystem for Linux 2, x86_64, glibc
- JDK: Temurin 25
- Native: `cargo build --release` (LTO, codegen-units = 1), ABI 1
- Scene: one 16x16 column with a stone floor, an interior wall, and four glowstone sources
- Protocol: 20 warmup + 50 timed iterations per phase, minimum observed time; single JVM

## Observed result (release, one run)

```text
light-kernel,vanillaNs=1588848,snapshotNs=2362905,nativeNs=3115924,ratio=0.510
```

| Phase                     | Nanoseconds |
|---------------------------|------------:|
| Vanilla `runLightUpdates` |   1 588 848 |
| Snapshot build (Java)     |   2 362 905 |
| Native batch              |   3 115 924 |

## Interpretation

- The snapshot build alone (flattening every cell of the affected sections and their halo) costs
  more than the entire vanilla update. The native batch then runs at about 0.51x the vanilla engine
  on this scene.
- This harness builds the whole halo from scratch for every measurement; the production hook has the
  same cost per `runLightUpdates` batch. Block-light updates are usually small and incremental, so a
  full-section flatten is a poor match for the common case.
- WSL2 timing is exploratory only and is not an enablement gate.

## Decision

Keep the `LIGHT` feature bit clear. The native block-light batch stays available but inactive; the
vanilla engine is used. Correctness is covered by the two-version differential tests.

## Next steps

1. Controlled end-to-end measurement (place/remove source, occlusion, cross-chunk, section
   load/unload) before the per-version `LIGHT` feature-bit decision.
2. Snapshot cost reduction: only flatten sections that can actually be reached, cache flattened
   properties across the session, and avoid re-scanning unchanged sections.
3. Evaluate 1.21.1 independently: correctness is already exact, but its flattened-property access
   must clear the same end-to-end gate on its own.
