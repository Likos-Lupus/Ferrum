//! FerrumNbt native entry points.
//!
//! Two wire forms are supported (ADR-0013): the named form (`ferrum_nbt_parse`/`ferrum_nbt_write`,
//! matching `NbtIo`) and the any-tag form (`ferrum_nbt_parse_any`/`ferrum_nbt_write_any`, matching
//! `NbtIo.readAnyTag`/`writeAnyTag` and `FriendlyByteBuf`). Both share the flat arena (ADR-0012).

pub mod arena;
pub mod error;
pub mod limits;
pub mod mutf8;
pub mod parser;
pub mod tag;
pub mod writer;

use std::slice;

use crate::abi::{
    FERRUM_ERR_BUFFER_TOO_SMALL, FERRUM_ERR_INVALID_ARGUMENT, FERRUM_ERR_LIMIT_EXCEEDED,
    FERRUM_ERR_MALFORMED_INPUT, FERRUM_OK, FerrumLimits,
};
use crate::guard::guard;
use crate::nbt::limits::Limits;
use crate::nbt::writer::WriteError;

/// Parses the named wire form into the flat arena.
///
/// # Safety
///
/// `src` must be valid for `src_len` bytes (or null when `src_len` is `0`), `limits` must point to a
/// valid [`FerrumLimits`], `arena` must be valid for `arena_cap` bytes (or null when `arena_cap` is
/// `0`), and `root_index`/`used_or_required` must be valid for writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_nbt_parse(
    src: *const u8,
    src_len: usize,
    limits: *const FerrumLimits,
    arena: *mut u8,
    arena_cap: usize,
    root_index: *mut u32,
    used_or_required: *mut usize,
) -> i32 {
    guard(|| unsafe {
        parse_entry(
            src,
            src_len,
            limits,
            arena,
            arena_cap,
            root_index,
            used_or_required,
            false,
            std::ptr::null_mut(),
        )
    })
}

/// Parses the any-tag wire form (no root name) into the flat arena.
///
/// # Safety
///
/// Same contract as [`ferrum_nbt_parse`]. `consumed` must be valid for writing a single `usize` and
/// receives the number of input bytes consumed.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_nbt_parse_any(
    src: *const u8,
    src_len: usize,
    limits: *const FerrumLimits,
    arena: *mut u8,
    arena_cap: usize,
    root_index: *mut u32,
    used_or_required: *mut usize,
    consumed: *mut usize,
) -> i32 {
    guard(|| unsafe {
        parse_entry(
            src,
            src_len,
            limits,
            arena,
            arena_cap,
            root_index,
            used_or_required,
            true,
            consumed,
        )
    })
}

/// Writes the flat arena back to the named wire form.
///
/// # Safety
///
/// `arena` must be valid for `arena_len` bytes (or null when `arena_len` is `0`), `dst` must be
/// valid for `dst_cap` bytes (or null when `dst_cap` is `0`), and `written_or_required` must be valid
/// for writing a single value.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_nbt_write(
    arena: *const u8,
    arena_len: usize,
    root_index: u32,
    dst: *mut u8,
    dst_cap: usize,
    written_or_required: *mut usize,
) -> i32 {
    guard(|| unsafe {
        write_entry(
            arena,
            arena_len,
            root_index,
            dst,
            dst_cap,
            written_or_required,
            true,
        )
    })
}

/// Writes the flat arena back to the any-tag wire form.
///
/// # Safety
///
/// Same contract as [`ferrum_nbt_write`].
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ferrum_nbt_write_any(
    arena: *const u8,
    arena_len: usize,
    root_index: u32,
    dst: *mut u8,
    dst_cap: usize,
    written_or_required: *mut usize,
) -> i32 {
    guard(|| unsafe {
        write_entry(
            arena,
            arena_len,
            root_index,
            dst,
            dst_cap,
            written_or_required,
            false,
        )
    })
}

#[allow(clippy::too_many_arguments)]
unsafe fn parse_entry(
    src: *const u8,
    src_len: usize,
    limits: *const FerrumLimits,
    arena: *mut u8,
    arena_cap: usize,
    root_index: *mut u32,
    used_or_required: *mut usize,
    any: bool,
    consumed: *mut usize,
) -> i32 {
    if limits.is_null() || root_index.is_null() || used_or_required.is_null() {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    if src.is_null() && src_len != 0 {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    if arena.is_null() && arena_cap != 0 {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }

    let limits_ref = unsafe { &*limits };
    let input: &[u8] = if src_len == 0 {
        &[]
    } else {
        unsafe { slice::from_raw_parts(src, src_len) }
    };

    let (builder, consumed_bytes) = match parser::parse(input, Limits::from_abi(limits_ref), any) {
        Ok(parsed) => parsed,
        Err(status) => return status,
    };

    let arena_slice: &mut [u8] = if arena_cap == 0 {
        &mut []
    } else {
        unsafe { slice::from_raw_parts_mut(arena, arena_cap) }
    };

    match builder.serialize(arena_slice) {
        Ok(used) => {
            unsafe {
                *root_index = builder.root;
                *used_or_required = used;
                if !consumed.is_null() {
                    *consumed = consumed_bytes;
                }
            }
            FERRUM_OK
        }
        Err(required) => {
            unsafe {
                *used_or_required = required;
            }
            FERRUM_ERR_BUFFER_TOO_SMALL
        }
    }
}

unsafe fn write_entry(
    arena: *const u8,
    arena_len: usize,
    root_index: u32,
    dst: *mut u8,
    dst_cap: usize,
    written_or_required: *mut usize,
    named: bool,
) -> i32 {
    if written_or_required.is_null() {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    if arena.is_null() && arena_len != 0 {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }
    if dst.is_null() && dst_cap != 0 {
        return FERRUM_ERR_INVALID_ARGUMENT;
    }

    let arena_slice: &[u8] = if arena_len == 0 {
        &[]
    } else {
        unsafe { slice::from_raw_parts(arena, arena_len) }
    };
    let out: &mut [u8] = if dst_cap == 0 {
        &mut []
    } else {
        unsafe { slice::from_raw_parts_mut(dst, dst_cap) }
    };

    match writer::write(arena_slice, root_index, named, out) {
        Ok(used) => {
            unsafe {
                *written_or_required = used;
            }
            FERRUM_OK
        }
        Err(WriteError::Required(required)) => {
            unsafe {
                *written_or_required = required;
            }
            FERRUM_ERR_BUFFER_TOO_SMALL
        }
        Err(WriteError::Malformed) => FERRUM_ERR_MALFORMED_INPUT,
        Err(WriteError::Limit) => FERRUM_ERR_LIMIT_EXCEEDED,
    }
}
