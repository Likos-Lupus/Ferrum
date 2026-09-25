# ADR-0015: Palette bulk pack/unpack uses the `SimpleBitStorage` layout

- Status: Accepted
- Date: 2026-09-24
- Affected modules: FerrumPalette, FerrumCore (FFM bindings)

## Context

Palette ids are packed into `long[]` words by vanilla `net.minecraft.util.SimpleBitStorage`.
Offloading bulk pack/unpack to Rust only helps if the native kernel reproduces that layout exactly,
including its indexing oddities, and if both game versions agree on it.

Bytecode inspection of `SimpleBitStorage` in 1.21.1 and 26.1.2 shows identical behaviour:

- the constructor accepts `bits` in `[1, 32]`, with `mask = (1L << bits) - 1`;
- `valuesPerLong = (char) (64 / bits)`, so values never cross a word boundary;
- `data.length = ceil(size / valuesPerLong)`;
- `unpack(int[])` extracts `valuesPerLong` values per full word, low bits first, then a tail word
  with the remaining values; the unused high bits of a word stay zero when `valuesPerLong * bits`
  is less than 64;
- `bits == 0` is handled by `ZeroBitStorage`, a different class.

## Decision

The native palette bulk entries implement exactly that layout and freeze these ABI semantics:

- `ferrum_palette_unpack(data, data_len, bits, value_count, out_values, out_len)`:
    - `bits` in `[1, 32]`, else `INVALID_ARGUMENT`;
    - `data_len` must be at least `ceil(value_count / values_per_long(bits))`, else
      `INVALID_ARGUMENT` (a caller contract violation, not external corruption);
    - `out_len` must be at least `value_count`, else `BUFFER_TOO_SMALL`;
    - values are masked extraction, low bits first, tail word only for the remaining values.
- `ferrum_palette_pack(values, value_count, bits, out_data, out_len)`:
    - `bits` in `[1, 32]`, else `INVALID_ARGUMENT`;
    - `out_len` must be at least `ceil(value_count / values_per_long(bits))`, else
      `BUFFER_TOO_SMALL`;
    - a value larger than the width mask is `INVALID_ARGUMENT`, matching `SimpleBitStorage.set`'s
      range check;
    - output words are written whole, so the unused high bits of the final word stay zero.
- The layout is defined on the numeric `u64` word. The kernel never reinterprets host memory bytes,
  so results are identical on little-endian and big-endian targets.
- `bits == 0` is not accepted; `ZeroBitStorage` short-circuits in Java and is never routed here.
- Native entry points reject null pointers for non-empty buffers and wrap their bodies in the shared
  no-unwind guard.

The fast path is wired at `SimpleBitStorage.unpack(int[] only)`; single-value `get`/`set` stay in
Java, and every other `BitStorage` implementation is left untouched. Eligibility additionally
requires the runtime to be available, the palette module enabled and advertised, the value count at
or above the configured `minValues`, and a caller output array large enough that the vanilla
exception semantics are preserved.

## Alternatives considered

- Re-deriving the layout from `valuesPerLong` without the tail rule: rejected; it breaks non
  power-of-two widths such as 3, 5, 6, and 7.
- Accepting `bits == 0`: rejected; it duplicates `ZeroBitStorage` and adds an unreachable branch.
- Byte-level interpretation of `long[]`: rejected; it would make the result endianness-dependent.

## Performance evidence

The bulk kernel is measured by `paletteBenchmark`. At the time of this record the kernel does not
beat vanilla consistently at the 4096-entry section size, so the `PALETTE` feature bit stays clear;
see `docs/palette-performance-report.md`. Enablement requires the kernel plus an in-game measurement
to clear the M3 gate.

## Correctness / compatibility impact

A Java-authored `SimpleBitStorage` corpus (`native/ferrum-native/tests/golden/palette/`, a compact
representative set of widths and tail shapes) checks unpack and pack byte-for-byte without needing
Minecraft; the native-tagged differential test additionally compares every width 1..32 and several
sizes against the live vanilla class on a real target. A 32-bit value with the top bit set cannot be
authored through `SimpleBitStorage.set` (which takes a signed `int`); that range is covered by the
Rust property tests instead.

## Rollback

Disable the palette module by configuration; the vanilla `SimpleBitStorage` path remains. The layout
decision itself is changed only by a superseding ADR.

## Follow-up work

- Outcome of the F-055 remap spike is recorded in `docs/palette-remap-spike.md`.
- Kernel and in-game benchmarks before any default enablement.
