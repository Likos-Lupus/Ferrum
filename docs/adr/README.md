# Architecture Decision Records

This directory holds Ferrum's **frozen implementation decisions**.

These records are agent-facing **communication and execution** documents: they pin down choices that
implementation work must follow so that later steps do not drift back to earlier ambiguous wording.
They are deliberately self-contained and are **not** an external project specification and **not** a
changelog.

All files in this directory are written in English.

## Process

1. One decision per record. A record is numbered sequentially (`NNNN-short-title.md`).
2. An accepted record is **never edited in place**. To change a decision, add a new record with a
   higher number, set its status to `Accepted`, and set the superseded record's status to
   `Superseded by ADR-NNNN`.
3. Every record uses the structure below.
4. Real-time progress, task status, and "what I just did" notes do **not** belong here.

## Record structure

```text
# ADR-NNNN: <Title>

- Status: Proposed | Accepted | Rejected | Superseded
- Date: YYYY-MM-DD
- Affected modules: <modules or "all">

## Context

## Decision

## Alternatives considered

## Performance evidence

## Correctness / compatibility impact

## Rollback

## Follow-up work
```

## Index

| ADR                                                      | Title                                                | Status   |
|----------------------------------------------------------|------------------------------------------------------|----------|
| [0001](0001-frozen-implementation-decisions.md)          | Frozen implementation decisions                      | Accepted |
| [0002](0002-nbt-modified-utf8.md)                        | NBT strings use Modified UTF-8                       | Accepted |
| [0003](0003-lz4-block-stream-framing.md)                 | Codec LZ4 uses block-stream framing                  | Accepted |
| [0004](0004-arena-and-scratch-lifecycle.md)              | Arena and scratch-buffer lifecycle                   | Accepted |
| [0005](0005-native-platform-matrix.md)                   | Native platform support matrix                       | Accepted |
| [0006](0006-noise-and-float-bit-exactness.md)            | Noise and float bit-exactness policy                 | Accepted |
| [0007](0007-performance-multipliers-are-hypotheses.md)   | Performance multipliers are hypotheses               | Accepted |
| [0008](0008-mvp-and-v1-definition-of-done.md)            | MVP and v1 Definition of Done                        | Accepted |
| [0009](0009-stonecutter-version-axis.md)                 | Stonecutter version axis                             | Accepted |
| [0010](0010-stonecutter-controller-and-26x-toolchain.md) | Stonecutter module controller and the 26.x toolchain | Accepted |
| [0011](0011-error-prone-and-nullaway-pins.md)            | Error Prone and NullAway pins                        | Accepted |
| [0012](0012-nbt-flat-arena-format.md)                    | NBT flat arena format and entry points               | Accepted |
| [0013](0013-nbt-wire-forms.md)                           | NBT wire forms and the additive `*_any` entry points | Accepted |
