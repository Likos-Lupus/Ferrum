# ADR-0008: MVP and v1 Definition of Done

- Status: Accepted
- Date: 2026-09-23
- Affected modules: all

## Context

Before scaffolding begins, "done" must be defined so that modules are not declared complete on an
ad-hoc basis. Ferrum ships in stages: an MVP covering the foundation and the storage/worldgen hot
paths, followed by a v1 that adds the remaining modules. This record freezes the product goal, the
release criterion, the module delivery tiers, the non-goals, the release channels, and the single
per-feature Definition of Done. The platform, tooling, and quality constraints are frozen in
ADR-0001 and ADR-0005; performance gates are governed by ADR-0007 and by a later performance record.

## Decision

### Product goal

Ferrum is not "Java mechanically translated to Rust". It handles batch, flat, vectorizable, or
allocation-heavy work that the JVM is bad at, while keeping vanilla behavior as the fallback and
degrading cleanly.

### Release criterion

A module counts as a "releasable optimization" only when all four hold:

1. **Behavior-correct** — equal to the vanilla reference within the specified semantics.
2. **Stable and safe** — corrupt input, a wrong native build, or an undersized buffer must not crash
   the JVM.
3. **Genuinely faster** — the module scenario meets the performance gate, with no meaningful
   regression in non-target scenarios.
4. **Operable** — switch, logging, status, fallback, and problem-reproduction information exist.

### Version and build matrix

Two Minecraft versions (1.21.1 and 26.1.2) × two loaders (Fabric and NeoForge) produce four Java
release variants, across the native platform matrix frozen in ADR-0005. A single native library
carries one Minecraft-independent ABI for as many variants as possible. Platform and quality
constraints (Java 25, namespace, nullability, Jackson 3, JUnit 6, Architectury Loom, native platform
matrix) are frozen in ADR-0001 and ADR-0005 and are not restated here.

### Module delivery tiers

| Module        | MVP                                                           | v1 stable                                    | Deferred / experimental          |
|---------------|---------------------------------------------------------------|----------------------------------------------|----------------------------------|
| FerrumCore    | Required                                                      | Required                                     | —                                |
| FerrumNbt     | Generic full-tree parse/write + network/file buffer fast path | Chunk fixed-schema fast path (if beneficial) | Lazy materialization             |
| FerrumCodec   | Region LZ4 compatible replacement                             | Stable enable                                | Network zstd negotiation         |
| FerrumPalette | pack/unpack                                                   | PalettedContainer bulk remap                 | More aggressive container fusion |
| FerrumNoise   | Normal/Perlin/Improved batch                                  | Grid-level batch                             | Full Router DAG                  |
| FerrumLight   | —                                                             | Block light                                  | Sky light                        |
| FerrumCollide | —                                                             | Batch AABB / shape sweep                     | Multi-entity cross-tick batching |
| FerrumPath    | —                                                             | Walk evaluator + A*                          | Fly/Swim/Amphibious full         |

The first stable release must not be blocked by any deferred/experimental item.

### Non-goals

The first stable release does not treat the following as success prerequisites:

- Client render-mesh construction.
- Replacing every Java object with a native object.
- Changing the save format.
- Forcing a network-protocol change.
- Establishing resident thread pools inside Rust.
- Holding JVM objects or global refs in native code.
- Trading "not fully equal but close enough" for Noise or physics speed.

### Release channels

- **Alpha** — for development and stress testing. Core is on by default; individual modules can be
  enabled; a diagnostic verify mode is allowed. Full logging and crash-safe fallback are mandatory.
- **Beta** — only fast paths that have passed the correctness suite; performance thresholds are
  largely stable; cross-mod compatibility and real modpack testing begin.
- **Stable** — only paths that have passed all blocking gates are enabled by default. Experimental
  features (zstd, Router DAG, and similar) are explicit opt-in.

### Definition of Done (per feature)

Every feature must have:

- A Java reference implementation, a native fast path, and an eligibility check.
- Unit tests, a differential test, and a malformed-input test.
- A JMH/kernel benchmark.
- At least one in-game scenario benchmark.
- A runtime switch and status.
- Fallback counters and failure logging.
- Adapters for both Minecraft versions when the module is cross-version.
- Fabric and NeoForge startup tests.
- Documented known limitations.
- `package-info.java` with `@NullMarked` for every project-owned package.
- Zero Error Prone / NullAway errors and a passing `-Xlint`/`-Werror` build.
- Java tests in JUnit 6, all runnable through `./gradlew check`.
- JSON/YAML/TOML access only through the Jackson 3 project facade.
- Fabric/NeoForge variants built and remapped by the unified Architectury Loom chain.

## Alternatives considered

- Declaring the MVP complete per module without a shared DoD: rejected; it lets behavior, tests,
  performance, and operability drift between modules.
- Leaving the v1 scope open-ended: rejected; deferring Light/Collide/Path and the experimental
  features keeps the first stable release achievable.
- Requiring every module, including deferred ones, for the first stable release: rejected; it
  couples the stable release to the highest-risk work.

## Performance evidence

No thresholds are fixed here. Per ADR-0007, gates are adjusted after a first benchmark round and
recorded separately. The dual gate (kernel-level benefit plus end-to-end benefit) applies to every
enabled fast path.

## Correctness / compatibility impact

The release criterion and the DoD keep the Java reference implementation mandatory and keep the
vanilla path as the fallback, so no module can become the sole correct implementation. Deferred and
experimental items are explicitly excluded from the first stable release criteria.

## Rollback

Scope is changed by adding a higher-numbered ADR and marking this record superseded. At runtime,
`native.enabled=false` restores pure Java behavior regardless of scope.

## Follow-up work

- Scaffold the repository layout and build chain.
- Establish the correctness/fuzz suite and the dual performance gates referenced by the DoD.
- Record the concrete per-module performance thresholds once measured.
