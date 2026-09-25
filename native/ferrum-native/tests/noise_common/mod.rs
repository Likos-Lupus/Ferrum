//! Shared descriptor builders for the noise integration tests.
//!
//! These tests construct the descriptor blob themselves rather than depending on the parser, so the
//! FFI surface is exercised end to end without the Java generator.

#![allow(dead_code)]

pub const MAGIC: &[u8; 4] = b"FBNS";
pub const VERSION: u8 = 1;
pub const KIND_IMPROVED: u8 = 1;
pub const KIND_PERLIN: u8 = 2;
pub const KIND_NORMAL: u8 = 3;

/// One `ImprovedNoise` level.
#[derive(Clone)]
pub struct ImprovedSpec {
    pub xo: f64,
    pub yo: f64,
    pub zo: f64,
    pub permutation: [u8; 256],
}

impl ImprovedSpec {
    /// An identity permutation with the given offsets.
    pub fn identity() -> Self {
        let mut permutation = [0u8; 256];
        for (index, value) in permutation.iter_mut().enumerate() {
            *value = index as u8;
        }
        Self {
            xo: 0.0,
            yo: 0.0,
            zo: 0.0,
            permutation,
        }
    }
}

/// One `PerlinNoise`.
pub struct PerlinSpec {
    pub first_octave: i32,
    pub lowest_freq_input_factor: f64,
    pub lowest_freq_value_factor: f64,
    pub amplitudes: Vec<f64>,
    pub levels: Vec<Option<ImprovedSpec>>,
}

/// The four-byte magic, version, and kind prefix.
pub fn header(kind: u8) -> Vec<u8> {
    let mut bytes = Vec::new();
    bytes.extend_from_slice(MAGIC);
    bytes.push(VERSION);
    bytes.push(kind);
    bytes
}

/// Appends one `ImprovedNoise` body.
pub fn push_improved(bytes: &mut Vec<u8>, spec: &ImprovedSpec) {
    bytes.extend_from_slice(&spec.xo.to_le_bytes());
    bytes.extend_from_slice(&spec.yo.to_le_bytes());
    bytes.extend_from_slice(&spec.zo.to_le_bytes());
    bytes.extend_from_slice(&spec.permutation);
}

/// Appends one `PerlinNoise` body.
pub fn push_perlin(bytes: &mut Vec<u8>, spec: &PerlinSpec) {
    bytes.extend_from_slice(&spec.first_octave.to_le_bytes());
    bytes.extend_from_slice(&spec.lowest_freq_input_factor.to_le_bytes());
    bytes.extend_from_slice(&spec.lowest_freq_value_factor.to_le_bytes());
    bytes.extend_from_slice(&(spec.amplitudes.len() as u32).to_le_bytes());
    for amplitude in &spec.amplitudes {
        bytes.extend_from_slice(&amplitude.to_le_bytes());
    }
    for improved in spec.levels.iter().flatten() {
        push_improved(bytes, improved);
    }
}

/// Builds a complete `Perlin` descriptor.
pub fn perlin_descriptor(spec: &PerlinSpec) -> Vec<u8> {
    let mut bytes = header(KIND_PERLIN);
    push_perlin(&mut bytes, spec);
    bytes
}

/// Builds a complete `Improved` descriptor.
pub fn improved_descriptor(spec: &ImprovedSpec) -> Vec<u8> {
    let mut bytes = header(KIND_IMPROVED);
    push_improved(&mut bytes, spec);
    bytes
}

/// Builds a complete `Normal` descriptor.
pub fn normal_descriptor(value_factor: f64, first: &PerlinSpec, second: &PerlinSpec) -> Vec<u8> {
    let mut bytes = header(KIND_NORMAL);
    bytes.extend_from_slice(&value_factor.to_le_bytes());
    push_perlin(&mut bytes, first);
    push_perlin(&mut bytes, second);
    bytes
}
