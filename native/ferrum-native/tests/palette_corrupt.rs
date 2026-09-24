use ferrum::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use ferrum::palette::{pack, unpack};

#[test]
fn unpack_rejects_invalid_widths() {
    let data = [0u64; 4];
    let mut out = [0u32; 8];
    assert_eq!(
        unpack::unpack(&data, 0, 8, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
    assert_eq!(
        unpack::unpack(&data, 33, 8, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
}

#[test]
fn unpack_rejects_short_input() {
    let data = [0u64; 1];
    let mut out = [0u32; 8];
    // bits 4 -> 16 values per word, so 8 values need one word; 130 values need nine.
    assert_eq!(unpack::unpack(&data, 4, 8, &mut out), FERRUM_OK);
    assert_eq!(
        unpack::unpack(&data, 4, 130, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
}

#[test]
fn unpack_rejects_small_output() {
    let data = [0u64; 4];
    let mut out = [0u32; 3];
    assert_eq!(
        unpack::unpack(&data, 4, 8, &mut out),
        FERRUM_ERR_BUFFER_TOO_SMALL
    );
}

#[test]
fn pack_rejects_invalid_widths() {
    let values = [0u32; 4];
    let mut out = [0u64; 4];
    assert_eq!(
        pack::pack(&values, 0, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
    assert_eq!(
        pack::pack(&values, 33, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );
}

#[test]
fn pack_rejects_small_output() {
    let values = [0u32; 130];
    let mut out = [0u64; 1];
    assert_eq!(
        pack::pack(&values, 4, &mut out),
        FERRUM_ERR_BUFFER_TOO_SMALL
    );
}

#[test]
fn pack_rejects_out_of_range_values() {
    let values = [16u32];
    let mut out = [0u64; 1];
    assert_eq!(
        pack::pack(&values, 4, &mut out),
        FERRUM_ERR_INVALID_ARGUMENT
    );

    let max = [15u32];
    assert_eq!(pack::pack(&max, 4, &mut out), FERRUM_OK);
}
