//! The block-light batch model and its scalar two-phase BFS.
//!
//! The kernel mirrors `LightEngine` / `BlockLightEngine`: it drains the check set, processes all
//! decreases, then all increases, using vanilla's packed queue-entry word and direction order. The
//! snapshot is fully flattened, so no Minecraft state is read during propagation.

use std::collections::{HashMap, VecDeque};

use crate::abi::FERRUM_ERR_LIMIT_EXCEEDED;

/// The maximum number of sections accepted in one batch.
pub const MAX_SECTIONS: usize = 512;
/// The maximum number of palette entries accepted in one batch.
pub const MAX_PALETTE: usize = 256;
/// The maximum number of queue or check entries accepted in one batch.
pub const MAX_QUEUE: usize = 1 << 20;
/// The number of cells in one section.
pub const CELLS: usize = 4096;

/// The flattened light-relevant properties of one block state.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Property {
    /// `max(1, lightDampening)`.
    pub opacity: u8,
    /// The block's light emission, before the per-section `lightOn` gate.
    pub emission: u8,
    /// Whether the state's occlusion shape is empty.
    pub empty_shape: bool,
}

/// One section in the snapshot.
pub struct Section {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub light_on: bool,
    pub props: Vec<u16>,
    pub levels: Vec<u8>,
    /// Whether any level changed relative to the snapshot.
    pub changed: bool,
}

/// A discrete block position.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Node {
    pub x: i32,
    pub y: i32,
    pub z: i32,
}

impl Node {
    /// Returns the containing section coordinates.
    #[inline]
    pub fn section(self) -> (i32, i32, i32) {
        (self.x >> 4, self.y >> 4, self.z >> 4)
    }

    /// Returns the vanilla cell index within the section (`y << 8 | z << 4 | x`).
    #[inline]
    pub fn cell(self) -> usize {
        (((self.y & 15) as usize) << 8) | (((self.z & 15) as usize) << 4) | ((self.x & 15) as usize)
    }

    /// Returns the node offset one step in `direction` (DOWN, UP, NORTH, SOUTH, WEST, EAST).
    #[inline]
    pub fn offset(self, direction: usize) -> Self {
        let (dx, dy, dz) = DELTAS[direction];
        Self {
            x: self.x + dx,
            y: self.y + dy,
            z: self.z + dz,
        }
    }
}

/// The vanilla `Direction.values()` order: DOWN, UP, NORTH, SOUTH, WEST, EAST.
const DELTAS: [(i32, i32, i32); 6] = [
    (0, -1, 0),
    (0, 1, 0),
    (0, 0, -1),
    (0, 0, 1),
    (-1, 0, 0),
    (1, 0, 0),
];

/// `direction.getOpposite().ordinal()` for the direction order above.
const OPPOSITE: [usize; 6] = [1, 0, 3, 2, 5, 4];

const DIRECTIONS_MASK: u64 = 1008;
const FLAG_FROM_EMPTY_SHAPE: u64 = 1024;
const FLAG_INCREASE_FROM_EMISSION: u64 = 2048;

#[inline]
const fn with_level(entry: u64, level: i32) -> u64 {
    (entry & !15u64) | (level as u64 & 15)
}

#[inline]
const fn with_direction(entry: u64, direction: usize) -> u64 {
    entry | (1u64 << (direction + 4))
}

#[inline]
const fn without_direction(entry: u64, direction: usize) -> u64 {
    entry & !(1u64 << (direction + 4))
}

#[inline]
const fn decrease_all(level: i32) -> u64 {
    with_level(DIRECTIONS_MASK, level)
}

#[inline]
const fn decrease_skip(level: i32, direction: usize) -> u64 {
    with_level(without_direction(DIRECTIONS_MASK, direction), level)
}

#[inline]
const fn increase_from_emission(level: i32, empty_shape: bool) -> u64 {
    let mut entry = DIRECTIONS_MASK | FLAG_INCREASE_FROM_EMISSION;
    if empty_shape {
        entry |= FLAG_FROM_EMPTY_SHAPE;
    }
    with_level(entry, level)
}

#[inline]
const fn increase_skip(level: i32, empty_shape: bool, direction: usize) -> u64 {
    let mut entry = without_direction(DIRECTIONS_MASK, direction);
    if empty_shape {
        entry |= FLAG_FROM_EMPTY_SHAPE;
    }
    with_level(entry, level)
}

#[inline]
const fn increase_only(level: i32, empty_shape: bool, direction: usize) -> u64 {
    let mut entry = 0u64;
    if empty_shape {
        entry |= FLAG_FROM_EMPTY_SHAPE;
    }
    with_level(with_direction(entry, direction), level)
}

const PULL_LIGHT_IN_ENTRY: u64 = decrease_all(1);

#[inline]
fn from_level(entry: u64) -> i32 {
    (entry & 15) as i32
}

#[inline]
fn is_from_empty_shape(entry: u64) -> bool {
    entry & FLAG_FROM_EMPTY_SHAPE != 0
}

#[inline]
fn is_increase_from_emission(entry: u64) -> bool {
    entry & FLAG_INCREASE_FROM_EMISSION != 0
}

#[inline]
fn should_propagate(entry: u64, direction: usize) -> bool {
    entry & (1u64 << (direction + 4)) != 0
}

/// One parsed block-light batch.
pub struct Batch {
    pub palette: Vec<Property>,
    pub sections: Vec<Section>,
    pub index: HashMap<(i32, i32, i32), usize>,
    pub decreases: VecDeque<(Node, u64)>,
    pub increases: VecDeque<(Node, u64)>,
    pub checks: Vec<Node>,
}

impl Batch {
    /// Builds a batch and its section index, validating the section count.
    pub fn new(
        palette: Vec<Property>,
        sections: Vec<Section>,
        decreases: VecDeque<(Node, u64)>,
        increases: VecDeque<(Node, u64)>,
        checks: Vec<Node>,
    ) -> Result<Self, i32> {
        if sections.len() > MAX_SECTIONS
            || palette.len() > MAX_PALETTE
            || decreases.len() > MAX_QUEUE
            || increases.len() > MAX_QUEUE
            || checks.len() > MAX_QUEUE
        {
            return Err(FERRUM_ERR_LIMIT_EXCEEDED);
        }
        let mut index = HashMap::with_capacity(sections.len());
        for (slot, section) in sections.iter().enumerate() {
            index.insert((section.x, section.y, section.z), slot);
        }
        Ok(Self {
            palette,
            sections,
            index,
            decreases,
            increases,
            checks,
        })
    }

    #[inline]
    fn section_slot(&self, node: Node) -> Option<usize> {
        self.index.get(&node.section()).copied()
    }

    #[inline]
    fn level(&self, node: Node) -> i32 {
        self.section_slot(node)
            .map_or(0, |slot| i32::from(self.sections[slot].levels[node.cell()]))
    }

    #[inline]
    fn set_level(&mut self, node: Node, level: i32) {
        if let Some(slot) = self.section_slot(node) {
            let cell = node.cell();
            let value = level as u8 & 15;
            let section = &mut self.sections[slot];
            if section.levels[cell] != value {
                section.levels[cell] = value;
                section.changed = true;
            }
        }
    }

    #[inline]
    fn property(&self, node: Node) -> Property {
        let slot = self.section_slot(node).expect("property for a stored node");
        let section = &self.sections[slot];
        let property = section.props[node.cell()];
        self.palette[property as usize]
    }

    #[inline]
    fn opacity(&self, node: Node) -> i32 {
        i32::from(self.property(node).opacity.max(1))
    }

    #[inline]
    fn empty_shape(&self, node: Node) -> bool {
        self.property(node).empty_shape
    }

    #[inline]
    fn emission(&self, node: Node) -> i32 {
        let slot = self.section_slot(node).expect("emission for a stored node");
        let section = &self.sections[slot];
        let property = self.palette[section.props[node.cell()] as usize];
        if property.emission > 0 && section.light_on {
            i32::from(property.emission)
        } else {
            0
        }
    }

    /// Runs the full batch and returns the number of processed queue entries.
    pub fn run(&mut self) -> u64 {
        let checks = std::mem::take(&mut self.checks);
        for node in checks {
            self.check_node(node);
        }

        let mut processed = 0u64;
        while let Some((node, entry)) = self.decreases.pop_front() {
            processed += 1;
            self.propagate_decrease(node, entry);
        }
        while let Some((node, entry)) = self.increases.pop_front() {
            processed += 1;
            self.propagate_increase_entry(node, entry);
        }
        processed
    }

    fn check_node(&mut self, node: Node) {
        if self.section_slot(node).is_none() {
            return;
        }
        let emission = self.emission(node);
        let old_level = self.level(node);
        if emission < old_level {
            self.set_level(node, 0);
            self.decreases.push_back((node, decrease_all(old_level)));
        } else {
            self.decreases.push_back((node, PULL_LIGHT_IN_ENTRY));
        }
        if emission > 0 {
            self.increases.push_back((
                node,
                increase_from_emission(emission, self.empty_shape(node)),
            ));
        }
    }

    fn propagate_decrease(&mut self, from: Node, entry: u64) {
        let old_from_level = from_level(entry);
        for (direction, &opposite) in OPPOSITE.iter().enumerate() {
            if !should_propagate(entry, direction) {
                continue;
            }
            let to = from.offset(direction);
            if self.section_slot(to).is_none() {
                continue;
            }
            let to_level = self.level(to);
            if to_level == 0 {
                continue;
            }
            if to_level < old_from_level {
                let to_emission = self.emission(to);
                self.set_level(to, 0);
                if to_emission < to_level {
                    self.decreases
                        .push_back((to, decrease_skip(to_level, opposite)));
                }
                if to_emission > 0 {
                    self.increases.push_back((
                        to,
                        increase_from_emission(to_emission, self.empty_shape(to)),
                    ));
                }
            } else {
                self.increases
                    .push_back((to, increase_only(to_level, false, opposite)));
            }
        }
    }

    fn propagate_increase_entry(&mut self, from: Node, entry: u64) {
        let mut current_level = self.level(from);
        let target = from_level(entry);
        if is_increase_from_emission(entry) && current_level < target {
            self.set_level(from, target);
            current_level = target;
        }
        if current_level == target {
            self.propagate_increase(from, entry, current_level);
        }
    }

    #[cfg(test)]
    pub(crate) fn level_at(&self, x: i32, y: i32, z: i32) -> i32 {
        self.level(Node { x, y, z })
    }

    fn propagate_increase(&mut self, from: Node, entry: u64, from_level: i32) {
        let from_empty = is_from_empty_shape(entry);
        for (direction, &opposite) in OPPOSITE.iter().enumerate() {
            if !should_propagate(entry, direction) {
                continue;
            }
            let to = from.offset(direction);
            if self.section_slot(to).is_none() {
                continue;
            }
            let to_level = self.level(to);
            if from_level <= to_level + 1 {
                continue;
            }
            let new_to_level = from_level - self.opacity(to);
            if new_to_level <= to_level {
                continue;
            }
            let from_occludes = if from_empty {
                false
            } else {
                !self.empty_shape(from)
            };
            let to_occludes = !self.empty_shape(to);
            if from_occludes || to_occludes {
                continue;
            }
            self.set_level(to, new_to_level);
            if new_to_level > 1 {
                self.increases.push_back((
                    to,
                    increase_skip(new_to_level, self.empty_shape(to), opposite),
                ));
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::VecDeque;

    fn air() -> Property {
        Property {
            opacity: 1,
            emission: 0,
            empty_shape: true,
        }
    }

    fn source() -> Property {
        Property {
            opacity: 1,
            emission: 15,
            empty_shape: true,
        }
    }

    fn opaque() -> Property {
        Property {
            opacity: 15,
            emission: 0,
            empty_shape: false,
        }
    }

    fn index(x: i32, y: i32, z: i32) -> usize {
        Node { x, y, z }.cell()
    }

    fn single_section(palette: Vec<Property>, props: Vec<u16>, levels: Vec<u8>) -> Batch {
        let section = Section {
            x: 0,
            y: 0,
            z: 0,
            light_on: true,
            props,
            levels,
            changed: false,
        };
        Batch::new(
            palette,
            vec![section],
            VecDeque::new(),
            VecDeque::new(),
            Vec::new(),
        )
        .expect("batch")
    }

    #[test]
    fn emission_propagates_and_attenuates() {
        let mut props = vec![0u16; CELLS];
        props[index(8, 8, 8)] = 1;
        let mut batch = single_section(vec![air(), source()], props, vec![0u8; CELLS]);
        let checks = vec![Node { x: 8, y: 8, z: 8 }];
        batch.checks = checks;
        batch.run();

        assert_eq!(batch.level_at(8, 8, 8), 15);
        assert_eq!(batch.level_at(9, 8, 8), 14);
        assert_eq!(batch.level_at(10, 8, 8), 13);
        assert_eq!(batch.level_at(6, 6, 6), 9);
        assert!(batch.sections[0].changed);
    }

    #[test]
    fn opaque_block_stops_light() {
        let mut props = vec![0u16; CELLS];
        props[index(8, 8, 8)] = 1;
        props[index(9, 8, 8)] = 2;
        let mut batch = single_section(vec![air(), source(), opaque()], props, vec![0u8; CELLS]);
        batch.checks = vec![Node { x: 8, y: 8, z: 8 }];
        batch.run();

        assert_eq!(batch.level_at(8, 8, 8), 15);
        assert_eq!(batch.level_at(9, 8, 8), 0);
        // The direct neighbour is blocked, so the cell beyond is lit only by a detour.
        assert_eq!(batch.level_at(10, 8, 8), 11);
        assert_eq!(batch.level_at(8, 9, 8), 14);
    }

    #[test]
    fn decrease_clears_unreachable_light() {
        let mut levels = vec![0u8; CELLS];
        for y in 0i32..16 {
            for z in 0i32..16 {
                for x in 0i32..16 {
                    let distance = (x - 8).abs() + (y - 8).abs() + (z - 8).abs();
                    levels[index(x, y, z)] = (15 - distance).max(0) as u8;
                }
            }
        }
        let mut batch = single_section(vec![air()], vec![0u16; CELLS], levels);
        batch.checks = vec![Node { x: 8, y: 8, z: 8 }];
        batch.run();

        for y in 0..16 {
            for z in 0..16 {
                for x in 0..16 {
                    assert_eq!(batch.level_at(x, y, z), 0, "at {x},{y},{z}");
                }
            }
        }
    }

    #[test]
    fn opaque_nodes_are_not_in_the_section_snapshot() {
        let batch = single_section(vec![air()], vec![0u16; CELLS], vec![0u8; CELLS]);
        assert!(batch.section_slot(Node { x: 16, y: 0, z: 0 }).is_none());
        assert_eq!(batch.level(Node { x: 16, y: 0, z: 0 }), 0);
    }
}
