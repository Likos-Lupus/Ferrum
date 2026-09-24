use ferrum::codec::framing::{
    CHECKSUM_MASK, DEFAULT_BLOCK_SIZE, DEFAULT_SEED, MAGIC, MAX_BLOCK_SIZE, MIN_BLOCK_SIZE,
    block_size_ceiling, compression_nibble,
};

#[test]
fn default_block_size_maps_to_nibble_six() {
    assert_eq!(compression_nibble(DEFAULT_BLOCK_SIZE), Some(6));
    assert_eq!(block_size_ceiling(6), DEFAULT_BLOCK_SIZE);
}

#[test]
fn nibble_covers_the_accepted_range() {
    assert_eq!(compression_nibble(MIN_BLOCK_SIZE), Some(0));
    assert_eq!(block_size_ceiling(0), 1 << 10);
    assert_eq!(compression_nibble(MAX_BLOCK_SIZE), Some(15));
    assert_eq!(block_size_ceiling(15), MAX_BLOCK_SIZE);
    assert_eq!(compression_nibble(MIN_BLOCK_SIZE - 1), None);
    assert_eq!(compression_nibble(MAX_BLOCK_SIZE + 1), None);
}

#[test]
fn magic_seed_and_checksum_mask_match_lz4_java() {
    assert_eq!(&MAGIC, b"LZ4Block");
    assert_eq!(DEFAULT_SEED, 0x9747_b28c);
    assert_eq!(CHECKSUM_MASK, 0x0FFF_FFFF);
}
