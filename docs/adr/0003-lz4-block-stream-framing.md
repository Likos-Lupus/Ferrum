# ADR-0003: Codec LZ4 uses block-stream framing

- Status: Accepted
- Date: 2026-09-23
- Affected modules: FerrumCodec

## Context

Region files are LZ4-compressed using `LZ4BlockInputStream` / `LZ4BlockOutputStream`, which frame
data as a **block stream**: a specific magic value, per-block headers, block lengths, a trailing
checksum, and an end block. Producing or consuming a raw LZ4 block alone is not sufficient for
compatibility.

## Decision

Rust LZ4 support targets the vanilla block-stream framing, not raw LZ4 blocks.

- Parse and generate the same magic, block headers, lengths, checksum field, and end block.
- Define the rule that selects compressed blocks versus raw/incompressible blocks.
- Treat vanilla-to-Ferrum and Ferrum-to-vanilla bidirectional reads as a release-blocking test.

## Alternatives considered

- Raw LZ4 block compression only: rejected; it does not interoperate with vanilla region files.
- Delegating all LZ4 to the existing Java library: kept as the fallback path, not the fast path.

## Performance evidence

No performance commitment in this record. The LZ4 stream benchmark measures encode/decode throughput
and allocation before enabling the native path.

## Correctness / compatibility impact

Bidirectional byte compatibility with vanilla block streams is mandatory. Fuzzing must cover random
bytes, truncated blocks, checksum errors, abnormal lengths, and compression bombs.

## Rollback

Disable the Codec LZ4 fast path per module via configuration; the Java `lz4-java` path remains. The
decision itself is changed only by a superseding ADR.

## Follow-up work

- Capture a vanilla block-stream corpus as golden assets.
- Implement and fuzz the Rust encoder/decoder in both directions.
