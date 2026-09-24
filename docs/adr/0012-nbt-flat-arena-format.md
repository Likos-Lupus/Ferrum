# ADR-0012: NBT flat arena format and entry points

- Status: Accepted
- Date: 2026-09-24
- Affected modules: FerrumNbt

## Context

FerrumNbt moves NBT parsing and encoding out of the JVM. The native kernel must never hold Java
object references (ADR-0001 AI-02), so an interchange representation is needed between a complete
byte buffer and a complete NBT tree. An arena node table is required, and its concrete layout must
be frozen before coding. This record freezes that layout, its ownership rules, and the entry points
that read and write it.

The representation must support bounds-checked access, migration, and fuzzing, and must allow a
legitimate root node at index `0`; therefore failures are reported through status codes, never by a
sentinel node index.

## Decision

### Ownership and lifetime

- The arena is a **caller-provided, caller-owned** byte buffer (typically a thread-local
  `NativeScratch` segment per ADR-0004). The native kernel writes into it and retains nothing past
  the call.
- The arena is not shared across threads unless the caller provides a shareable buffer; the kernel
  adds no locking.
- All offsets are `u32` byte offsets relative to the arena start. The format is little-endian.
- The root is identified by an explicit index; index `0` is valid.

### Two-pass / growth protocol

- `ferrum_nbt_parse*` writes the arena only when `arena_cap` is sufficient. When it is not, the call
  writes nothing meaningful, returns `FERRUM_ERR_BUFFER_TOO_SMALL`, and reports the required byte
  size through `used_or_required`.
- The caller sizes a thread-local scratch generously, calls once, and on `BUFFER_TOO_SMALL` grows
  the scratch to the required size and retries **at most once** (ADR-0001 FD-07). A second failure
  is an internal error.
- `arena == NULL` with `arena_cap == 0` is a valid sizing query and must not be dereferenced.

### Arena v1 layout

Header (32 bytes):

| Offset | Type  | Field                                     |
|-------:|-------|-------------------------------------------|
|      0 | `u32` | magic `0x544E4246` ("FBNT" little-endian) |
|      4 | `u16` | format version (`1`)                      |
|      6 | `u16` | header size (`32`)                        |
|      8 | `u32` | arena size (used or required bytes)       |
|     12 | `u32` | root node index                           |
|     16 | `u32` | node region offset                        |
|     20 | `u32` | node count                                |
|     24 | `u32` | string region offset (UTF-16 code units)  |
|     28 | `u32` | string region length in code units        |

Node record (32 bytes, 4-byte aligned):

| Offset | Type  | Field                                                                                       |
|-------:|-------|---------------------------------------------------------------------------------------------|
|      0 | `u8`  | tag type id (NBT ids `0..12`, see `TagTypes`)                                               |
|      1 | `u8`  | flags: bit0 = name present, bits1..7 reserved                                               |
|      2 | `u16` | list element tag id (only meaningful for `ListTag`)                                         |
|      4 | `u32` | name offset (byte offset into the string region)                                            |
|      8 | `u32` | name length (UTF-16 code units)                                                             |
|     12 | `u32` | payload offset (byte offset for scalar/array/string data; `0` when unused)                  |
|     16 | `u32` | payload length (scalars: bytes; arrays: element count; string: code units; `0` when unused) |
|     20 | `u32` | child region offset (byte offset into the child-index region)                               |
|     24 | `u32` | child count                                                                                 |
|     28 | `u32` | reserved (must be `0`)                                                                      |

Child-index region: `u32` node indices, in source order.

String region: `u16` UTF-16 code units. Strings are stored as decoded code units (not raw MUTF-8) so
that lone surrogates survive and the Java materializer can build a `String` directly; the writer
re-encodes to MUTF-8 (ADR-0002).

## Alternatives considered

- Storing raw MUTF-8 bytes for strings: rejected; Java materialization would need a second MUTF-8
  decoder, and byte offsets would not map to `String` code units.
- A pointer-based representation: rejected; offsets keep the arena relocatable and fuzz-friendly
  (plan §3).
- A recursive in-memory Rust tree: rejected; it allocates per node and duplicates the arena without
  benefit.
- Using `0` as a failure root index: rejected; `0` is a valid node index.

## Performance evidence

No performance commitment in this record. The format is validated by the NBT kernel benchmarks and
the FFM crossover benchmark before any fast path is enabled by default.

## Correctness / compatibility impact

The arena is internal to Ferrum and does not change the NBT binary format. Semantic equality with
vanilla is established by differential tests against `NbtIo`/`CompoundTag`; byte identity is only
required for canonically ordered golden corpora (plan §7.3).

## Rollback

The format version is carried in the header. A breaking layout change increments the version and the
ABI feature gate; the Java path remains the fallback.

## Follow-up work

- Implement the arena builder/reader, parser, and writer in `native/ferrum-native/src/nbt/`.
- Add the flat-arena golden corpus and the fuzz target.
