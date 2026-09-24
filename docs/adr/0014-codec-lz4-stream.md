# ADR-0014: Codec LZ4 block-stream implementation and gating

- Status: Accepted
- Date: 2026-09-24
- Affected modules: FerrumCodec

## Context

ADR-0003 froze that region payloads use the lz4-java `LZ4Block` block-stream framing, not the
standard LZ4 Frame format, and that native support must interoperate with vanilla in both
directions. This record fixes how that framing is implemented, where Java intercepts it, and when
the feature may be enabled.

The framing (bytecode-verified for both supported Minecraft versions) is:

- 8-byte magic `"LZ4Block"`;
- a 21-byte header per block: magic + token + compressed length (LE u32) + decompressed length (LE
  u32) + checksum (LE u32);
- token = method (`0x10` raw, `0x20` LZ4) | level, where level encodes the block-size ceiling as
  `32 - clz(blockSize - 1) - 10` (Minecraft's default 64 KiB block size gives level 6);
- a block is stored raw when compression does not shrink it;
- (de)compressed blocks are checksummed with XXH32, seed `0x9747b28c`, over the **decompressed**
  bytes;
- a zero-length terminator block ends the stream.

`RegionFileVersion.VERSION_LZ4` wraps reads and writes with `LZ4BlockInputStream` /
`LZ4BlockOutputStream`; both Minecraft versions use the same class names and the same
`RegionFileVersion.wrap(InputStream)` / `wrap(OutputStream)` methods. `RegionFile` holds the whole
compressed chunk payload in memory before wrapping, so one native call per chunk is the natural
granularity.

## Decision

**Native implementation.** Add `lz4_flex` (with `default-features = false`, `safe-encode` and
`safe-decode`; only the raw block `compress_into`/`decompress_into` APIs, never its Frame format)
and
`xxhash-rust` (`xxh32`) as the only new Rust dependencies. Ferrum implements the `LZ4Block` framing
itself; it does not use the LZ4 Frame format.

**Checksum mask.** lz4-java's `StreamingXXHash32.asChecksum().getValue()` returns
`getValue() & 0xFFFFFFFL`, clearing the top four bits. The stored checksum is therefore
`xxh32(bytes, 0x9747b28c) & 0x0FFFFFFF`. Ferrum reproduces this mask on encode and applies it on
decode; a stream encoded with the full 32-bit checksum is **not** compatible with vanilla.

**Decoder.** Decoding validates magic, token method, length consistency, the block-size ceiling, the
checksum, and the presence of the terminator, and returns `MALFORMED_INPUT` on any violation. A
first pass sums the declared decompressed sizes so an over-large stream returns `BUFFER_TOO_SMALL`
with the required size before any decompression (decompression-bomb guard, bounded by the caller's
capacity). Decoding is allocation-free and writes into the caller-provided buffer.

**Encoder.** Blocks are at most 64 KiB, the raw/LZ4 choice matches lz4-java, and the terminator is
emitted last. Encoding uses a precomputed worst-case size so the caller can size its buffer.

**Java interception.** A single `RegionFileVersion` mixin cancels `wrap(InputStream)` /
`wrap(OutputStream)` at HEAD **only when the receiver is `VERSION_LZ4`**. Java keeps region sectors,
chunk length and version bytes, and file locks; native sees only the LZ4 payload. On any native
ineligibility or failure the buffered bytes are handed to the vanilla `lz4-java` stream, so valid
and corrupt inputs behave exactly like vanilla.

**Configuration.** The codec module owns its options through the generic `ModuleSettings.options`
map: `lz4` (default true), `accelerateExistingLz4` (default true), and `preferLz4ForNewWrites`
(default false). The codec module projects these onto a typed `CodecOptions` record.

**Format selection is separate from native availability.** `preferLz4ForNewWrites` is parsed but
does **not** change Minecraft's region compression selection. Whether Ferrum may choose LZ4 for new
writes is a storage-policy decision for a later ADR. Native availability must only decide whether
LZ4 is implemented by Rust or by `lz4-java`, never which on-disk format is written.

**Gating.** The `CODEC_LZ4` feature bit stays clear until the kernel and end-to-end performance
gates both pass. Until then the fast path is available but inactive by default, and regions are
handled by vanilla `lz4-java`. Correctness is exercised by the `@Tag("native")` differential tests,
which call the native codec directly.

## Alternatives considered

- Raw LZ4 blocks only: rejected; incompatible with vanilla region files (ADR-0003).
- Delegate all LZ4 to `lz4-java`: kept as the fallback, not the fast path.
- Hand-write XXH32: rejected; the framing checksum is a disk-format compatibility surface, and a
  subtle error in seed, overflow, or tail handling would break interoperability.
- Use the full 32-bit XXH32 checksum: rejected; lz4-java stores the 28-bit-masked value, so the full
  checksum does not interoperate.
- Use the LZ4 Frame format: rejected; vanilla does not use it.
- Drive the region format from native availability: rejected; it would let the same configuration
  produce different on-disk formats on different machines.

## Performance evidence

Kernel evidence is recorded in `docs/codec-performance-report.md`. On the development host the
native path clears the 1.25x kernel gate at chunk sizes of 64 KiB and above (decode ~1.7-1.85x,
encode ~2.2-3.0x) and regresses at 4 KiB, so a minimum batch threshold is required. The end-to-end
region gate has not been measured; the feature bit therefore stays clear.

## Correctness / compatibility impact

Vanilla-to-Ferrum and Ferrum-to-vanilla reads are covered by differential tests over a committed
`lz4-java` corpus and by direct round-trips in both directions. Corrupt, truncated,
checksum-failing, and over-large streams are rejected without crashing. Adding the two
`ferrum_lz4_block_stream_*`
implementations does not change the ABI surface or the ABI version.

## Rollback

Disable the codec module per configuration or leave the `CODEC_LZ4` feature bit clear; the vanilla
`lz4-java` path remains. The framing decision itself is changed only by a superseding ADR.

## Follow-up work

- Replace the interim kernel benchmark with JMH and measure the end-to-end region scenario.
- Derive `minBatch` from the crossover and set `CODEC_LZ4` only after both gates pass.
- Add a region compression-selection policy ADR covering `server.properties` versus `ferrum.json`
  precedence and the cross-loader, cross-version injection points.
