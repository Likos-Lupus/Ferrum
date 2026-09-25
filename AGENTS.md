# AGENTS.md · Ferrum Project Guide

This file is the **navigation and direction** document for agents working in this repository. It is
a stable map of the project and its rules — not a changelog, not a status board, and not a place to
record real-time progress. Do not add per-task status, TODOs-in-flight, or "what I just did" notes
here; those belong in the task board, milestone tracking, or dedicated notes.

Frozen implementation rules live in the git-tracked, English ADRs under
[`docs/adr/`](docs/adr/README.md). Those records are authoritative for the decisions they cover;
when this file and an ADR disagree on a frozen decision, the ADR wins. A decision is changed by
adding a new ADR — accepted records are superseded, never edited in place.

---

## 1. Project Summary

Ferrum optimizes Minecraft **1.21.1** and **26.1.2** by moving batch/flat/vectorizable or
allocation-heavy work from the JVM into a **Rust native kernel** accessed through **Java 25 FFM**.

- Loaders: **Fabric + NeoForge** (shared `common` layer, thin loader glue).
- Distribution: multi-module, independently shippable; **every functional module depends on
  FerrumCore**.
- Java root namespace: `top.likoslupus.ferrum` (Gradle `group` and all project-owned packages).
- Native: a single `libferrum` with a versioned, Minecraft-independent C ABI.
- Java fallback (vanilla behavior) is always retained; native is never the only implementation.
- Java baseline: JSpecify `@NullMarked` + NullAway/Error Prone, **Jackson 3**, **JUnit 6 /
  Jupiter**, **Stonecutter** (Minecraft version axis) + **Architectury Loom** (loader build).

### Core philosophy

Ferrum is not "Java mechanically translated to Rust". It handles work the JVM is bad at, keeps
vanilla behavior as the fallback, and degrades cleanly. A module is only "releasable" when it is
**behavior-correct, memory-safe, genuinely faster, and operable** (switch, logging, status,
fallback).

---

## 2. Frozen Decision Records

Frozen implementation rules are kept as self-contained, English ADRs under
[`docs/adr/`](docs/adr/README.md). They are the git-tracked execution reference; consult them
together with this file. Accepted records are superseded by new ADRs, never edited in place.

| ADR                                                               | Title                                                                             |
|-------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| [0001](docs/adr/0001-frozen-implementation-decisions.md)          | Frozen implementation decisions (platform, architecture invariants, Java quality) |
| [0002](docs/adr/0002-nbt-modified-utf8.md)                        | NBT strings use Modified UTF-8                                                    |
| [0003](docs/adr/0003-lz4-block-stream-framing.md)                 | Codec LZ4 uses block-stream framing                                               |
| [0004](docs/adr/0004-arena-and-scratch-lifecycle.md)              | Arena and scratch-buffer lifecycle                                                |
| [0005](docs/adr/0005-native-platform-matrix.md)                   | Native platform support matrix                                                    |
| [0006](docs/adr/0006-noise-and-float-bit-exactness.md)            | Noise and float bit-exactness policy                                              |
| [0007](docs/adr/0007-performance-multipliers-are-hypotheses.md)   | Performance multipliers are hypotheses                                            |
| [0008](docs/adr/0008-mvp-and-v1-definition-of-done.md)            | MVP and v1 Definition of Done                                                     |
| [0009](docs/adr/0009-stonecutter-version-axis.md)                 | Stonecutter version axis                                                          |
| [0010](docs/adr/0010-stonecutter-controller-and-26x-toolchain.md) | Stonecutter module controller and the 26.x toolchain                              |
| [0011](docs/adr/0011-error-prone-and-nullaway-pins.md)            | Error Prone and NullAway pins                                                     |
| [0012](docs/adr/0012-nbt-flat-arena-format.md)                    | NBT flat arena format and entry points                                            |
| [0013](docs/adr/0013-nbt-wire-forms.md)                           | NBT wire forms and the additive `*_any` entry points                              |
| [0014](docs/adr/0014-codec-lz4-stream.md)                         | Codec LZ4 block-stream implementation and gating                                  |
| [0015](docs/adr/0015-palette-bit-storage-layout.md)               | Palette bulk pack/unpack uses the `SimpleBitStorage` layout                       |
| [0016](docs/adr/0016-production-fused-palette-remap.md)           | Production fused palette remap (proposed, from the F-055 spike)                   |
| [0017](docs/adr/0017-noise-descriptor-and-leaf-grid.md)           | Noise descriptor, handle ABI, and leaf-grid batching                              |
| [0018](docs/adr/0018-block-light-batch-abi.md)                    | Block-light batch ABI and versioned snapshot blob                                 |

---

## 3. Big Direction

### Execution order

1. Freeze the implementation rules (ADR-0001).
2. Define scope and the MVP/v1 Definition of Done (ADR-0008).
3. Scaffold the repository layout and build chain.
4. Build the cross-language foundation: ABI/error model and memory/thread lifecycle.
5. Implement modules strictly in dependency order: **Core → NBT → Codec/Palette → Noise →
   Light/Collide/Path**.
6. Thin per-version adapters / Mixins.
7. Enforce correctness **and** performance dual gates before any optimization ships.
8. Deliver repeatably via CI and milestone gates.

### Staged scope

- **MVP-A (foundation):** FerrumCore + ABI self-check + Java fallback + native CI matrix.
- **MVP-B (storage):** FerrumNbt generic tree fast path, FerrumCodec region LZ4, FerrumPalette bulk
  pack/unpack.
- **MVP-C (worldgen):** FerrumNoise leaf/grid batch (no full Router DAG yet).
- **v1 later:** FerrumLight, FerrumCollide, FerrumPath; network zstd and Noise Router DAG are opt-in
  experiments that must **not** block the first stable release.

### Milestones (estimates, not commitments)

`M0` engineering foundation → `M1` Core → `M2` NBT → `M3` Codec+Palette → `M4` Noise →
`M5` stable MVP → `M6` Light → `M7` Collide → `M8` Path.

---

## 4. Non-Negotiable Engineering Principles

These are frozen by ADR-0001 and ADR-0002 through ADR-0007 and must not be traded away:

- The **Java vanilla path is always the final fallback**; native is never the sole correct
  implementation.
- The native kernel only accepts **flat, explicitly-sized, validated** data and **never holds Java
  object references**.
- **No per-block / per-node / per-value FFM calls**; every native fast path has a minimum batch
  threshold.
- Numeric modules (Noise, collision) target **bit-exact / behavior-exact** Java equivalence first,
  speed second.
- `Noise` bit-exactness is **raw-bits 0 mismatch**; epsilon relaxation is forbidden. Any mismatching
  target is No-Go.
- Never unwind across FFI: **no panic/cross-language unwind**; every native failure becomes a status
  code.
- All length/offset arithmetic is **checked**; memory errors are never tolerated, only cleanly
  degraded.
- No nested parallelism by default (no internal Rust thread pools in v1).
- Every performance claim must report baseline, data, hardware/JVM, sample size, and P50/P95/P99 or
  throughput.
- Codec LZ4 must interoperate with vanilla's **block-stream framing** in both directions.
- NBT strings are **Modified UTF-8 (MUTF-8)**, not standard UTF-8.

---

## 5. Operating Rules (Hard Requirements)

These rules apply to every agent working in this repository.

1. **Never overwrite existing files.**
    - Use `edit` / patch tools for targeted, diff-style changes.
    - Reserve `write` / `create_new_file` / IDE create-file tools for **newly introduced files
      only**.
    - For Rust code, prefer RustRover MCP tools; for Java/JVM, prefer IntelliJ MCP tools.

2. **Language: English in git.**
    - `SKILLS.md`, all in-code documentation/comments, and every documentation file that is
      committed to git must be written in **English**. Local, non-git reference material is the only
      exception (see rule 3).

3. **Local-only reference material must not enter git.**
    - Never `git add` / commit / stage anything under the untracked `Ferrum-可执行实施方案包/`
      directory.
    - `.idea/` is ignored except the shared `.idea/inspectionProfiles/` and `.idea/codeStyles/`
      settings, which may be tracked; keep every other IDE file out of git.

4. **Do not commit unless explicitly asked.** Inspect `git status` / `git diff` first; stage only
   intended files.

5. **Do not add code comments unless explicitly requested.**

6. **This file is navigation only.** Do not record real-time progress, per-task status, or
   changelogs here.

7. **Use SKILLS.md for the tool/skill inventory.** It is the authoritative reference for available
   MCP servers, subagents, and tools — do not duplicate it here.
    - Rust operations → RustRover MCP (`rustrover_*`).
    - Java / Gradle / Fabric / NeoForge / root project → IntelliJ MCP (`intellij-idea_*`).
    - Library/API docs → Context7 (`context7_*`).

---

## 6. Java Quality Baseline (frozen)

- `group` and all project-owned Java packages live under `top.likoslupus.ferrum`; no second root
  package.
- Every project-owned package (main/test/testFixtures) has `package-info.java` marked `@NullMarked`
  (JSpecify). Sub-packages do not inherit it — declare each one.
- Error Prone + NullAway are **compile blockers**: `NullAway=ERROR`, `OnlyNullMarked=true`,
  `RequireExplicitNullMarking=ERROR`, `-Xlint:all -Werror`.
- Structured data (JSON/YAML/TOML, configs, manifests, reports, repro metadata) goes through the
  single **Jackson 3** `DataFormats` facade. No second data-binding stack (no Jackson 2 databind,
  Gson, SnakeYAML, Tomlj). Jackson 2 `annotations` are tolerated transitively only.
- Java tests are **JUnit 6 / Jupiter** with `useJUnitPlatform()`.
- Java 25, no preview features in release builds; prefer records/sealed types/pattern matching, but
  keep hot paths on predictable primitives and explicit loops. No raw types, wildcard imports, or
  `@SuppressWarnings("all")`.
- The Minecraft version axis (1.21.1 / 26.1.2) is managed by **Stonecutter** over a single source
  tree per module; the loader build/remap/run environment is **Architectury Loom**. Canonical active
  target is `26.1.2-fabric`; all four targets are checked in aggregate. Stonecutter conditions must
  not reach `java-shared`, the FFM ABI, the Rust kernel, or pure algorithm code.
- Fabric + NeoForge share the `common` design via **Architectury Loom**; Architectury API only at
  low-frequency loader glue boundaries, never in FFM/algorithm hot paths.

Full detail: ADR-0001 (Java quality freeze, JQ-01 through JQ-04) and ADR-0009 (Stonecutter version
axis).

---

## 7. Future Repository Layout

When the codebase is scaffolded it should follow this layout:

```text
ferrum/
├─ settings.gradle.kts / build.gradle.kts / gradle/
├─ build-logic/            # Gradle convention plugins
├─ native/ferrum-native/   # Rust kernel: lib.rs, abi.rs, core.rs, nbt/, codec/, noise/ ...
├─ java-shared/            # core-runtime, testkit, module-api
├─ modules/                # ferrum-core, ferrum-nbt, ... one src/ tree + Stonecutter targets
├─ integration-tests/      # fixtures/{1.21.1,26.1.2}/ + scenarios/ (target-aware)
├─ benchmark-worlds/       # scripts/generators only, no large worlds
├─ docs/
└─ scripts/                # build-native, package-native, run-differential, run-benchmarks
```

Dependency direction (reverse dependencies forbidden):

```text
Stonecutter target (<mc>-<loader>) → Loader entrypoint → MC version adapter/Mixin
    → module-api + core-runtime → FFM ABI → libferrum
```

The native layer knows nothing about loaders; `core-runtime` knows nothing about Minecraft classes.
