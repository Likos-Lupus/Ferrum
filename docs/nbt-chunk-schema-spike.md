# NBT chunk-schema fast path: spike outcome

- Status: No-Go for this increment (deferred)
- Date: 2026-09-24
- Scope: FerrumNbt chunk load/save path on Minecraft 1.21.1 and 26.1.2

## Question

After the generic buffer fast path (parse/encode of a complete NBT buffer), does the chunk load/save
path still spend most of its time materializing a `CompoundTag`, so that a fixed-schema fast path
would pay off?

## Vanilla chunk path (reference)

- 1.21.1: `RegionFile` → `NbtIo.readCompressed(InputStream, NbtAccounter)` (GZIP) → `NbtIo.read` →
  `ChunkSerializer.read(ServerLevel, PoiManager, RegionStorageInfo, ChunkPos, CompoundTag)`. Save
  reverses through `ChunkSerializer.write(...)` → `NbtIo.write` → `writeCompressed`.
- 26.1.2: the same compressed read, then
  `SerializableChunkData.parse(LevelHeightAccessor, PalettedContainerFactory, CompoundTag)` and
  `SerializableChunkData.write()` / `copyOf(ServerLevel, ChunkAccess)`.

Both versions decompress with GZIP; the LZ4 region codec is a separate module. The chunk tag is a
`CompoundTag` whose sections, palettes, heightmaps, and block entities are still built as Java
objects by the serializer; the generic parse only replaces the byte→tag step.

## Findings

- The generic parse path already removes the Java `DataInputStream`/`readUTF` byte work, but
  `ChunkSerializer`/`SerializableChunkData` still allocate the full object graph (palettes, section
  containers, heightmaps) on every load. A native fixed-schema path would have to reimplement that
  serialization contract per version, and the two versions differ in both host class and tag shape.
- Region files mix GZIP and optional LZ4. Any native chunk fast path is coupled to the codec module
  and to region framing, so it cannot be delivered before the codec module lands.
- The chunk-schema adapters (`ChunkSerializer` on 1.21.1, `SerializableChunkData` on 26.1.2) are
  structurally different, so they need independent implementations plus a differential test each.

## Decision

Do not enable a chunk-schema fast path in this increment. The generic buffer path remains the only
NBT fast path, and it is itself gated by the performance thresholds below. Revisit the chunk path
only when all of the following hold:

1. The generic NBT buffer path passes its kernel and end-to-end gates.
2. The region LZ4 codec module is stable and the region framing is available natively.
3. An in-game profile of chunk load/save shows materialization remains the dominant cost after the
   generic path.

## How to measure (when revisited)

- Fixed seed and world copy; load and save 1k/10k chunks; record wall-clock, P95, and JFR
  allocations for vanilla versus Ferrum on the same machine.
- Confirm zero semantic difference on chunk data (this is a correctness gate, not a performance one)
  before accepting any schema fast path.

## Consequence

The `NBT_CHUNK_SCHEMA` capability stays unadvertised. No new default-enabled behavior is added by
this spike.
