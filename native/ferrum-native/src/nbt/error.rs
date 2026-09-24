//! Shared error type for internal NBT helpers.

/// A malformed representation detected outside the status-coded ABI boundary.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct NbtError;
