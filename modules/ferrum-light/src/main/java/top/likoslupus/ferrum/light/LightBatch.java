package top.likoslupus.ferrum.light;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ShortOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import top.likoslupus.ferrum.light.mixin.LayerLightSectionStorageAccessor;
import top.likoslupus.ferrum.light.mixin.LightEngineAccessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;

/**
 * Builds the block-light snapshot blob and commits the native output (ADR-0018).
 *
 * <p>Every required property is validated while the snapshot is flattened. If any section is
 * missing, any state has a partial or otherwise unrepresentable occlusion shape, the batch is too
 * large, or the pending queues are non-empty, the whole batch is rejected and the vanilla path
 * runs. Nothing is committed when the build fails.
 */
@SuppressWarnings("Convert2streamapi")
final class LightBatch {

    private static final int CELLS = 4096;
    private static final int PACKED_LIGHT_BYTES = CELLS >>> 1;
    private static final int MAX_PALETTE = 256;
    private static final int MAX_QUEUE = 1 << 20;
    private static final int PROPERTY_CODE_COUNT = 1 << 9;
    private static final int OUTPUT_HEADER = 16;
    private static final int OUTPUT_SECTION = 16 + PACKED_LIGHT_BYTES;

    /** The reason the last {@link #build} call was rejected, for diagnostics and tests. */
    static @Nullable String lastFailure;

    private LightBatch() {
    }

    static byte @Nullable [] build(
            LightEngineAccessor engine,
            LayerLightSectionStorageAccessor storage,
            LightChunkGetter chunkSource,
            int maxSections
    ) {
        lastFailure = null;

        var checks = engine.ferrum$blockNodesToCheck();
        if (checks.isEmpty()) {
            return reject("no checks");
        }
        if (!engine.ferrum$decreaseQueue().isEmpty()
                || !engine.ferrum$increaseQueue().isEmpty()
        ) {
            return reject("pending queues");
        }
        if (checks.size() > MAX_QUEUE) {
            return reject("too many checks");
        }
        if (maxSections <= 0) {
            return reject("invalid max sections");
        }

        var sectionKeys = new LongOpenHashSet();
        for (var iterator = checks.iterator(); iterator.hasNext(); ) {
            var packed = iterator.nextLong();
            var sx = SectionPos.blockToSectionCoord(BlockPos.getX(packed));
            var sy = SectionPos.blockToSectionCoord(BlockPos.getY(packed));
            var sz = SectionPos.blockToSectionCoord(BlockPos.getZ(packed));
            for (var dx = -1; dx <= 1; dx++) {
                for (var dy = -1; dy <= 1; dy++) {
                    for (var dz = -1; dz <= 1; dz++) {
                        if (sectionKeys.add(SectionPos.asLong(sx + dx, sy + dy, sz + dz))
                                && sectionKeys.size() > maxSections
                        ) {
                            return reject("too many sections: " + sectionKeys.size());
                        }
                    }
                }
            }
        }

        var level = chunkSource.getLevel();
        var palette = new IntArrayList();
        var paletteLookup = new short[PROPERTY_CODE_COUNT];
        var statePaletteIds = new Reference2ShortOpenHashMap<BlockState>();
        statePaletteIds.defaultReturnValue((short) -1);
        var sections = new ArrayList<SectionData>(sectionKeys.size());
        var mutable = new BlockPos.MutableBlockPos();

        for (var iterator = sectionKeys.iterator(); iterator.hasNext(); ) {
            var sectionNode = iterator.nextLong();
            if (!storage.ferrum$storingLightForSection(sectionNode)) {
                continue;
            }

            var layer = storage.ferrum$getDataLayer(sectionNode, true);
            if (layer == null) {
                return reject("no data layer");
            }

            var sx = SectionPos.x(sectionNode);
            var sy = SectionPos.y(sectionNode);
            var sz = SectionPos.z(sectionNode);
            var chunk = chunkSource.getChunkForLighting(sx, sz);
            if (chunk == null) {
                return reject("no chunk at " + sx + "," + sz);
            }

            var homogeneous = layer.isDefinitelyHomogenous();
            var defaultLevel = homogeneous
                    ? layer.get(0, 0, 0)
                    : 0;
            var lightOn = storage.ferrum$lightOnInSection(sectionNode);
            var props = new short[CELLS];
            var levels = homogeneous
                    ? null
                    : expandLevels(layer);
            var baseX = sx << 4;
            var baseY = sy << 4;
            var baseZ = sz << 4;

            for (var cell = 0; cell < CELLS; cell++) {
                var x = cell & 15;
                var z = (cell >>> 4) & 15;
                var y = cell >>> 8;
                mutable.set(baseX + x, baseY + y, baseZ + z);

                var state = chunk.getBlockState(mutable);
                var paletteId = statePaletteIds.getShort(state);
                if (paletteId < 0) {
                    var propertyCode = propertyCodeOf(state, level, mutable);
                    if (propertyCode < 0) {
                        return reject("unsupported state " + state);
                    }

                    var encodedPaletteId = Short.toUnsignedInt(paletteLookup[propertyCode]);
                    if (encodedPaletteId == 0) {
                        if (palette.size() >= MAX_PALETTE) {
                            return reject("palette full");
                        }

                        paletteId = (short) palette.size();
                        palette.add(propertyCode);
                        paletteLookup[propertyCode] = (short) (paletteId + 1);
                    } else {
                        paletteId = (short) (encodedPaletteId - 1);
                    }
                    statePaletteIds.put(state, paletteId);
                }
                props[cell] = paletteId;
            }

            sections.add(new SectionData(
                    sx,
                    sy,
                    sz,
                    lightOn,
                    homogeneous,
                    defaultLevel,
                    props,
                    levels
            ));
        }

        if (sections.isEmpty()) {
            return reject("no storing sections");
        }

        var size = 32L + (long) palette.size() * 4L;
        size += sections.stream()
                .mapToLong(section ->
                        20L + CELLS * 2L + (
                                section.levels != null
                                        ? CELLS
                                        : 0L
                        )
                ).sum();
        size += (long) checks.size() * 12L;
        if (size > Integer.MAX_VALUE) {
            return reject("snapshot too large");
        }

        var buffer = ByteBuffer.allocate((int) size).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 'F').put((byte) 'B').put((byte) 'L').put((byte) 'T');
        buffer.put((byte) 1).put((byte) 0).putShort((short) 0);
        buffer.putInt(sections.size())
                .putInt(palette.size())
                .putInt(0)
                .putInt(0)
                .putInt(checks.size())
                .putInt(0);

        for (var index = 0; index < palette.size(); index++) {
            var propertyCode = palette.getInt(index);
            buffer.put((byte) (propertyCode & 15))
                    .put((byte) ((propertyCode >>> 4) & 15))
                    .put((byte) ((propertyCode >>> 8) & 1))
                    .put((byte) 0);
        }

        for (var section : sections) {
            buffer.putInt(section.x).putInt(section.y).putInt(section.z).putInt(0);
            buffer.put((byte) section.defaultLevel)
                    .put((byte) (
                            section.homogeneous
                                    ? 0
                                    : 1
                    ))
                    .put((byte) (
                            section.lightOn
                                    ? 1
                                    : 0
                    ))
                    .put((byte) 0);
            for (var prop : section.props) {
                buffer.putShort(prop);
            }
            if (section.levels != null) {
                buffer.put(section.levels);
            }
        }

        for (var iterator = checks.iterator(); iterator.hasNext(); ) {
            var packed = iterator.nextLong();
            buffer.putInt(BlockPos.getX(packed))
                    .putInt(BlockPos.getY(packed))
                    .putInt(BlockPos.getZ(packed));
        }
        return buffer.array();
    }

    private static byte @Nullable [] reject(String reason) {
        lastFailure = reason;
        return null;
    }

    private static byte[] expandLevels(DataLayer layer) {
        var packed = layer.getData();
        var levels = new byte[CELLS];
        for (var index = 0; index < PACKED_LIGHT_BYTES; index++) {
            var value = packed[index] & 0xFF;
            var cell = index << 1;
            levels[cell] = (byte) (value & 15);
            levels[cell + 1] = (byte) (value >>> 4);
        }
        return levels;
    }

    private static int propertyCodeOf(
            BlockState state,
            BlockGetter level,
            BlockPos pos
    ) {
        var emptyShape = !state.canOcclude() || !state.useShapeForLightOcclusion();
        if (!emptyShape
                && !state.isCollisionShapeFullBlock(level, pos)
        ) {
            return -1;
        }

        var opacity = opacityOf(state, level, pos);
        var emission = emissionOf(state, level, pos);
        if ((opacity & ~15) != 0 || (emission & ~15) != 0) {
            return -1;
        }
        return opacity | (emission << 4) | (
                emptyShape
                        ? 1 << 8
                        : 0
        );
    }

    @SuppressWarnings({"UnusedVariable", "unused"})
    private static int opacityOf(
            BlockState state,
            BlockGetter level,
            BlockPos pos
    ) {
        //? if >=26.1 {
        return Math.max(1, state.getLightDampening());
        //?} else {
        /*return Math.max(1, state.getLightBlock(level, pos));*/
        //?}
    }

    @SuppressWarnings({"UnusedVariable"})
    private static int emissionOf(
            BlockState state,
            BlockGetter level,
            BlockPos pos
    ) {
        return state.getLightEmission();
    }

    static int apply(
            LightEngine<?, ?> engine,
            LightEngineAccessor accessor,
            LayerLightSectionStorageAccessor storage,
            byte[] output
    ) {
        if (output.length < OUTPUT_HEADER
                || output[0] != 'F'
                || output[1] != 'B'
                || output[2] != 'L'
                || output[3] != 'O'
                || output[4] != 1
                || output[5] != 0
                || output[6] != 0
                || output[7] != 0
        ) {
            return -1;
        }

        var buffer = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN);
        var changed = buffer.getInt(8);
        var processed = buffer.getInt(12);
        if (changed < 0
                || processed < 0
                || OUTPUT_HEADER + (long) changed * OUTPUT_SECTION > output.length
        ) {
            return -1;
        }

        var layers = new DataLayer[changed];
        var sectionKeys = new long[changed];
        var offset = OUTPUT_HEADER;
        for (var index = 0; index < changed; index++) {
            var sx = buffer.getInt(offset);
            var sy = buffer.getInt(offset + 4);
            var sz = buffer.getInt(offset + 8);
            var sectionKey = SectionPos.asLong(sx, sy, sz);
            var layer = storage.ferrum$getDataLayerToWrite(sectionKey);
            if (layer == null) {
                return -1;
            }
            layers[index] = layer;
            sectionKeys[index] = sectionKey;
            offset += OUTPUT_SECTION;
        }

        var affected = storage.ferrum$sectionsAffectedByLightUpdates();
        offset = OUTPUT_HEADER;
        for (var index = 0; index < changed; index++) {
            var target = layers[index].getData();
            if (target.length != PACKED_LIGHT_BYTES) {
                return -1;
            }
            System.arraycopy(
                    output,
                    offset + 16,
                    target,
                    0,
                    PACKED_LIGHT_BYTES
            );

            var sectionKey = sectionKeys[index];
            var sx = SectionPos.x(sectionKey);
            var sy = SectionPos.y(sectionKey);
            var sz = SectionPos.z(sectionKey);
            for (var dx = -1; dx <= 1; dx++) {
                for (var dy = -1; dy <= 1; dy++) {
                    for (var dz = -1; dz <= 1; dz++) {
                        affected.add(SectionPos.asLong(sx + dx, sy + dy, sz + dz));
                    }
                }
            }
            offset += OUTPUT_SECTION;
        }

        accessor.ferrum$blockNodesToCheck().clear();
        accessor.ferrum$decreaseQueue().clear();
        accessor.ferrum$increaseQueue().clear();
        storage.ferrum$markNewInconsistencies(engine);
        storage.ferrum$swapSectionMap();
        return processed;
    }

    @SuppressWarnings("ArrayRecordComponent")
    private record SectionData(
            int x,
            int y,
            int z,
            boolean lightOn,
            boolean homogeneous,
            int defaultLevel,
            short[] props,
            byte @Nullable [] levels
    ) {

    }

}
