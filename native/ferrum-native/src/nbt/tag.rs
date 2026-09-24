//! NBT tag type identifiers and small helpers.
//!
//! The numeric ids match vanilla `TagTypes`: `EndTag, ByteTag, ShortTag, IntTag, LongTag, FloatTag,
//! DoubleTag, ByteArrayTag, StringTag, ListTag, CompoundTag, IntArrayTag, LongArrayTag`.

pub const TAG_END: u8 = 0;
pub const TAG_BYTE: u8 = 1;
pub const TAG_SHORT: u8 = 2;
pub const TAG_INT: u8 = 3;
pub const TAG_LONG: u8 = 4;
pub const TAG_FLOAT: u8 = 5;
pub const TAG_DOUBLE: u8 = 6;
pub const TAG_BYTE_ARRAY: u8 = 7;
pub const TAG_STRING: u8 = 8;
pub const TAG_LIST: u8 = 9;
pub const TAG_COMPOUND: u8 = 10;
pub const TAG_INT_ARRAY: u8 = 11;
pub const TAG_LONG_ARRAY: u8 = 12;

/// Returns whether a tag id is a defined NBT tag type.
pub fn is_valid(id: u8) -> bool {
    id <= TAG_LONG_ARRAY
}

/// Returns the element size of a primitive array tag, or `None` for non-array tags.
pub fn array_element_size(id: u8) -> Option<usize> {
    match id {
        TAG_BYTE_ARRAY => Some(1),
        TAG_INT_ARRAY => Some(4),
        TAG_LONG_ARRAY => Some(8),
        _ => None,
    }
}
