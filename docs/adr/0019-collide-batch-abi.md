# ADR-0019: Collide batch ABI (AABB clip and VoxelShape sweep)

- Status: Accepted
- Date: 2026-09-24
- Affected modules: FerrumCollide

## Context

FerrumCollide has two independent fast paths: a batch AABB ray clip and a batched voxel-shape sweep
used by entity movement. Both must stay behaviour-identical to the vanilla Java implementations; the
sweep in particular decides entity positions and hit faces, so any divergence is a correctness bug.

The vanilla behaviour is version-stable but not a single fixed axis order:
`Entity.collideWithShapes`
resolves movement axes in the order `Direction.axisStepOrder(movement)` (26.1.2) or the equivalent
unrolled order (1.21.1), where the vertical axis is always first and the larger of the horizontal
axes follows. The Rust kernel must therefore receive the axis order from the Java adapter instead of
assuming `X, Y, Z`.

## Decision

Add two additive, stateless ABI entry points (ABI version stays 1):

```c
int32_t ferrum_collide_aabb_clip(
    const uint8_t* in, size_t in_len,
    uint8_t* out, size_t out_cap,
    size_t* out_written);

int32_t ferrum_collide_sweep(
    const uint8_t* in, size_t in_len,
    uint8_t* out, size_t out_cap,
    size_t* out_written);
```

Both use a little-endian, magic-versioned self-describing blob and are exception-safe (`guard`),
bounds-checked, and allocation-bounded.

- **Clip input `FBCA` v1**: `from[3]`, `to[3]`, `box_count`, then `box_count` axis-aligned boxes
  `[minX,minY,minZ,maxX,maxY,maxZ]` in world coordinates. The adapter has already applied the
  block-position offset.
- **Clip output `FBCO` v1**: `found`, `direction` (vanilla `Direction` ordinal, `0xFF` when none),
  `scale`, `box_index`. The adapter builds the `BlockHitResult` from `from + scale * (to - from)`.
- The clip kernel replicates `AABB.clip(Iterable, from, to, pos)` exactly: `EPSILON = 1.0E-7`, the
  shared `scaleReference`, the carried last direction, and the `clipPoint` slab test in the
  `X, Y, Z` order with the vanilla from/point/min-max argument shuffling.
- **Sweep input `FBCS` v1**: `moving[6]`, `movement[3]`, `axis_order[3]`, `shape_count`, then each
  shape as axis sizes, the per-axis coordinate arrays, and an occupancy bitset.
- **Sweep output `FBCT` v1**: the resolved movement `[3]`.
- The sweep kernel replicates `Shapes.collide` + `VoxelShape.collideX` (including `Mth.binarySearch`
  index finding and `DiscreteVoxelShape.isFullWide` under the `AxisCycle` transform) and applies the
  axes in the adapter-supplied order, accumulating the resolved movement exactly as
  `collideWithShapes` does.
- Java owns world queries and candidate collection; Rust never touches world state. Shapes are
  encoded by a Java-side identity cache; native keeps no handles, so resource reload cannot leak
  one.
- The Java adapters are the vanilla `AABB.clip(Iterable, ...)` static and the private static
  `Entity.collideWithShapes`. A conservative minimum batch size applies; anything not recognised,
  over-limit, or non-`List` falls back to vanilla.
- The `COLLIDE` feature bit stays clear until the end-to-end gate passes (dual-gate policy). Tests
  reach the path only through the test-only `ferrum.test.collide.force` override, which never
  changes the reported feature bits.

## Alternatives considered

- Native voxel-shape handles with a lifecycle cache: deferred; a stateless blob avoids resource
  reload leak risk. Revisit only if descriptor encoding shows up in profiles.
- A `BlockGetter.clip` level hook: deferred. That method mixes block and fluid shapes, the
  `VoxelShape.clip` full-cell probe path, and the interaction-shape override, so a faithful fusion
  is a larger follow-up; the per-list kernel is the bounded first step.

## Performance evidence

No performance commitment in this record. `docs/collide-performance-report.md` records the local
kernel numbers and the enablement decision; the hard gate is the controlled end-to-end measurement.

## Correctness / compatibility impact

Behaviour equivalence is the release criterion: same position, collision result, and hit face.
Golden tests cover parallel axes, origin inside a box, boundaries, negative movement, and zero or
tiny deltas; differential tests compare against the vanilla engines on both supported versions.
Corrupt, truncated, and over-limit blobs return a status and never crash.

## Rollback

Disable either fast path through configuration; the vanilla Java paths remain. The decision itself
is changed only by a superseding ADR.

## Follow-up work

- A `BlockGetter.clip`-level fusion once the per-list kernel is proven.
- Wrapper/composite density-style fusion is out of scope here; the sweep already batches per
  movement.
