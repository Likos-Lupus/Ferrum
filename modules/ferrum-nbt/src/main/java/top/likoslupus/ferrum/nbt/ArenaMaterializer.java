package top.likoslupus.ferrum.nbt;

import net.minecraft.nbt.*;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Rebuilds a vanilla {@link Tag} tree from the flat NBT arena (ADR-0012).
 *
 * <p>This runs on the read fast path. It uses only constructors that exist on every supported
 * Minecraft version, so no per-version adapter is required for materialization.
 */
public final class ArenaMaterializer {

    private static final int ARENA_MAGIC = 0x544E_4246;
    private static final int HEADER_SIZE = 32;
    private static final int NODE_SIZE = 32;

    // NBT payload scalars and arrays are stored big-endian; arena structure and string code units
    // are little-endian.

    private static final ValueLayout.OfShort BE_SHORT =
            ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfInt BE_INT =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfLong BE_LONG =
            ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfFloat BE_FLOAT =
            ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);
    private static final ValueLayout.OfDouble BE_DOUBLE =
            ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

    private static final int TYPE_END = 0;
    private static final int TYPE_BYTE = 1;
    private static final int TYPE_SHORT = 2;
    private static final int TYPE_INT = 3;
    private static final int TYPE_LONG = 4;
    private static final int TYPE_FLOAT = 5;
    private static final int TYPE_DOUBLE = 6;
    private static final int TYPE_BYTE_ARRAY = 7;
    private static final int TYPE_STRING = 8;
    private static final int TYPE_LIST = 9;
    private static final int TYPE_COMPOUND = 10;
    private static final int TYPE_INT_ARRAY = 11;
    private static final int TYPE_LONG_ARRAY = 12;

    private ArenaMaterializer() {
    }

    /**
     * Materializes the arena root.
     *
     * @param arena     the arena bytes
     * @param rootIndex the root node index
     *
     * @return the root tag, or {@code null} for an end tag or an invalid arena
     */
    public static @Nullable Tag materialize(MemorySegment arena, int rootIndex) {
        if (arena.byteSize() < HEADER_SIZE
                || arena.get(ValueLayout.JAVA_INT_UNALIGNED, 0L) != ARENA_MAGIC
                || (arena.get(ValueLayout.JAVA_SHORT_UNALIGNED, 4L) & 0xFFFF) != 1
        ) {
            return null;
        }

        var nodesBase = arena.get(ValueLayout.JAVA_INT_UNALIGNED, 16L);
        var nodeCount = arena.get(ValueLayout.JAVA_INT_UNALIGNED, 20L);

        return rootIndex < 0 || rootIndex >= nodeCount
                ? null
                : build(arena, nodesBase, nodeCount, rootIndex);
    }

    private static @Nullable Tag build(
            MemorySegment arena,
            int nodesBase,
            int nodeCount,
            int index
    ) {
        if (index < 0 || index >= nodeCount) {
            return null;
        }

        var offset = nodesBase + index * NODE_SIZE;
        var type = arena.get(ValueLayout.JAVA_BYTE, offset) & 0xFF;
        var payloadOffset = arena.get(ValueLayout.JAVA_INT_UNALIGNED, offset + 12L);
        var payloadLength = arena.get(ValueLayout.JAVA_INT_UNALIGNED, offset + 16L);
        var childOffset = arena.get(ValueLayout.JAVA_INT_UNALIGNED, offset + 20L);
        var childCount = arena.get(ValueLayout.JAVA_INT_UNALIGNED, offset + 24L);

        return switch (type) {
            case TYPE_END -> null;
            case TYPE_BYTE -> ByteTag.valueOf(arena.get(ValueLayout.JAVA_BYTE, payloadOffset));
            case TYPE_SHORT -> ShortTag.valueOf(arena.get(BE_SHORT, payloadOffset));
            case TYPE_INT -> IntTag.valueOf(arena.get(BE_INT, payloadOffset));
            case TYPE_LONG -> LongTag.valueOf(arena.get(BE_LONG, payloadOffset));
            case TYPE_FLOAT -> FloatTag.valueOf(arena.get(BE_FLOAT, payloadOffset));
            case TYPE_DOUBLE -> DoubleTag.valueOf(arena.get(BE_DOUBLE, payloadOffset));
            case TYPE_BYTE_ARRAY -> new ByteArrayTag(
                    arena.asSlice(payloadOffset, payloadLength).toArray(ValueLayout.JAVA_BYTE)
            );
            case TYPE_STRING -> StringTag.valueOf(readString(arena, payloadOffset, payloadLength));
            case TYPE_LIST -> buildList(
                    arena,
                    nodesBase,
                    nodeCount,
                    childOffset,
                    childCount
            );
            case TYPE_COMPOUND -> buildCompound(
                    arena,
                    nodesBase,
                    nodeCount,
                    childOffset,
                    childCount
            );
            case TYPE_INT_ARRAY -> buildIntArray(arena, payloadOffset, payloadLength);
            case TYPE_LONG_ARRAY -> buildLongArray(arena, payloadOffset, payloadLength);
            default -> null;
        };
    }

    private static ListTag buildList(
            MemorySegment arena,
            int nodesBase,
            int nodeCount,
            int childOffset,
            int childCount
    ) {
        var list = new ListTag();
        IntStream.range(0, childCount)
                .map(position -> arena.get(
                        ValueLayout.JAVA_INT_UNALIGNED,
                        childOffset + 4L * position
                ))
                .mapToObj(child -> build(arena, nodesBase, nodeCount, child))
                .filter(Objects::nonNull)
                .forEach(list::add);
        return list;
    }

    private static CompoundTag buildCompound(
            MemorySegment arena,
            int nodesBase,
            int nodeCount,
            int childOffset,
            int childCount
    ) {
        var compound = new CompoundTag();
        IntStream.range(0, childCount)
                .map(position -> arena.get(
                        ValueLayout.JAVA_INT_UNALIGNED,
                        childOffset + 4L * position
                ))
                .forEach(child -> {
                    var tag = build(arena, nodesBase, nodeCount, child);
                    if (tag == null) {
                        return;
                    }
                    compound.put(readName(arena, nodesBase, child), tag);
                });
        return compound;
    }

    private static String readName(
            MemorySegment arena,
            int nodesBase,
            int index
    ) {
        var offset = nodesBase + index * NODE_SIZE;
        var nameOffset = arena.get(ValueLayout.JAVA_INT_UNALIGNED, offset + 4L);
        var nameUnits = arena.get(ValueLayout.JAVA_INT_UNALIGNED, offset + 8L);
        return readString(arena, nameOffset, nameUnits);
    }

    private static String readString(
            MemorySegment arena,
            int offset,
            int units
    ) {
        if (units <= 0) {
            return "";
        }

        var chars = new char[units];
        IntStream.range(0, units)
                .forEach(index ->
                        chars[index] = (char) (
                                arena.get(
                                        ValueLayout.JAVA_SHORT_UNALIGNED,
                                        offset + 2L * index
                                ) & 0xFFFF
                        )
                );
        return new String(chars);
    }

    private static IntArrayTag buildIntArray(
            MemorySegment arena,
            int offset,
            int count
    ) {
        var values = IntStream.range(0, count)
                .map(index ->
                        arena.get(BE_INT, offset + 4L * index)
                )
                .toArray();
        return new IntArrayTag(values);
    }

    private static LongArrayTag buildLongArray(
            MemorySegment arena,
            int offset,
            int count
    ) {
        var values = IntStream.range(0, count)
                .mapToLong(index ->
                        arena.get(BE_LONG, offset + 8L * index)
                )
                .toArray();
        return new LongArrayTag(values);
    }

}
