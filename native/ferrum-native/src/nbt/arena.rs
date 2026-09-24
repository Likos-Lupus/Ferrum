//! The flat NBT arena: an offset-based, bounds-checkable interchange representation (ADR-0012).
//!
//! An [`ArenaBuilder`] accumulates nodes, UTF-16 string code units, payload bytes, and child
//! indices; [`ArenaBuilder::serialize`] lays them out into a caller buffer with absolute offsets.
//! [`ArenaView`] parses such a buffer back for the writer. All offsets are little-endian and
//! relative to the arena start in the serialized form.

use crate::nbt::error::NbtError;
use crate::nbt::tag::TAG_STRING;

/// Arena magic `FBNT` read as a little-endian `u32`.
pub const ARENA_MAGIC: u32 = 0x544E_4246;
/// Arena format version.
pub const ARENA_VERSION: u16 = 1;
/// Serialized header size in bytes.
pub const HEADER_SIZE: usize = 32;
/// Serialized node record size in bytes.
pub const NODE_SIZE: usize = 32;

/// A single flat arena node.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Node {
    pub tag_type: u8,
    pub flags: u8,
    pub list_elem: u16,
    pub name_off: u32,
    pub name_units: u32,
    pub payload_off: u32,
    pub payload_len: u32,
    pub child_off: u32,
    pub child_count: u32,
}

/// Accumulates arena regions while parsing.
#[derive(Default, Debug)]
pub struct ArenaBuilder {
    pub nodes: Vec<Node>,
    pub strings: Vec<u16>,
    pub payload: Vec<u8>,
    pub children: Vec<u32>,
    pub root: u32,
}

impl ArenaBuilder {
    pub fn new() -> Self {
        Self::default()
    }

    /// Appends a node and returns its index.
    pub fn push_node(&mut self, node: Node) -> u32 {
        let index = self.nodes.len() as u32;
        self.nodes.push(node);
        index
    }

    /// Replaces an existing node.
    pub fn patch_node(&mut self, index: u32, node: Node) {
        self.nodes[index as usize] = node;
    }

    /// Appends UTF-16 code units and returns their region-relative byte offset.
    pub fn push_string(&mut self, units: &[u16]) -> u32 {
        let offset = (self.strings.len() * 2) as u32;
        self.strings.extend_from_slice(units);
        offset
    }

    /// Appends payload bytes and returns their region-relative byte offset.
    pub fn push_payload(&mut self, bytes: &[u8]) -> u32 {
        let offset = self.payload.len() as u32;
        self.payload.extend_from_slice(bytes);
        offset
    }

    /// Appends child node indices and returns their region-relative byte offset.
    pub fn push_children(&mut self, indices: &[u32]) -> u32 {
        let offset = (self.children.len() * 4) as u32;
        self.children.extend_from_slice(indices);
        offset
    }

    /// Returns the total serialized size in bytes.
    pub fn size(&self) -> usize {
        let strings_base = HEADER_SIZE + self.nodes.len() * NODE_SIZE;
        let payload_base = strings_base + self.strings.len() * 2;
        let children_base = align4(payload_base + self.payload.len());
        children_base + self.children.len() * 4
    }

    /// Serializes into `out`, returning the used size or the required size on overflow.
    pub fn serialize(&self, out: &mut [u8]) -> Result<usize, usize> {
        let total = self.size();
        if out.len() < total {
            return Err(total);
        }

        let nodes_base = HEADER_SIZE;
        let strings_base = nodes_base + self.nodes.len() * NODE_SIZE;
        let payload_base = strings_base + self.strings.len() * 2;
        let children_base = align4(payload_base + self.payload.len());

        write_u32(out, 0, ARENA_MAGIC);
        write_u16(out, 4, ARENA_VERSION);
        write_u16(out, 6, HEADER_SIZE as u16);
        write_u32(out, 8, total as u32);
        write_u32(out, 12, self.root);
        write_u32(out, 16, nodes_base as u32);
        write_u32(out, 20, self.nodes.len() as u32);
        write_u32(out, 24, strings_base as u32);
        write_u32(out, 28, self.strings.len() as u32);

        for (index, node) in self.nodes.iter().enumerate() {
            let offset = nodes_base + index * NODE_SIZE;
            out[offset] = node.tag_type;
            out[offset + 1] = node.flags;
            write_u16(out, offset + 2, node.list_elem);
            write_u32(out, offset + 4, (strings_base as u32) + node.name_off);
            write_u32(out, offset + 8, node.name_units);
            let payload_absolute = if node.tag_type == TAG_STRING {
                (strings_base as u32) + node.payload_off
            } else {
                (payload_base as u32) + node.payload_off
            };
            write_u32(out, offset + 12, payload_absolute);
            write_u32(out, offset + 16, node.payload_len);
            write_u32(out, offset + 20, (children_base as u32) + node.child_off);
            write_u32(out, offset + 24, node.child_count);
            write_u32(out, offset + 28, 0);
        }

        for (index, unit) in self.strings.iter().enumerate() {
            write_u16(out, strings_base + index * 2, *unit);
        }
        out[payload_base..payload_base + self.payload.len()].copy_from_slice(&self.payload);
        for (index, child) in self.children.iter().enumerate() {
            write_u32(out, children_base + index * 4, *child);
        }

        Ok(total)
    }
}

/// A validated view over a serialized arena.
pub struct ArenaView<'a> {
    bytes: &'a [u8],
    nodes_base: usize,
    node_count: usize,
    pub root: u32,
}

impl<'a> ArenaView<'a> {
    /// Parses and validates the arena header.
    pub fn parse(bytes: &'a [u8]) -> Result<Self, NbtError> {
        if bytes.len() < HEADER_SIZE {
            return Err(NbtError);
        }
        if read_u32(bytes, 0)? != ARENA_MAGIC {
            return Err(NbtError);
        }
        if read_u16(bytes, 4)? != ARENA_VERSION {
            return Err(NbtError);
        }
        if read_u16(bytes, 6)? as usize != HEADER_SIZE {
            return Err(NbtError);
        }
        let root = read_u32(bytes, 12)?;
        let nodes_base = read_u32(bytes, 16)? as usize;
        let node_count = read_u32(bytes, 20)? as usize;
        let nodes_end = nodes_base
            .checked_add(node_count.checked_mul(NODE_SIZE).ok_or(NbtError)?)
            .ok_or(NbtError)?;
        if nodes_end > bytes.len() {
            return Err(NbtError);
        }
        if root as usize >= node_count {
            return Err(NbtError);
        }
        Ok(Self {
            bytes,
            nodes_base,
            node_count,
            root,
        })
    }

    pub fn node_count(&self) -> usize {
        self.node_count
    }

    /// Reads a node by index with all offsets bounds-checked.
    pub fn node(&self, index: u32) -> Result<ArenaNode, NbtError> {
        if index as usize >= self.node_count {
            return Err(NbtError);
        }
        let offset = self.nodes_base + index as usize * NODE_SIZE;
        Ok(ArenaNode {
            tag_type: self.bytes[offset],
            flags: self.bytes[offset + 1],
            list_elem: read_u16(self.bytes, offset + 2)?,
            name_off: read_u32(self.bytes, offset + 4)? as usize,
            name_units: read_u32(self.bytes, offset + 8)? as usize,
            payload_off: read_u32(self.bytes, offset + 12)? as usize,
            payload_len: read_u32(self.bytes, offset + 16)? as usize,
            child_off: read_u32(self.bytes, offset + 20)? as usize,
            child_count: read_u32(self.bytes, offset + 24)? as usize,
        })
    }

    /// Returns `length` raw bytes at an absolute offset.
    pub fn bytes(&self, offset: usize, length: usize) -> Result<&'a [u8], NbtError> {
        let end = offset.checked_add(length).ok_or(NbtError)?;
        self.bytes.get(offset..end).ok_or(NbtError)
    }

    /// Returns the UTF-16 name code units for a node.
    pub fn name(&self, node: &ArenaNode) -> Result<&'a [u8], NbtError> {
        let bytes = node.name_units.checked_mul(2).ok_or(NbtError)?;
        let end = node.name_off.checked_add(bytes).ok_or(NbtError)?;
        self.bytes.get(node.name_off..end).ok_or(NbtError)
    }

    /// Returns the UTF-16 code units of a string payload.
    pub fn string_units(&self, node: &ArenaNode) -> Result<&'a [u8], NbtError> {
        let bytes = node.payload_len.checked_mul(2).ok_or(NbtError)?;
        let end = node.payload_off.checked_add(bytes).ok_or(NbtError)?;
        self.bytes.get(node.payload_off..end).ok_or(NbtError)
    }

    /// Returns the child node index at `position` within a node's child block.
    pub fn child(&self, node: &ArenaNode, position: usize) -> Result<u32, NbtError> {
        let offset = node
            .child_off
            .checked_add(position.checked_mul(4).ok_or(NbtError)?)
            .ok_or(NbtError)?;
        read_u32(self.bytes, offset)
    }
}

/// A decoded node with absolute offsets.
pub struct ArenaNode {
    pub tag_type: u8,
    pub flags: u8,
    pub list_elem: u16,
    pub name_off: usize,
    pub name_units: usize,
    pub payload_off: usize,
    pub payload_len: usize,
    pub child_off: usize,
    pub child_count: usize,
}

/// Aligns `value` up to the next multiple of four.
pub fn align4(value: usize) -> usize {
    (value + 3) & !3
}

pub fn write_u16(out: &mut [u8], offset: usize, value: u16) {
    out[offset..offset + 2].copy_from_slice(&value.to_le_bytes());
}

pub fn write_u32(out: &mut [u8], offset: usize, value: u32) {
    out[offset..offset + 4].copy_from_slice(&value.to_le_bytes());
}

pub fn read_u16(bytes: &[u8], offset: usize) -> Result<u16, NbtError> {
    let raw = bytes.get(offset..offset + 2).ok_or(NbtError)?;
    Ok(u16::from_le_bytes([raw[0], raw[1]]))
}

pub fn read_u32(bytes: &[u8], offset: usize) -> Result<u32, NbtError> {
    let raw = bytes.get(offset..offset + 4).ok_or(NbtError)?;
    Ok(u32::from_le_bytes([raw[0], raw[1], raw[2], raw[3]]))
}
