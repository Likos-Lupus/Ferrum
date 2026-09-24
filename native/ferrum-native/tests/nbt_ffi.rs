use std::ptr;

use ferrum::abi::{FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_OK};
use ferrum::{FerrumLimits, nbt};

fn limits() -> FerrumLimits {
    FerrumLimits {
        max_total_bytes: u64::MAX,
        max_depth: 512,
        max_nodes: u32::MAX,
        max_array_length: u32::MAX,
        max_string_encoded_bytes: 0xFFFF,
    }
}

fn named_document() -> Vec<u8> {
    vec![
        0x0A, 0x00, 0x00, // compound, empty root name
        0x03, 0x00, 0x01, 0x78, 0x00, 0x00, 0x00, 0x05, // int x = 5
        0x00,
    ]
}

fn any_document() -> Vec<u8> {
    vec![
        0x0A, // compound, no name
        0x03, 0x00, 0x01, 0x78, 0x00, 0x00, 0x00, 0x05, // int x = 5
        0x00,
    ]
}

#[test]
fn ffi_sizing_query_then_fill_and_write() {
    let input = named_document();
    let limits = limits();
    let mut root = 0u32;
    let mut used = 0usize;

    let status = unsafe {
        nbt::ferrum_nbt_parse(
            input.as_ptr(),
            input.len(),
            &limits,
            ptr::null_mut(),
            0,
            &mut root,
            &mut used,
        )
    };
    assert_eq!(status, FERRUM_ERR_BUFFER_TOO_SMALL);
    let required = used;
    assert!(required > 0);

    let mut arena = vec![0u8; required];
    let status = unsafe {
        nbt::ferrum_nbt_parse(
            input.as_ptr(),
            input.len(),
            &limits,
            arena.as_mut_ptr(),
            arena.len(),
            &mut root,
            &mut used,
        )
    };
    assert_eq!(status, FERRUM_OK);
    assert_eq!(used, required);

    let mut out = vec![0u8; any_document().len() + 16];
    let mut written = 0usize;
    let status = unsafe {
        nbt::ferrum_nbt_write_any(
            arena.as_ptr(),
            arena.len(),
            root,
            out.as_mut_ptr(),
            out.len(),
            &mut written,
        )
    };
    assert_eq!(status, FERRUM_OK);
    out.truncate(written);
    assert_eq!(out, any_document());
}

#[test]
fn ffi_named_write_reproduces_input() {
    let input = named_document();
    let limits = limits();
    let mut root = 0u32;
    let mut used = 0usize;
    let mut arena = vec![0u8; 256];
    let status = unsafe {
        nbt::ferrum_nbt_parse(
            input.as_ptr(),
            input.len(),
            &limits,
            arena.as_mut_ptr(),
            arena.len(),
            &mut root,
            &mut used,
        )
    };
    assert_eq!(status, FERRUM_OK);

    let mut out = vec![0u8; 256];
    let mut written = 0usize;
    let status = unsafe {
        nbt::ferrum_nbt_write(
            arena.as_ptr(),
            used,
            root,
            out.as_mut_ptr(),
            out.len(),
            &mut written,
        )
    };
    assert_eq!(status, FERRUM_OK);
    out.truncate(written);
    assert_eq!(out, input);
}

#[test]
fn ffi_invalid_arguments_are_rejected() {
    let input = named_document();
    let limits = limits();
    let mut root = 0u32;
    let mut used = 0usize;

    let status = unsafe {
        nbt::ferrum_nbt_parse(
            input.as_ptr(),
            input.len(),
            &limits,
            ptr::null_mut(),
            0,
            &mut root,
            ptr::null_mut(),
        )
    };
    assert_eq!(status, FERRUM_ERR_INVALID_ARGUMENT);

    let status = unsafe {
        nbt::ferrum_nbt_parse(
            ptr::null(),
            4,
            &limits,
            ptr::null_mut(),
            0,
            &mut root,
            &mut used,
        )
    };
    assert_eq!(status, FERRUM_ERR_INVALID_ARGUMENT);
}
