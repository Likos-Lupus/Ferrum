use ferrum::abi::{FERRUM_ERR_LIMIT_EXCEEDED, FERRUM_ERR_MALFORMED_INPUT};
use ferrum::nbt::limits::Limits;
use ferrum::nbt::parser;

fn simple_document() -> Vec<u8> {
    vec![
        0x0A, 0x00, 0x00, // compound, empty root name
        0x03, 0x00, 0x01, 0x78, 0x00, 0x00, 0x00, 0x05, // int x = 5
        0x08, 0x00, 0x01, 0x73, 0x00, 0x02, 0x68, 0x69, // string s = "hi"
        0x00,
    ]
}

fn limits() -> Limits {
    Limits::default()
}

#[test]
fn truncated_input_is_malformed() {
    let bytes = simple_document();
    let truncated = &bytes[..bytes.len() - 3];
    assert_eq!(
        parser::parse(truncated, limits(), false).unwrap_err(),
        FERRUM_ERR_MALFORMED_INPUT
    );
}

#[test]
fn illegal_root_tag_id_is_malformed() {
    let bytes = [0x0Du8, 0x00, 0x00];
    assert_eq!(
        parser::parse(&bytes, limits(), false).unwrap_err(),
        FERRUM_ERR_MALFORMED_INPUT
    );
}

#[test]
fn negative_array_length_is_malformed() {
    let bytes = vec![
        0x0A, 0x00, 0x00, // compound
        0x0B, 0x00, 0x01, 0x61, 0xFF, 0xFF, 0xFF, 0xFF, // int[] a with length -1
        0x00,
    ];
    assert_eq!(
        parser::parse(&bytes, limits(), false).unwrap_err(),
        FERRUM_ERR_MALFORMED_INPUT
    );
}

#[test]
fn list_of_end_with_values_is_malformed() {
    let bytes = vec![
        0x0A, 0x00, 0x00, // compound
        0x09, 0x00, 0x01, 0x61, 0x00, 0x00, 0x00, 0x00, 0x01, // list a: END element, length 1
        0x00,
    ];
    assert_eq!(
        parser::parse(&bytes, limits(), false).unwrap_err(),
        FERRUM_ERR_MALFORMED_INPUT
    );
}

#[test]
fn depth_limit_is_enforced() {
    let mut bytes = vec![0x0A, 0x00, 0x00];
    for _ in 0..64 {
        bytes.extend_from_slice(&[0x0A, 0x00, 0x01, 0x61]);
    }
    bytes.resize(bytes.len() + 65, 0x00);

    let limited = Limits {
        max_depth: 10,
        ..Limits::default()
    };
    assert_eq!(
        parser::parse(&bytes, limited, false).unwrap_err(),
        FERRUM_ERR_LIMIT_EXCEEDED
    );
}

#[test]
fn node_limit_is_enforced() {
    let limited = Limits {
        max_nodes: 2,
        ..Limits::default()
    };
    assert_eq!(
        parser::parse(&simple_document(), limited, false).unwrap_err(),
        FERRUM_ERR_LIMIT_EXCEEDED
    );
}

#[test]
fn array_length_limit_is_enforced() {
    let bytes = vec![
        0x0A, 0x00, 0x00, // compound
        0x0B, 0x00, 0x01, 0x61, 0x00, 0x00, 0x00, 0x02, // int[] a length 2
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00,
    ];
    let limited = Limits {
        max_array_length: 1,
        ..Limits::default()
    };
    assert_eq!(
        parser::parse(&bytes, limited, false).unwrap_err(),
        FERRUM_ERR_LIMIT_EXCEEDED
    );
}

#[test]
fn string_limit_is_enforced() {
    let limited = Limits {
        max_string_encoded_bytes: 1,
        ..Limits::default()
    };
    assert_eq!(
        parser::parse(&simple_document(), limited, false).unwrap_err(),
        FERRUM_ERR_LIMIT_EXCEEDED
    );
}

#[test]
fn total_bytes_limit_is_enforced() {
    let limited = Limits {
        max_total_bytes: 4,
        ..Limits::default()
    };
    assert_eq!(
        parser::parse(&simple_document(), limited, false).unwrap_err(),
        FERRUM_ERR_LIMIT_EXCEEDED
    );
}
