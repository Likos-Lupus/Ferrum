//! The create/use/destroy lifecycle for opaque native handles.
//!
//! Long-lived objects such as noise tables are created once, referenced many times through a
//! [`FerrumHandle`], and released exactly once. [`HandleRegistry`] implements that contract with
//! tagged handles so stale or foreign handles are rejected instead of silently dereferenced.

use std::collections::HashMap;
use std::sync::Mutex;
use std::sync::atomic::{AtomicU32, Ordering};

use crate::abi::FerrumHandle;

/// Tag stored in the high 32 bits of every handle, used to reject foreign values cheaply.
const HANDLE_TAG: u64 = 0xF0F0_1234_0000_0000;
/// Mask selecting the sequence part of a handle.
const HANDLE_SEQUENCE_MASK: u64 = 0x0000_0000_FFFF_FFFF;

/// A registry of native handles, one per payload type.
///
/// Create returns a non-zero tagged handle; use reads the payload through a closure so the entry
/// lock is held only for the duration of the borrow; destroy removes the entry exactly once.
pub struct HandleRegistry<T> {
    entries: Mutex<HashMap<FerrumHandle, T>>,
    next: AtomicU32,
}

impl<T> Default for HandleRegistry<T> {
    fn default() -> Self {
        Self::new()
    }
}

impl<T> HandleRegistry<T> {
    /// Creates an empty registry.
    pub fn new() -> Self {
        Self {
            entries: Mutex::new(HashMap::new()),
            next: AtomicU32::new(1),
        }
    }

    /// Registers `value` and returns its handle.
    pub fn create(&self, value: T) -> FerrumHandle {
        let mut value = Some(value);
        let mut entries = self.lock();
        loop {
            let sequence =
                u64::from(self.next.fetch_add(1, Ordering::Relaxed)) & HANDLE_SEQUENCE_MASK;
            let handle = HANDLE_TAG | sequence;
            if handle != HANDLE_TAG
                && let std::collections::hash_map::Entry::Vacant(slot) = entries.entry(handle)
            {
                slot.insert(value.take().expect("handle payload available"));
                return handle;
            }
        }
    }

    /// Runs `action` with the payload registered under `handle`.
    ///
    /// Returns `None` when the handle is unknown, stale, or foreign.
    pub fn with<R>(&self, handle: FerrumHandle, action: impl FnOnce(&T) -> R) -> Option<R> {
        if !is_tagged(handle) {
            return None;
        }
        self.lock().get(&handle).map(action)
    }

    /// Removes the entry for `handle`, returning `true` when it existed.
    pub fn destroy(&self, handle: FerrumHandle) -> bool {
        if !is_tagged(handle) {
            return false;
        }
        self.lock().remove(&handle).is_some()
    }

    /// Returns the number of live handles (diagnostics and leak tests).
    pub fn len(&self) -> usize {
        self.lock().len()
    }

    /// Returns whether the registry currently holds no handles.
    pub fn is_empty(&self) -> bool {
        self.lock().is_empty()
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<FerrumHandle, T>> {
        self.entries
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }
}

fn is_tagged(handle: FerrumHandle) -> bool {
    handle != 0 && (handle & !HANDLE_SEQUENCE_MASK) == HANDLE_TAG
}
