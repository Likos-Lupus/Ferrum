use ferrum::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_OK};
use ferrum::codec::{decode_stream, encode_stream};

const LEVEL: i32 = 6;

fn encode(data: &[u8]) -> Vec<u8> {
    let mut required = 0usize;
    let status = encode_stream(data, &mut [], LEVEL, &mut required);
    assert_eq!(status, FERRUM_ERR_BUFFER_TOO_SMALL);
    let mut out = vec![0u8; required];
    let mut written = 0usize;
    assert_eq!(
        encode_stream(data, &mut out, LEVEL, &mut written),
        FERRUM_OK
    );
    out.truncate(written);
    out
}

fn decode(stream: &[u8]) -> Vec<u8> {
    let mut required = 0usize;
    let status = decode_stream(stream, &mut [], &mut required);
    if status == FERRUM_OK {
        return Vec::new();
    }
    assert_eq!(status, FERRUM_ERR_BUFFER_TOO_SMALL);
    let mut out = vec![0u8; required];
    let mut written = 0usize;
    assert_eq!(decode_stream(stream, &mut out, &mut written), FERRUM_OK);
    out.truncate(written);
    out
}

fn pseudo_random(len: usize) -> Vec<u8> {
    let mut state = 0x1234_5678_9abc_def0u64;
    (0..len)
        .map(|_| {
            state ^= state << 13;
            state ^= state >> 7;
            state ^= state << 17;
            (state & 0xFF) as u8
        })
        .collect()
}

fn round_trip(data: &[u8]) {
    let stream = encode(data);
    assert_eq!(decode(&stream), data, "round trip for {} bytes", data.len());
}

#[test]
fn round_trips_empty_and_small_inputs() {
    round_trip(b"");
    round_trip(b"a");
    round_trip(b"hello world");
    round_trip(b"\x00\x00\x00");
}

#[test]
fn round_trips_across_block_boundaries() {
    for len in [1023usize, 1024, 1025, 65_535, 65_536, 65_537, 131_072] {
        round_trip(&pseudo_random(len));
    }
}

#[test]
fn round_trips_highly_repetitive_multi_block_input() {
    round_trip(&vec![b'a'; 200_000]);
}

#[test]
fn encode_uses_lz4_blocks_for_repetitive_and_raw_for_random() {
    let repetitive = encode(&vec![b'x'; 200_000]);
    let random = encode(&pseudo_random(200_000));
    assert!(methods(&repetitive).contains(&ferrum::codec::framing::COMPRESSION_METHOD_LZ4));
    assert!(methods(&random).contains(&ferrum::codec::framing::COMPRESSION_METHOD_RAW));
    assert_eq!(decode(&repetitive), vec![b'x'; 200_000]);
    assert_eq!(decode(&random), pseudo_random(200_000));
}

#[test]
fn stream_starts_with_the_lz4_java_magic() {
    let stream = encode(b"hello");
    assert_eq!(&stream[..8], b"LZ4Block");
}

#[test]
fn empty_input_encodes_to_only_a_terminator_block() {
    let stream = encode(b"");
    assert_eq!(stream.len(), ferrum::codec::framing::HEADER_LEN);
}

#[test]
fn checksum_matches_the_lz4_java_mask() {
    // lz4-java's asChecksum().getValue() clears the top four bits, so it stores xxh32 & 0x0FFFFFFF.
    // For the single byte 0x2A that is the known value 0x09275C2A.
    let stream = encode(&[0x2A]);
    let expected = 0x0927_5c2a_u32.to_le_bytes();
    assert_eq!(&stream[17..21], &expected);
}

#[test]
fn invalid_level_is_rejected() {
    let mut written = 0usize;
    assert_eq!(
        encode_stream(b"data", &mut [0u8; 64], 16, &mut written),
        ferrum::abi::FERRUM_ERR_INVALID_ARGUMENT
    );
}

fn methods(stream: &[u8]) -> Vec<u8> {
    use ferrum::codec::framing::{HEADER_LEN, MAGIC_LEN, read_u32_le};

    let mut pos = 0usize;
    let mut found = Vec::new();
    while pos + HEADER_LEN <= stream.len() {
        let token = stream[pos + MAGIC_LEN];
        let compressed = read_u32_le(stream, pos + MAGIC_LEN + 1) as usize;
        let original = read_u32_le(stream, pos + MAGIC_LEN + 5) as usize;
        if compressed == 0 && original == 0 {
            break;
        }
        found.push(token & 0xF0);
        pos += HEADER_LEN + compressed;
    }
    found
}
