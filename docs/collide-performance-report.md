# Collide kernel benchmark: interim result

- Status: No-Go for default enablement (inconclusive evidence)
- Date: 2026-09-24
- Module: FerrumCollide
- Harness: `CollideBenchmarkTest` (`collideBenchmark` Gradle task); interim runner, not JMH

## Build and environment

- Platform: Windows Subsystem for Linux 2, x86_64, glibc
- JDK: Temurin 25.0.3
- Native: `cargo build --release` (LTO, codegen-units = 1), ABI 1
- Protocol: 50 warmup + 200 timed iterations, mean per iteration, single JVM

## Observed results (release, one run)

| Kernel    | Batch      | Vanilla ns | Native ns | Vanilla / Native |
|-----------|------------|-----------:|----------:|-----------------:|
| AABB clip | 256 boxes  |     44 040 |    47 774 |             0.92 |
| Sweep     | 256 shapes |     88 762 |   197 189 |             0.45 |

## Interpretation

- The clip kernel is within ~8% of vanilla; the native path is dominated by blob construction and
  the 256-box list is already well handled by the JVM.
- The sweep number is pessimistic: the interim harness re-serializes every shape on every iteration
  (reflecting `VoxelShape.shape` and packing the occupancy bitset). Production amortizes this in the
  `ShapeDescriptorCache`, so a cache-amortized measurement is required before judging the kernel.
- Neither path has a measured end-to-end scenario (real block raycasts, entity movement in a world).

## Decision

Keep the `COLLIDE` feature bit clear. Both fast paths stay available but inactive by default; the
vanilla paths are used. Behaviour is exercised by the `@Tag("native")` differential tests and the
cross-arch golden corpus.

## Next steps

1. Replace the interim runner with JMH and measure the descriptor-cache-amortized sweep and the clip
   over representative ray lengths.
2. Measure the in-game scenarios (player raycast targeting; entity movement over stairs and gaps)
   with JFR allocations.
3. Only if both gates pass on the supported platform matrix, set the `COLLIDE` feature bit and
   record the rule via a new ADR.
