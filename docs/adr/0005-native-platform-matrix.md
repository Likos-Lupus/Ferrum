# ADR-0005: Native platform support matrix

- Status: Accepted
- Date: 2026-09-23
- Affected modules: FerrumCore

## Context

Ferrum ships native libraries per platform. Calling a target "supported" when it merely compiles
without being tested would overstate coverage and hide broken artifacts. The support matrix must be
explicit about which targets are required and which are conditional.

## Decision

Ferrum targets the following eight platform families. A target is only advertised as supported when
its smoke test passes.

| PlatformId          | Rust target                  | Library | Support strategy              |
|---------------------|------------------------------|---------|-------------------------------|
| windows-x86_64      | `x86_64-pc-windows-msvc`     | dll     | Required                      |
| windows-aarch64     | `aarch64-pc-windows-msvc`    | dll     | Required if runner verified   |
| macos-x86_64        | `x86_64-apple-darwin`        | dylib   | Required                      |
| macos-aarch64       | `aarch64-apple-darwin`       | dylib   | Required                      |
| linux-glibc-x86_64  | `x86_64-unknown-linux-gnu`   | so      | Required                      |
| linux-glibc-aarch64 | `aarch64-unknown-linux-gnu`  | so      | Required if runner verified   |
| linux-musl-x86_64   | `x86_64-unknown-linux-musl`  | so      | Optional until smoke verified |
| linux-musl-aarch64  | `aarch64-unknown-linux-musl` | so      | Optional until smoke verified |

A target that is not actually supported must be removed from the support matrix. "Compiles but
untested" is not "supported".

## Alternatives considered

- Shipping all eight unconditionally: rejected; untested artifacts must not be advertised.
- Shipping only one platform: rejected; it contradicts the cross-platform distribution goal.

## Performance evidence

Not applicable. This record defines support status, not speed.

## Correctness / compatibility impact

Each supported target must pass a native smoke test: load, call `ferrum_abi_version()`, call
`ferrum_build_info()`, and run a fixed checksum kernel. A failed smoke test removes the target from
the supported set.

## Rollback

Support status can be narrowed per target without changing the ABI. This decision is changed only by
a superseding ADR.

## Follow-up work

- Build the eight-target native CI matrix with per-target smoke tests.
- Generate the native `manifest.json` (ABI, version, commit, target triple, SHA-256, features).
