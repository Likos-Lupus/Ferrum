# ADR-0013: NBT wire forms and the additive `*_any` entry points

- Status: Accepted
- Date: 2026-09-24
- Affected modules: FerrumNbt

## Context

Vanilla encodes NBT in two distinct wire forms, and both are used on hot paths:

- **Named form** (`NbtIo.read(DataInput, NbtAccounter)` / `NbtIo.write(CompoundTag, DataOutput)`):
  a type byte, then a name (`writeUTF`, the empty string for the root), then the payload. Compressed
  chunk files use this form through `NbtIo.readCompressed`/`writeCompressed`.
- **Any-tag form** (`NbtIo.readAnyTag` / `NbtIo.writeAnyTag`): a type byte, then the payload, with
  **no name**. `FriendlyByteBuf.readNbt`/`writeNbt` use this form for network payloads.

The frozen ABI header declares a single `ferrum_nbt_parse`/`ferrum_nbt_write` pair. One pair cannot
serve both forms without an ambiguous auto-detection heuristic. Adding an optional parameter to an
already-declared symbol would be a breaking ABI change requiring a version bump.

## Decision

Keep `ferrum_nbt_parse`/`ferrum_nbt_write` as the **named form** (matching `NbtIo.read`/
`NbtIo.write`)
and add two **additive** symbols for the any-tag form:

```c
int32_t ferrum_nbt_parse_any(
    const uint8_t* src, size_t src_len,
    const struct FerrumLimits* limits,
    uint8_t* arena, size_t arena_cap,
    uint32_t* root_index,
    size_t* used_or_required,
    size_t* consumed);

int32_t ferrum_nbt_write_any(
    const uint8_t* arena, size_t arena_len,
    uint32_t root_index,
    uint8_t* dst, size_t dst_cap,
    size_t* written_or_required);
```

The any-form parser reports the number of input bytes it consumed through `size_t* consumed`, so the
Java network adapter can advance a `ByteBuf` reader index exactly as `readAnyTag` would. The named
form (`ferrum_nbt_parse`/`ferrum_nbt_write`) is unchanged and treats the whole input as one
document; interception of the named read path is therefore deferred to the chunk-schema work, where
the whole buffer is guaranteed to be one tag.

Both pairs share the same flat arena (ADR-0012) and the same MUTF-8 codec (ADR-0002). The only
difference is the presence of the root name field. Adding symbols is an additive ABI change: the ABI
version stays `1`, and callers discover support through symbol presence plus the advertised feature
bit. The header, Rust exports, Java bindings, and `scripts/check-abi.sh` must be updated together.

The named-form writer mirrors `NbtIo.write`'s `StringFallbackDataOutput` behavior at the adapter
level: if a string cannot be MUTF-8 encoded within the 65535-byte limit, the native writer returns
`FERRUM_ERR_LIMIT_EXCEEDED` and the Java adapter falls back to the vanilla path (which writes `""`).
The any-form writer returns the error directly, matching `writeAnyTag`, whose caller throws.

## Alternatives considered

- Auto-detecting the form from the bytes: rejected; the two forms are not reliably distinguishable
  without knowing whether a name field is present.
- Bumping the ABI to `2` and adding a `flags` parameter to the existing symbols: rejected; an
  additive symbol avoids invalidating the already-frozen ABI for a purely local format detail.
- Handling the any-tag form only in Java: rejected; it would exclude the network read/write path
  from the native kernel and duplicate the MUTF-8/byte work in Java.

## Performance evidence

No performance commitment in this record. The any-form path is measured by the NBT kernel and
network benchmarks before it is enabled by default.

## Correctness / compatibility impact

No change to the NBT binary format. Both forms are covered by differential tests against vanilla
`NbtIo` and `FriendlyByteBuf`.

## Rollback

Disable the NBT module per configuration; the vanilla path handles both forms. The symbol surface is
additive, so removing the symbols later is a new ADR, not a silent edit.

## Follow-up work

- Update `include/ferrum_abi.h`, the Rust exports, `NativeBindings`, and `check-abi.sh`.
- Add differential tests for both wire forms.
