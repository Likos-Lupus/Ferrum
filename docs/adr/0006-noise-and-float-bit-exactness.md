# ADR-0006: Noise and float bit-exactness policy

- Status: Accepted
- Date: 2026-09-23
- Affected modules: FerrumNoise (and all numerically sensitive modules)

## Context

Noise and similar numeric modules must match Java bit for bit. Floating-point results can change
through fused multiply-add (FMA) contraction, fast-math, or manual reordering. Relying on an
unverified compiler flag to preserve results is not acceptable, because a flag may behave
differently across architectures or compiler versions.

## Decision

- Rust source preserves the Java operation order step by step.
- Do **not** use `mul_add`, fast-math, or manual reordering in numerically sensitive code.
- CI runs a raw-bits comparison on every supported architecture.
- Inspect disassembly/instructions for key functions. If a target produces a contraction that would
  change results, adjust codegen/target features for that target.
- "100% identical raw bits" is the sole release criterion. Epsilon relaxation is forbidden.
- Any target with a mismatch is **No-Go** for that path; the target may not be shipped as supported
  for that functionality.

## Alternatives considered

- Allowing an epsilon tolerance: rejected; it changes world generation and is not bit-exact.
- Trusting a compiler flag name without verification: rejected; it is platform- and
  version-dependent.

## Performance evidence

Correctness takes priority over speed for numeric modules. Performance is only considered after the
raw-bits gate passes on all supported targets.

## Correctness / compatibility impact

Raw-bits equivalence with the Java reference is required before a numeric fast path can be enabled.
Differential and property tests compare random parameters and coordinates.

## Rollback

Disable the affected module via configuration; the Java compute path remains. The decision itself is
changed only by a superseding ADR.

## Follow-up work

- Build the noise descriptor extractor and the raw-bits differential harness.
- Add per-architecture raw-bits CI coverage.
