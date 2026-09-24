//! NBT parser producing a flat arena (ADR-0012).
//!
//! The parser is iterative for containers via an explicit child stack and enforces every limit with
//! checked arithmetic. It never panics on malformed input; failures are status codes.

use crate::abi::{FERRUM_ERR_LIMIT_EXCEEDED, FERRUM_ERR_MALFORMED_INPUT};
use crate::nbt::arena::{ArenaBuilder, Node};
use crate::nbt::limits::Limits;
use crate::nbt::mutf8;
use crate::nbt::tag::{
    TAG_BYTE, TAG_BYTE_ARRAY, TAG_COMPOUND, TAG_DOUBLE, TAG_END, TAG_FLOAT, TAG_INT, TAG_INT_ARRAY,
    TAG_LIST, TAG_LONG, TAG_LONG_ARRAY, TAG_SHORT, TAG_STRING, is_valid,
};

const NODE_ERROR: i32 = FERRUM_ERR_MALFORMED_INPUT;

/// Parses an NBT document and reports the number of input bytes consumed.
///
/// When `any` is `true`, the any-tag form (no root name) is expected; otherwise the named form
/// (`NbtIo.read`) is expected.
pub fn parse(input: &[u8], limits: Limits, any: bool) -> Result<(ArenaBuilder, usize), i32> {
    let mut parser = Parser {
        input,
        pos: 0,
        limits,
        arena: ArenaBuilder::new(),
        child_stack: Vec::new(),
        depth: 0,
    };
    parser.parse_document(any)?;
    let consumed = parser.pos;
    Ok((parser.arena, consumed))
}

struct Parser<'a> {
    input: &'a [u8],
    pos: usize,
    limits: Limits,
    arena: ArenaBuilder,
    child_stack: Vec<u32>,
    depth: u32,
}

impl<'a> Parser<'a> {
    fn parse_document(&mut self, any: bool) -> Result<(), i32> {
        if self.input.len() as u64 > self.limits.max_total_bytes {
            return Err(FERRUM_ERR_LIMIT_EXCEEDED);
        }

        let tag_id = self.read_u8()?;
        let root = if tag_id == TAG_END {
            self.check_node_budget()?;
            self.arena.push_node(Node {
                tag_type: TAG_END,
                ..Default::default()
            })
        } else {
            if !is_valid(tag_id) {
                return Err(NODE_ERROR);
            }
            if any {
                self.parse_tag(tag_id, None)?
            } else {
                let name = self.read_string_value()?;
                self.parse_tag(tag_id, Some(name))?
            }
        };

        self.arena.root = root;
        Ok(())
    }

    fn parse_tag(&mut self, tag_id: u8, name: Option<(u32, u32)>) -> Result<u32, i32> {
        self.check_node_budget()?;
        let flags = if name.is_some() { 1 } else { 0 };
        let index = self.arena.push_node(Node {
            tag_type: tag_id,
            flags,
            ..Default::default()
        });

        let (name_off, name_units) = name.unwrap_or((0, 0));
        let mut node = Node {
            tag_type: tag_id,
            flags,
            name_off,
            name_units,
            ..Default::default()
        };

        match tag_id {
            TAG_BYTE => {
                node.payload_len = 1;
                let bytes = self.read_bytes(1)?;
                node.payload_off = self.arena.push_payload(bytes);
            }
            TAG_SHORT => {
                node.payload_len = 2;
                let bytes = self.read_bytes(2)?;
                node.payload_off = self.arena.push_payload(bytes);
            }
            TAG_INT | TAG_FLOAT => {
                node.payload_len = 4;
                let bytes = self.read_bytes(4)?;
                node.payload_off = self.arena.push_payload(bytes);
            }
            TAG_LONG | TAG_DOUBLE => {
                node.payload_len = 8;
                let bytes = self.read_bytes(8)?;
                node.payload_off = self.arena.push_payload(bytes);
            }
            TAG_BYTE_ARRAY => {
                let count = self.read_array_length()?;
                let bytes = self.read_bytes(count)?;
                node.payload_off = self.arena.push_payload(bytes);
                node.payload_len = count as u32;
            }
            TAG_INT_ARRAY => {
                let count = self.read_array_length()?;
                let bytes =
                    self.read_bytes(count.checked_mul(4).ok_or(FERRUM_ERR_LIMIT_EXCEEDED)?)?;
                node.payload_off = self.arena.push_payload(bytes);
                node.payload_len = count as u32;
            }
            TAG_LONG_ARRAY => {
                let count = self.read_array_length()?;
                let bytes =
                    self.read_bytes(count.checked_mul(8).ok_or(FERRUM_ERR_LIMIT_EXCEEDED)?)?;
                node.payload_off = self.arena.push_payload(bytes);
                node.payload_len = count as u32;
            }
            TAG_STRING => {
                let (offset, units) = self.read_string_value()?;
                node.payload_off = offset;
                node.payload_len = units;
            }
            TAG_LIST => {
                let (child_off, child_count, elem) = self.parse_list_children()?;
                node.child_off = child_off;
                node.child_count = child_count;
                node.list_elem = elem;
            }
            TAG_COMPOUND => {
                let (child_off, child_count) = self.parse_compound_children()?;
                node.child_off = child_off;
                node.child_count = child_count;
            }
            _ => return Err(NODE_ERROR),
        }

        self.arena.patch_node(index, node);
        Ok(index)
    }

    fn parse_compound_children(&mut self) -> Result<(u32, u32), i32> {
        self.enter_depth()?;
        let mark = self.child_stack.len();
        loop {
            let child_id = self.read_u8()?;
            if child_id == TAG_END {
                break;
            }
            if !is_valid(child_id) {
                return Err(NODE_ERROR);
            }
            let name = self.read_string_value()?;
            let child = self.parse_tag(child_id, Some(name))?;
            self.child_stack.push(child);
        }
        self.leave_depth();
        self.finish_children(mark)
    }

    fn parse_list_children(&mut self) -> Result<(u32, u32, u16), i32> {
        let elem = self.read_u8()?;
        let count = self.read_array_length()?;
        if elem == TAG_END {
            if count != 0 {
                return Err(NODE_ERROR);
            }
        } else if !is_valid(elem) {
            return Err(NODE_ERROR);
        }

        self.enter_depth()?;
        let mark = self.child_stack.len();
        for _ in 0..count {
            let child = self.parse_tag(elem, None)?;
            self.child_stack.push(child);
        }
        self.leave_depth();
        let (offset, child_count) = self.finish_children(mark)?;
        Ok((offset, child_count, u16::from(elem)))
    }

    fn finish_children(&mut self, mark: usize) -> Result<(u32, u32), i32> {
        let child_count = (self.child_stack.len() - mark) as u32;
        let offset = self.arena.push_children(&self.child_stack[mark..]);
        self.child_stack.truncate(mark);
        Ok((offset, child_count))
    }

    fn enter_depth(&mut self) -> Result<(), i32> {
        self.depth = self.depth.saturating_add(1);
        if self.depth > self.limits.max_depth {
            return Err(FERRUM_ERR_LIMIT_EXCEEDED);
        }
        Ok(())
    }

    fn leave_depth(&mut self) {
        self.depth = self.depth.saturating_sub(1);
    }

    fn check_node_budget(&self) -> Result<(), i32> {
        if self.arena.nodes.len() as u32 >= self.limits.max_nodes {
            return Err(FERRUM_ERR_LIMIT_EXCEEDED);
        }
        Ok(())
    }

    fn read_array_length(&mut self) -> Result<usize, i32> {
        let raw = self.read_i32()?;
        if raw < 0 {
            return Err(NODE_ERROR);
        }
        let count = raw as usize;
        if raw as u32 > self.limits.max_array_length {
            return Err(FERRUM_ERR_LIMIT_EXCEEDED);
        }
        Ok(count)
    }

    fn read_string_value(&mut self) -> Result<(u32, u32), i32> {
        let length = usize::from(self.read_u16()?);
        if length as u32 > self.limits.max_string_encoded_bytes {
            return Err(FERRUM_ERR_LIMIT_EXCEEDED);
        }
        let bytes = self.read_bytes(length)?;
        let units = mutf8::decode(bytes).map_err(|_| NODE_ERROR)?;
        let offset = self.arena.push_string(&units);
        Ok((offset, units.len() as u32))
    }

    fn read_u8(&mut self) -> Result<u8, i32> {
        let byte = *self.input.get(self.pos).ok_or(NODE_ERROR)?;
        self.pos += 1;
        Ok(byte)
    }

    fn read_u16(&mut self) -> Result<u16, i32> {
        let bytes = self.read_bytes(2)?;
        Ok(u16::from_be_bytes([bytes[0], bytes[1]]))
    }

    fn read_i32(&mut self) -> Result<i32, i32> {
        let bytes = self.read_bytes(4)?;
        Ok(i32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn read_bytes(&mut self, count: usize) -> Result<&'a [u8], i32> {
        let end = self
            .pos
            .checked_add(count)
            .ok_or(FERRUM_ERR_LIMIT_EXCEEDED)?;
        let bytes = self.input.get(self.pos..end).ok_or(NODE_ERROR)?;
        self.pos = end;
        Ok(bytes)
    }
}
