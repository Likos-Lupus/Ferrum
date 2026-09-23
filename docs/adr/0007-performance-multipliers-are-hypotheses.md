# ADR-0007: Performance multipliers are hypotheses

- Status: Accepted
- Date: 2026-09-23
- Affected modules: all

## Context

Early analysis produced expected speedups (for example 2-6x and 3-10x). If such figures are treated
as commitments, they drive decisions that are not backed by measured evidence and create false
expectations.

## Decision

- All previously stated expected speedups are **experimental hypotheses**, not commitments.
- Whether a native path is enabled is decided by measured performance thresholds, not by an expected
  multiplier.
- Every fast path must pass both gates: kernel-level benefit and end-to-end benefit.
- Every performance claim reports baseline, data, hardware/JVM, sample size, and P50/P95/P99 or
  throughput.

## Alternatives considered

- Publishing expected multipliers as goals: rejected; unverified numbers distort prioritization.
- Ignoring performance entirely and shipping on correctness alone: rejected; a path with no measured
  benefit should not be enabled by default.

## Performance evidence

Thresholds are project-management gates and may be adjusted by a later ADR after a first benchmark
round. Benchmark reports must follow the fixed set of reporting fields.

## Correctness / compatibility impact

None directly. This record governs how performance decisions are justified.

## Rollback

Not applicable; the rule is a policy, changed only by a superseding ADR.

## Follow-up work

- Establish the JMH crossover benchmarks and the Minecraft scenario benchmarks.
- Record per-module thresholds in the configuration schema once measured.
