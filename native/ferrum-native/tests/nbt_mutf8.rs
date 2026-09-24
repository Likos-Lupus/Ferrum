use ferrum::nbt::mutf8::{decode, encode, encoded_len};

#[test]
fn ascii_round_trip() {
    let units: Vec<u16> = "hello world".encode_utf16().collect();
    let mut buffer = vec![0u8; 64];
    let written = encode(&units, &mut buffer).unwrap();
    assert_eq!(written, 11);
    assert_eq!(decode(&buffer[..written]).unwrap(), units);
}

#[test]
fn null_is_encoded_as_two_bytes() {
    let units = [0u16];
    let mut buffer = [0u8; 4];
    let written = encode(&units, &mut buffer).unwrap();
    assert_eq!(written, 2);
    assert_eq!(&buffer[..2], &[0xC0, 0x80]);
    assert_eq!(decode(&buffer[..2]).unwrap(), vec![0u16]);
}

#[test]
fn two_and_three_byte_boundaries() {
    let units = [0x0000u16, 0x0001, 0x007F, 0x0080, 0x07FF, 0x0800, 0xFFFF];
    let mut buffer = vec![0u8; 64];
    let written = encode(&units, &mut buffer).unwrap();
    assert_eq!(
        &buffer[..written],
        &[
            0xC0, 0x80, // U+0000
            0x01, // U+0001
            0x7F, // U+007F
            0xC2, 0x80, // U+0080
            0xDF, 0xBF, // U+07FF
            0xE0, 0xA0, 0x80, // U+0800
            0xEF, 0xBF, 0xBF, // U+FFFF
        ]
    );
    assert_eq!(decode(&buffer[..written]).unwrap(), units);
}

#[test]
fn surrogate_pair_is_six_bytes_and_preserved() {
    // U+1F600 encoded the Java way: two three-byte sequences (CESU-8).
    let units = [0xD83Du16, 0xDE00];
    let mut buffer = vec![0u8; 16];
    let written = encode(&units, &mut buffer).unwrap();
    assert_eq!(written, 6);
    assert_eq!(&buffer[..6], &[0xED, 0xA0, 0xBD, 0xED, 0xB8, 0x80]);
    assert_eq!(decode(&buffer[..6]).unwrap(), units.to_vec());
}

#[test]
fn unpaired_surrogate_is_preserved() {
    let units = [0xD800u16];
    let mut buffer = vec![0u8; 8];
    let written = encode(&units, &mut buffer).unwrap();
    assert_eq!(decode(&buffer[..written]).unwrap(), units.to_vec());
}

#[test]
fn literal_zero_is_accepted_on_decode() {
    // Java's readUTF accepts a literal 0x00 byte even though writeUTF never emits it.
    assert_eq!(decode(&[0x00]).unwrap(), vec![0u16]);
}

#[test]
fn malformed_sequences_are_rejected() {
    assert!(decode(&[0x80]).is_err());
    assert!(decode(&[0xC0]).is_err());
    assert!(decode(&[0xC0, 0x00]).is_err());
    assert!(decode(&[0xE0, 0x80]).is_err());
    assert!(decode(&[0xF0, 0x80, 0x80, 0x80]).is_err());
    assert!(decode(&[0xFF]).is_err());
}

#[test]
fn encoded_length_overflow_is_rejected() {
    let units = vec![0x0800u16; 30_000];
    assert!(encoded_len(&units).is_err());
}
