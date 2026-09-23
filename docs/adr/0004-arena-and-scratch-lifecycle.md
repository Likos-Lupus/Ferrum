# ADR-0004: Arena and scratch-buffer lifecycle

- Status: Accepted
- Date: 2026-09-23
- Affected modules: all (Core runtime)

## Context

FFM `Arena.ofConfined()` has no reset semantics that would make it safe to reuse as an unbounded
per-tick object pool. Treating a confined arena as a reusable pool accumulates memory and leaks.
Ferrum needs a clear, bounded lifecycle for long-lived native tables and for temporary buffers
across world/resource and thread boundaries.

## Decision

Memory is handled in three classes with explicit ownership.

- **Read-only resident tables** (for example Noise tables, light attribute tables): create a
  dedicated shared arena / native handle scoped to the world or resource lifecycle, and release it
  explicitly on unload/reload.
- **Temporary buffers** (thread-local scratch): a ThreadLocal `NativeScratch` holds fixed-capacity
  segments for bytes, ints, longs, and doubles. On insufficient capacity, close the old arena,
  create a new arena, and grow (next power of two or 1.5x). Never allocate unboundedly into a single
  arena.
- **Small one-shot temporaries**: prefer stack-style Java arrays or a reused direct buffer to avoid
  frequent native allocation.

Ownership rules:

- `Arena.ofConfined` segments are only accessed by the owning thread; a segment stored in a
  ThreadLocal is never handed to another worker.
- Data shared across workers must use a shareable lifetime, or a native handle owned by Rust.
- The native kernel uses no global mutable scratch; all transient state lives on the stack, in call
  arguments, or in thread-local storage.

## Alternatives considered

- Unbounded per-tick allocation into one arena: rejected as an unsafe pseudo-pool.
- Global mutable scratch inside Rust: rejected; it would introduce data races and hidden state.
- A Rust internal thread pool: rejected by ADR-0001 (FD-08) pending separate evidence.

## Performance evidence

No performance commitment in this record. Scratch sizing and growth behavior are validated by the
FFM crossover benchmarks.

## Correctness / compatibility impact

Prevents use-after-free and cross-thread misuse of confined arenas. Handle creation and destruction
are explicit; `Cleaner` is only a leak backstop, never the normal release path.

## Rollback

Scratch buffers can be reduced to plain Java arrays if native allocation proves costly; the
lifecycle decision is changed only by a superseding ADR.

## Follow-up work

- Define the world/resource lifecycle events that create and destroy handles.
- Add leak/counter diagnostics for buffer growth and handle destruction.
