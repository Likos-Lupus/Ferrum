# ADR-0017: Noise descriptor, handle ABI, and leaf-grid batching

- Status: Accepted
- Date: 2026-09-25
- Affected modules: FerrumNoise, FerrumCore (FFM bindings)

## Context

Worldgen noise must be bit-exact with vanilla (ADR-0006). Vanilla builds immutable
`NormalNoise` / `PerlinNoise` / `ImprovedNoise` tables from the world seed; a value is a
deterministic `double`. Moving evaluation to Rust therefore needs a way to carry those tables across
the ABI without re-deriving them, and an entry point to evaluate many coordinates at once.

At the time of this record the ABI declared `ferrum_noise_batch` and `ferrum_noise_destroy` but no
creation entry point and no wire format for the tables. The leaf classes have the same names and the
same evaluation logic in 1.21.1 and 26.1.2, so a single kernel serves both versions and only the
Java extraction differs.

## Decision

### ABI

- Add the additive symbol `ferrum_noise_create(const uint8_t* descriptor, size_t descriptor_len,
  uint64_t* out_handle)` that parses a descriptor, validates it, stores the field in a handle
  registry, and writes an opaque handle. The stable surface grows from 14 to 15 symbols.
- `ferrum_noise_batch(handle, xs, ys, zs, out, count, flags)` evaluates one value per sample;
  `flags` is reserved and must be zero.
- `ferrum_noise_destroy(handle)` releases the field exactly once. Handles are tagged so stale or
  foreign handles are rejected instead of dereferenced.

### Descriptor

A descriptor is a small, versioned, little-endian blob. Java copies every scalar verbatim,
**including the derived factors** (`lowestFreqInputFactor`, `lowestFreqValueFactor`, and
`NormalNoise.valueFactor`), so Rust never re-derives them with a potentially different `pow`
implementation.

```text
magic   [4]  "FBNS"
version u8   1
kind    u8   1 = Improved, 2 = Perlin, 3 = Normal
```

- `Improved`: `xo f64, yo f64, zo f64, permutation[256]`.
- `Perlin`: `firstOctave i32, lowestFreqInputFactor f64, lowestFreqValueFactor f64,
  levelCount u32, amplitudes f64[levelCount]`, then one `Improved` body for every non-zero amplitude
  (a zero amplitude has no level, matching the vanilla constructor).
- `Normal`: `valueFactor f64`, then the `first` and `second` `Perlin` bodies.

Malformed or truncated descriptors return `MALFORMED_INPUT`; more than 256 levels returns
`LIMIT_EXCEEDED`; null pointers for non-empty input return `INVALID_ARGUMENT`.

### Evaluation

The kernel reproduces the vanilla operation order exactly: `Mth.floor` / `Mth.lfloor`,
`Mth.smoothstep`, `Mth.lerp` / `lerp2` / `lerp3`, the `SimplexNoise.GRADIENT` table and `dot`,
`PerlinNoise.wrap`, and the `NormalNoise` input factor. No fast-math, reassociation, or fused
multiply-add is used.

### Java integration and grid scope

- The Java adapter serializes the tables through Mixin accessors and caches one handle per live
  `NormalNoise`, keyed by identity, for the owning world/resource generation. Handles are released
  on world unload and datapack reload (`NoiseHandleCache.closeAll()`).
- The only grid path is a `HEAD` hook on the direct `DensityFunctions.Noise.fillArray` leaf. It
  requires the provider to be a `NoiseChunk` so the index-to-coordinate mapping vanilla uses can be
  reproduced through `provider.forIndex(index)`. `ShiftedNoise`, other wrappers and composites,
  unknown density shapes, and the full Router DAG fall back to vanilla. The first version
  deliberately stays narrow; wrapper fusion is a follow-up.
- The `NOISE` feature bit stays clear until the correctness gates pass **and** a controlled
  end-to-end worldgen benchmark clears the performance gate. Rust-bit correctness plus a kernel
  benchmark is not enough to advertise the production feature. Tests reach the native path through a
  test-only override (`ferrum.test.noise.force`) that does not change the advertised feature bits or
  production reporting.

## Alternatives considered

- Rebuilding the noise tables in Rust from the seed: rejected; it duplicates vanilla's random
  derivation and widens the semantic surface that must stay bit-exact.
- Passing parameters as JSON: rejected; it adds a data-binding step to setup and is not a compact
  binary contract.
- Batching wrappers such as `ShiftedNoise` immediately: rejected; self-referential scale functions
  would need multiple Java/native transitions and are better evaluated later as one fused operation.
- Native `Router DAG` execution: rejected for this step; it is a separate, later experiment.

## Performance evidence

The leaf kernel is measured by `noiseBenchmark`; results are recorded in
`docs/noise-performance-report.md`. WSL2 numbers are exploratory only. The `NOISE` feature bit stays
clear until the controlled environment measures the end-to-end worldgen scenario.

## Correctness / compatibility impact

- `native/ferrum-native/tests/golden/noise/` holds a Java-authored corpus (descriptor, coordinates,
  expected raw bits) covering Normal, Perlin, and Improved fields across boundary, large-magnitude,
  and wrap-period coordinates. The Rust `noise_golden` test requires raw-bit equality.
- `NativeNoiseDifferentialTest` compares the native kernels against the live vanilla classes for all
  three kinds without requiring Minecraft world state.
- `NoiseGridDifferentialTest` builds a real `NoiseChunk` and compares the native grid fill against
  vanilla for a whole cell at a fixed seed; zero mismatches is required.
- The behavior is bit-exact by construction; any mismatch is a No-Go for the affected target.

## Rollback

Disable the noise module by configuration; the vanilla noise path remains. The descriptor format is
changed only by a superseding ADR. The override is test-only and never enables production.

## Follow-up work

- Controlled end-to-end worldgen benchmark, then the `NOISE` feature bit decision.
- Wrapper fusion (`ShiftedNoise` and friends) as a single fused native operation.
- Full Router DAG native interpreter as a later experiment.
- NeoForge datapack-reload handle release (Fabric releases on reload today; NeoForge currently
  releases on server stop).
