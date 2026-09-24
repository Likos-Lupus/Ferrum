//! The lz4-java `LZ4Block` stream framing.
//!
//! Region payloads are compressed by `net.jpountz.lz4.LZ4BlockInputStream` /
//! `LZ4BlockOutputStream`. That is a private block framing which is deliberately **not** the
//! standard LZ4 Frame format: it carries its own magic, per-block headers, and an XXH32 checksum.
//! Every constant and helper below mirrors that framing so the native kernel can read and write
//! vanilla region payloads byte-for-byte.

/// The 8-byte stream magic, `LZ4Block`.
pub const MAGIC: [u8; 8] = *b"LZ4Block";

/// The length of [`MAGIC`].
pub const MAGIC_LEN: usize = MAGIC.len();

/// The fixed per-block header length: magic + token + compressed length + decompressed length +
/// checksum.
pub const HEADER_LEN: usize = MAGIC_LEN + 1 + 4 + 4 + 4;

/// A raw (stored, uncompressed) block.
pub const COMPRESSION_METHOD_RAW: u8 = 0x10;

/// An LZ4-compressed block.
pub const COMPRESSION_METHOD_LZ4: u8 = 0x20;

/// The base lz4-java uses when encoding the block-size ceiling in the token.
pub const COMPRESSION_LEVEL_BASE: u32 = 10;

/// The XXH32 seed used by `LZ4BlockInputStream` / `LZ4BlockOutputStream`.
pub const DEFAULT_SEED: u32 = 0x9747_b28c;

/// The mask lz4-java applies to the XXH32 checksum before storing it.
///
/// `StreamingXXHash32.asChecksum().getValue()` returns `getValue() & 0xFFFFFFFL`, i.e. it clears the
/// top four bits. The block stream is self-consistent with this mask, so Ferrum must clear the same
/// bits to interoperate.
pub const CHECKSUM_MASK: u32 = 0x0FFF_FFFF;

/// The block size used by `new LZ4BlockOutputStream(OutputStream)`, and therefore by Minecraft.
pub const DEFAULT_BLOCK_SIZE: usize = 1 << 16;

/// The smallest block size lz4-java accepts.
pub const MIN_BLOCK_SIZE: usize = 64;

/// The largest block size lz4-java accepts.
pub const MAX_BLOCK_SIZE: usize = 1 << (COMPRESSION_LEVEL_BASE as usize + 0x0F);

/// Returns the token nibble that encodes `block_size`, or `None` when it is out of range.
pub fn compression_nibble(block_size: usize) -> Option<u8> {
    if !(MIN_BLOCK_SIZE..=MAX_BLOCK_SIZE).contains(&block_size) {
        return None;
    }
    let level = usize::BITS - (block_size - 1).leading_zeros();
    Some((level.saturating_sub(COMPRESSION_LEVEL_BASE) & 0x0F) as u8)
}

/// Returns the maximum block size encoded by `nibble`.
pub fn block_size_ceiling(nibble: u8) -> usize {
    1usize << (COMPRESSION_LEVEL_BASE + u32::from(nibble & 0x0F))
}

/// Reads a little-endian `u32` at `offset`.
///
/// The caller must have verified that `offset + 4 <= bytes.len()`.
pub fn read_u32_le(bytes: &[u8], offset: usize) -> u32 {
    u32::from_le_bytes([
        bytes[offset],
        bytes[offset + 1],
        bytes[offset + 2],
        bytes[offset + 3],
    ])
}

/// Writes a little-endian `u32` at `offset`.
///
/// The caller must have verified that `offset + 4 <= bytes.len()`.
pub fn write_u32_le(bytes: &mut [u8], offset: usize, value: u32) {
    bytes[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}
