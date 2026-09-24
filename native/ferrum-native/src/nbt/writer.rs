//! NBT writer: flat arena back to the binary encoding (ADR-0012, ADR-0013).
//!
//! The writer validates every arena offset before reading, tracks the required output size without
//! allocating, and reports overflow as [`WriteError::Required`].

use crate::nbt::arena::ArenaView;
use crate::nbt::tag::{
    TAG_BYTE, TAG_BYTE_ARRAY, TAG_COMPOUND, TAG_DOUBLE, TAG_END, TAG_FLOAT, TAG_INT, TAG_INT_ARRAY,
    TAG_LIST, TAG_LONG, TAG_LONG_ARRAY, TAG_SHORT, TAG_STRING, array_element_size, is_valid,
};

/// A writer failure mapped to an ABI status by the caller.
#[derive(Debug)]
pub enum WriteError {
    Malformed,
    Limit,
    Required(usize),
}

/// Writes the arena back to NBT bytes, returning the used size or the required size.
///
/// `named` selects the named form (`NbtIo.write`); `false` selects the any-tag form
/// (`NbtIo.writeAnyTag`).
pub fn write(arena: &[u8], root: u32, named: bool, out: &mut [u8]) -> Result<usize, WriteError> {
    let view = ArenaView::parse(arena).map_err(|_| WriteError::Malformed)?;
    let mut sink = Sink::new(out);
    write_node(&view, root, true, named, &mut sink, 0)?;
    sink.finish()
}

fn write_node(
    view: &ArenaView<'_>,
    index: u32,
    with_id: bool,
    with_name: bool,
    sink: &mut Sink<'_>,
    depth: u32,
) -> Result<(), WriteError> {
    if depth > 1024 {
        return Err(WriteError::Malformed);
    }
    let node = view.node(index).map_err(|_| WriteError::Malformed)?;
    if !is_valid(node.tag_type) {
        return Err(WriteError::Malformed);
    }

    if with_id {
        sink.u8(node.tag_type);
    }
    if node.tag_type == TAG_END {
        return Ok(());
    }

    if with_name {
        let name = view.name(&node).map_err(|_| WriteError::Malformed)?;
        emit_mutf8(name, sink)?;
    }

    match node.tag_type {
        TAG_BYTE | TAG_SHORT | TAG_INT | TAG_LONG | TAG_FLOAT | TAG_DOUBLE => {
            let size = scalar_size(node.tag_type);
            let bytes = view
                .bytes(node.payload_off, size)
                .map_err(|_| WriteError::Malformed)?;
            sink.write(bytes);
        }
        TAG_BYTE_ARRAY | TAG_INT_ARRAY | TAG_LONG_ARRAY => {
            let element = array_element_size(node.tag_type).ok_or(WriteError::Malformed)?;
            let byte_len = node
                .payload_len
                .checked_mul(element)
                .ok_or(WriteError::Limit)?;
            sink.i32(node.payload_len as i32);
            let bytes = view
                .bytes(node.payload_off, byte_len)
                .map_err(|_| WriteError::Malformed)?;
            sink.write(bytes);
        }
        TAG_STRING => {
            let units = view
                .string_units(&node)
                .map_err(|_| WriteError::Malformed)?;
            emit_mutf8(units, sink)?;
        }
        TAG_LIST => {
            sink.u8(node.list_elem as u8);
            sink.i32(node.child_count as i32);
            for position in 0..node.child_count {
                let child = view
                    .child(&node, position)
                    .map_err(|_| WriteError::Malformed)?;
                write_node(view, child, false, false, sink, depth + 1)?;
            }
        }
        TAG_COMPOUND => {
            for position in 0..node.child_count {
                let child = view
                    .child(&node, position)
                    .map_err(|_| WriteError::Malformed)?;
                write_node(view, child, true, true, sink, depth + 1)?;
            }
            sink.u8(TAG_END);
        }
        _ => return Err(WriteError::Malformed),
    }

    Ok(())
}

fn emit_mutf8(units_le: &[u8], sink: &mut Sink<'_>) -> Result<(), WriteError> {
    let mut length = 0usize;
    for pair in units_le.as_chunks::<2>().0 {
        let unit = u16::from_le_bytes(*pair);
        length += unit_size(unit);
        if length > 0xFFFF {
            return Err(WriteError::Limit);
        }
    }

    sink.u16(length as u16);
    for pair in units_le.as_chunks::<2>().0 {
        let unit = u16::from_le_bytes(*pair);
        if (0x0001..=0x007F).contains(&unit) {
            sink.u8(unit as u8);
        } else if unit == 0 || (0x0080..=0x07FF).contains(&unit) {
            sink.u8(0xC0 | (unit >> 6) as u8);
            sink.u8(0x80 | (unit & 0x3F) as u8);
        } else {
            sink.u8(0xE0 | (unit >> 12) as u8);
            sink.u8(0x80 | ((unit >> 6) & 0x3F) as u8);
            sink.u8(0x80 | (unit & 0x3F) as u8);
        }
    }
    Ok(())
}

fn scalar_size(tag_type: u8) -> usize {
    match tag_type {
        TAG_BYTE => 1,
        TAG_SHORT => 2,
        TAG_INT | TAG_FLOAT => 4,
        TAG_LONG | TAG_DOUBLE => 8,
        _ => 0,
    }
}

fn unit_size(unit: u16) -> usize {
    if (0x0001..=0x007F).contains(&unit) {
        1
    } else if unit == 0 || (0x0080..=0x07FF).contains(&unit) {
        2
    } else {
        3
    }
}

struct Sink<'a> {
    buffer: &'a mut [u8],
    position: usize,
    overflow: bool,
}

impl<'a> Sink<'a> {
    fn new(buffer: &'a mut [u8]) -> Self {
        Self {
            buffer,
            position: 0,
            overflow: false,
        }
    }

    fn write(&mut self, bytes: &[u8]) {
        let end = self.position.saturating_add(bytes.len());
        if !self.overflow {
            if end <= self.buffer.len() {
                self.buffer[self.position..end].copy_from_slice(bytes);
            } else {
                self.overflow = true;
            }
        }
        self.position = end;
    }

    fn u8(&mut self, value: u8) {
        self.write(&[value]);
    }

    fn u16(&mut self, value: u16) {
        self.write(&value.to_be_bytes());
    }

    fn i32(&mut self, value: i32) {
        self.write(&value.to_be_bytes());
    }

    fn finish(self) -> Result<usize, WriteError> {
        if self.overflow {
            Err(WriteError::Required(self.position))
        } else {
            Ok(self.position)
        }
    }
}
