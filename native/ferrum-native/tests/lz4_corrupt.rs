use ferrum::abi::{
    FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_ERR_MALFORMED_INPUT, FERRUM_OK,
};
use ferrum::codec::framing::{
    COMPRESSION_METHOD_LZ4, COMPRESSION_METHOD_RAW, HEADER_LEN, MAGIC, MAGIC_LEN,
};
use ferrum::codec::{decode_stream, encode_stream};

const LEVEL: i32 = 6;

fn encode(data: &[u8]) -> Vec<u8> {
    let mut required = 0usize;
    assert_eq!(
        encode_stream(data, &mut [], LEVEL, &mut required),
        FERRUM_ERR_BUFFER_TOO_SMALL
    );
    let mut out = vec![0u8; required];
    let mut written = 0usize;
    assert_eq!(
        encode_stream(data, &mut out, LEVEL, &mut written),
        FERRUM_OK
    );
    out.truncate(written);
    out
}

fn decode_status(stream: &[u8]) -> i32 {
    let mut out = vec![0u8; 4096];
    let mut written = 0usize;
    decode_stream(stream, &mut out, &mut written)
}

fn raw_block(method: u8, compressed: u32, original: u32, checksum: u32, payload: &[u8]) -> Vec<u8> {
    let mut bytes = Vec::new();
    bytes.extend_from_slice(&MAGIC);
    bytes.push(method | 6);
    bytes.extend_from_slice(&compressed.to_le_bytes());
    bytes.extend_from_slice(&original.to_le_bytes());
    bytes.extend_from_slice(&checksum.to_le_bytes());
    bytes.extend_from_slice(payload);
    bytes
}

#[test]
fn bad_magic_is_malformed() {
    let mut stream = encode(b"hello world");
    stream[0] = 0x00;
    assert_eq!(decode_status(&stream), FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn truncated_stream_is_malformed() {
    let stream = encode(b"hello world");
    for cut in [1usize, 2, HEADER_LEN - 1, HEADER_LEN, HEADER_LEN + 1] {
        if cut <= stream.len() {
            let truncated = &stream[..stream.len() - cut];
            assert_eq!(
                decode_status(truncated),
                FERRUM_ERR_MALFORMED_INPUT,
                "cut {cut}"
            );
        }
    }
}

#[test]
fn corrupted_checksum_is_malformed() {
    let mut stream = encode(b"hello hello hello hello");
    stream[MAGIC_LEN + 9] ^= 0xFF;
    assert_eq!(decode_status(&stream), FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn raw_block_with_mismatched_lengths_is_malformed() {
    let stream = raw_block(COMPRESSION_METHOD_RAW, 1, 2, 0, &[0x00, 0x00]);
    assert_eq!(decode_status(&stream), FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn terminator_with_nonzero_checksum_is_malformed() {
    let stream = raw_block(COMPRESSION_METHOD_RAW, 0, 0, 1, &[]);
    assert_eq!(decode_status(&stream), FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn unknown_compression_method_is_malformed() {
    let stream = raw_block(0x30, 1, 1, 0, &[0x41]);
    assert_eq!(decode_status(&stream), FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn empty_input_is_malformed() {
    assert_eq!(decode_status(&[]), FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn lz4_block_over_declared_ceiling_is_malformed() {
    // A nibble of 0 caps the block size at 1 KiB; declaring 4 KiB is rejected before decoding.
    let mut bytes = Vec::new();
    bytes.extend_from_slice(&MAGIC);
    bytes.push(COMPRESSION_METHOD_LZ4);
    bytes.extend_from_slice(&8u32.to_le_bytes());
    bytes.extend_from_slice(&4096u32.to_le_bytes());
    bytes.extend_from_slice(&0u32.to_le_bytes());
    bytes.extend_from_slice(&[0u8; 8]);
    assert_eq!(decode_status(&bytes), FERRUM_ERR_MALFORMED_INPUT);
}

#[test]
fn oversized_declared_output_is_rejected_before_decoding() {
    // A tiny LZ4 block may declare a 64 KiB output: the destination check must reject it without
    // ever running the decompressor (decompression-bomb guard).
    let mut stream = raw_block(COMPRESSION_METHOD_LZ4, 16, 64 * 1024, 0, &[0u8; 16]);
    stream.extend_from_slice(&raw_block(COMPRESSION_METHOD_RAW, 0, 0, 0, &[]));
    let mut written = 0usize;
    let status = decode_stream(&stream, &mut [0u8; 1024], &mut written);
    assert_eq!(status, FERRUM_ERR_BUFFER_TOO_SMALL);
    assert_eq!(written, 64 * 1024);
}

#[test]
fn buffer_too_small_reports_exact_required() {
    let data = b"the quick brown fox jumps over the lazy dog".repeat(64);
    let stream = encode(&data);
    let mut out = vec![0u8; data.len() - 1];
    let mut written = 0usize;
    assert_eq!(
        decode_stream(&stream, &mut out, &mut written),
        FERRUM_ERR_BUFFER_TOO_SMALL
    );
    assert_eq!(written, data.len());
}

#[test]
fn negative_level_is_invalid_argument() {
    let mut written = 0usize;
    assert_eq!(
        encode_stream(b"abc", &mut [0u8; 32], -1, &mut written),
        FERRUM_ERR_INVALID_ARGUMENT
    );
}
