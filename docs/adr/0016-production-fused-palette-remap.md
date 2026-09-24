# ADR-0016: Production fused palette remap

- Status: Proposed
- Date: 2026-09-24
- Affected modules: FerrumPalette, FerrumCore (FFM bindings)

## Context

The F-055 spike (`docs/palette-remap-spike.md`) measured four paths. The composed native path
(existing `ferrum_palette_unpack` + Java map + `ferrum_palette_pack`) is always slower than the Java
`SimpleBitStorage` baseline (0.22x-0.94x) because it materializes the intermediate value array and
crosses FFM twice. A fused test-hook kernel, measured through FFM, is faster than Java for
section-sized remaps (1.11x-2.24x at 4096 values) and slower below that (0.24x at 257 values). The
value therefore lies in a single-pass fused remap with one FFM crossing, not in the composed
symbols.

## Decision

Define a production fused remap as a follow-up:

- Add a stable, additive ABI symbol `ferrum_palette_remap(in_data, in_len, bits_in, value_count,
  map, map_len, bits_out, out_data, out_len, written_or_required) -> i32`. It performs one pass:
  extract a value at `bits_in`, look it up in `map`, range-check it against `bits_out`, and pack it,
  with no intermediate value array. The ABI version stays 1 because the change is additive (ADR-0013
  precedent).
- Eligibility requires: runtime available, palette module enabled and advertised, `bits_in`/
  `bits_out`
  in `[1, 31]` (or `[1, 32]` with an unsigned map check), `value_count` at or above a measured
  threshold, and a map table that is valid for every source value. Anything else falls back to the
  Java `PalettedContainer` path.
- Wire it at the vanilla remap boundary (`PalettedContainer.onResize` and the version-specific
  reencode helper), keeping palette storage, thread ownership, and world state in Java. Version
  differences stay in the module's adapters, not in the kernel.
- Keep the fast path behind the palette feature bit. Enabling it for release requires the M3 gate:
  JMH kernel numbers plus an in-game `PalettedContainer` measurement that clears the thresholds in
  ADR-0007/ADR-0008.
- Remove or rework the temporary `ferrum_test_palette_remap` hook when the production symbol lands;
  the hook must not become a de-facto production interface.

## Alternatives considered

- Ship the composed path (existing symbols): rejected; the spike shows it is a net loss.
- Keep the fused path as the test hook: rejected; a test-hooks-only symbol must not be a production
  interface.
- Unpack in Rust, map in Java, pack in Rust but with one shared buffer: rejected; still two
  crossings and still materializes the value array unless the kernel fuses the map.

## Performance evidence

Spike, release build, WSL2 x86_64, JDK 25, best of 100 (fused-through-FFM vs Java baseline):
1.11x-2.24x at 4096 values for widths 4->5, 5->4, 4->8, 8->4, 5->5, 3->4, 6->5; 0.24x at 257 values.
Kernel-only fused vs composed: 0.91x-2.01x. No end-to-end game measurement yet.

## Correctness / compatibility impact

Packed output must be byte-for-byte identical to the Java `SimpleBitStorage` remap; the spike's
12-case corpus and property tests already assert this. Fuzzing must cover random maps, missing map
entries, and values that overflow `bits_out`.

## Rollback

Disable the palette remap fast path by configuration; the Java `PalettedContainer` path remains. The
production symbol is changed only by a superseding ADR.

## Follow-up work

- Follow-up task: implement `ferrum_palette_remap`, production bindings, eligibility/fallback, and
  the `onResize`/reencode integration for both targets.
- Follow-up task: remove or rework `ferrum_test_palette_remap` once the production symbol exists.
- Follow-up task: JMH and in-game remap benchmarks, then the feature-bit decision.
