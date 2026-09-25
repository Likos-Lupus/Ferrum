# ADR-0018: Block-light batch ABI and versioned snapshot blob

- Status: Accepted
- Date: 2026-09-25
- Affected modules: FerrumLight, FerrumCore (FFM bindings)

## Context

Ferrum Light v1 accelerates **block light only**. The vanilla block-light engine
(`net.minecraft.world.level.lighting.LightEngine` / `BlockLightEngine`) is a queue-driven BFS:
`runLightUpdates()` drains a set of nodes to check, then processes a decrease queue, then an
increase queue, and finally publishes changed sections through the section storage. Both target
versions share the same queue-entry packing (level in bits 0-3, one direction bit per face in bits
4-9, `0x400` "from empty shape", `0x800` "increase from emission") and the same FIFO
decrease-then-increase ordering, so one Minecraft-independent kernel serves both.

The work must stay inside a single, bounded `runLightUpdates` batch. A large typed per-field ABI
would churn every time the flattened snapshot grows, and the kernel must never hold Java references
or mutate Java state before it can commit. This record freezes the one coarse batch entry point and
its blob schema.

## Decision

### ABI

- Add the additive symbol
  `ferrum_light_block_batch(const uint8_t* in, size_t in_len, uint8_t* out, size_t out_cap, size_t* out_written)`.
  The stable symbol surface grows from 15 to 16.
- The call is **stateless**: there is no light handle, no create/destroy. One call performs the
  whole batch.
- `in` is one versioned, little-endian, opaque blob. `out` receives one versioned blob. Both are
  caller-owned. On `BUFFER_TOO_SMALL`, `out_written` carries the required output size and the caller
  may reallocate and retry once.
- All offsets, counts, and lengths derived from the blob are validated with checked arithmetic
  before any slice is used. No pointer into Java memory is retained, and there is no nested native
  ownership.

### Snapshot blob schema (`FBLT`, version 1)

```text
magic          [4]  "FBLT"
version        u8   1
flags          u8   0 (reserved)
reserved       u16  0
section_count  u32
palette_count  u32
decrease_count u32
increase_count u32
check_count    u32
reserved2      u32  0
```

Then, in order:

- `palette`: `palette_count` entries of `opacity u8, emission u8, empty_shape u8, reserved u8`.
- `sections`: `section_count` records of `section_x i32, section_y i32, section_z i32,
  reserved i32, default_level u8, has_data u8, light_on u8, reserved2 u8`, followed by
  `cell_props[4096] u16`, and then, when `has_data != 0`, `cell_levels[4096] u8`. `cell_levels` and
  `cell_props` use the vanilla cell index `y << 8 | z << 4 | x` within the section.
- `decrease` queue: `decrease_count` entries of `x i32, y i32, z i32, data u64`.
- `increase` queue: `increase_count` entries of `x i32, y i32, z i32, data u64`.
- `checks`: `check_count` entries of `x i32, y i32, z i32`.

Section coordinates are section coordinates (`block >> 4`). Nodes are explicit `x/y/z`; the blob
never reuses `BlockPos.asLong` packing. The queue `data` word is the vanilla queue-entry word.

Rejection rules: an unknown magic, version, or non-zero reserved/flags returns `UNSUPPORTED`;
truncation, missing cells, or a bad palette/body returns `MALFORMED_INPUT`; counts or sizes above
the kernel limits return `LIMIT_EXCEEDED`; null pointers with non-empty lengths return
`INVALID_ARGUMENT`. A rejected input must not produce a partial output.

### Output blob schema (`FBLO`, version 1)

```text
magic         [4]  "FBLO"
version       u8   1
flags         u8   0 (reserved)
reserved      u16  0
changed_count u32
processed     u32
```

Then `changed_count` records of `section_x i32, section_y i32, section_z i32, reserved i32,
data[2048] u8`, holding the final nibble-encoded `DataLayer` for every section whose levels changed.

### Kernel semantics

The kernel reproduces the vanilla operation order exactly: `checkNode`, then `propagateDecrease`,
then `propagateIncrease`, over FIFO queues; `getOpacity = max(1, lightDampening)`; emission gated by
`lightOnInSection`; `PULL_LIGHT_IN_ENTRY = decreaseAllDirections(1)`; the same direction order and
opposite-face rule.

For v1, occlusion is represented by the frozen simple model: a state is either an empty shape or a
full cube, and `shapeOccludes(from, to, dir) = from.fullCube || to.fullCube`. Any partial or
otherwise unrepresentable shape rejects the whole batch.

### Java integration, hook, and scope

- The hook is the `BlockLightEngine` execution boundary. `BlockLightEngine` inherits
  `runLightUpdates()` from `LightEngine`, so the adapter intercepts that inherited path only when
  the receiver is a `BlockLightEngine` (a `LightEngine` mixin with an `instanceof BlockLightEngine`
  guard, or an equivalent block-engine-specific call site). `LevelLightEngine.runLightUpdates()` is
  deliberately **not** hooked: it coordinates block and sky lighting and widens the replacement
  boundary beyond the block-only v1 scope.
- Execution shape: build the complete snapshot, validate/flatten every section and property, run the
  eligibility check, make **one** FFM call, run the full BFS natively, then commit. Any of an
  unsupported shape, an incomplete halo, an invalid snapshot, or a native failure means **no partial
  commit** and the vanilla Java batch runs instead. Native and Java propagation are never mixed
  inside one BFS batch. Fallback reasons are counted.
- Java keeps region/storage publish, renderer/network invalidation, and the remaining section
  bookkeeping; Rust only computes final nibbles and the changed-section list.
- Sky light is out of scope for v1.

### Version axes

- One Minecraft-independent blob and kernel serve 1.21.1 and 26.1.2. Version differences are
  isolated in Java snapshot extraction and property flattening.
- The `LIGHT` feature bit stays clear. Correctness must be exact on both versions, but each version
  earns enablement independently: snapshot construction is measured separately, and if 1.21.1's
  BlockState/light-property access makes the end-to-end gate fail, it is recorded as a measured
  1.21.1 No-Go while 26.1.2 remains supported.
- Tests reach the native path through the test-only `ferrum.test.light.force` override, which does
  not change advertised feature bits or production reporting.

## Alternatives considered

- A large typed per-field C signature: rejected; it churns the ABI as the flattened snapshot grows.
- Hooking `LevelLightEngine.runLightUpdates()`: rejected; it would replace both block and sky
  lighting, beyond the v1 scope.
- Batching only the leaf propagation helpers (`propagateIncrease`/`propagateDecrease`): rejected for
  v1; it would interleave many small FFM calls instead of one coarse transition.
- Supporting partial occlusion shapes in v1: rejected; it would require a richer per-face shape
  table. A whole-batch fallback and a counted reason keep the first version honest.

## Performance evidence

Measured by `lightBenchmark` and recorded in `docs/light-performance-report.md`. WSL2 numbers are
exploratory only. The `LIGHT` feature bit stays clear until the controlled environment measures the
end-to-end scenario, and 1.21.1 is gated separately.

## Correctness / compatibility impact

- Final block-light level must match vanilla cell-for-cell; intermediate queue order may differ, but
  the changed-section notices must be complete.
- Rust differential tests cover small hand-built arrays and the blob corruption/limits surface.
- Java differential tests compare against the live vanilla engine for random block-property grids
  and game scenarios; zero mismatches is required.
- Any mismatch is a No-Go for the affected version.

## Rollback

Disable the light module by configuration; the vanilla light path remains. The blob schema is
changed only by a superseding ADR. The override is test-only and never enables production.

## Follow-up work

- Controlled end-to-end light benchmark; then the per-version `LIGHT` feature-bit decision.
- A richer occlusion table for partial shapes once profiling shows the simple eligibility set covers
  too little.
- Wrapper/edge fusion and SkyLight as later, separate work.
