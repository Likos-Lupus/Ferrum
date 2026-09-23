# ADR-0009: Stonecutter version axis

- Status: Accepted
- Date: 2026-09-23
- Affected modules: all Minecraft-facing modules (`ferrum-core`, later `ferrum-nbt`, …); build chain

## Context

Ferrum targets two Minecraft generations (1.21.1 and 26.1.2) across two loaders (Fabric and
NeoForge), and every functional module is independently shippable. Maintaining a separate,
near-duplicate source tree per Minecraft version inside every module does not scale: the copies
drift, the same fix must be applied twice, and per-module review becomes unreliable.

Ferrum therefore separates two axes that were previously conflated:

- the **Minecraft version axis** — which game APIs a source file compiles against;
- the **loader axis** — how a build is remapped and run.

This record freezes how the version axis is organised. It does not change the module boundaries, the
FFM ABI, the Rust kernel, or the Java quality gates frozen by ADR-0001.

## Decision

### Responsibility split

- **Stonecutter** owns the Minecraft version axis and target orchestration. It is a build-time tool
  only and must never become a runtime dependency of any released JAR.
- **Architectury Loom** owns the loader build environment: mappings, remap, and run configs for both
  Fabric and NeoForge.
- **Architectury API** remains optional and is used only at low-frequency loader glue boundaries,
  never in FFM or algorithm hot paths.
- The Java shared layer, the FFM ABI, the Rust/`libferrum` kernel, and pure algorithm reference
  implementations contain **no** Stonecutter conditions.

### Targets and naming

| Target ID         | Logical Minecraft | Loader   | Java |
|-------------------|-------------------|----------|------|
| `1.21.1-fabric`   | 1.21.1            | Fabric   | 25   |
| `1.21.1-neoforge` | 1.21.1            | NeoForge | 25   |
| `26.1.2-fabric`   | 26.1.2            | Fabric   | 25   |
| `26.1.2-neoforge` | 26.1.2            | NeoForge | 25   |

- A target ID is `<mc>-<loader>`.
- The logical version passed to version comparisons is the **bare** Minecraft version (`1.21.1`,
  `26.1.2`); the loader suffix must not leak into version arithmetic.
- The canonical active target, i.e. the committed state, is **`26.1.2-fabric`**. It is the IDE's
  default resolution target, not a statement about supported scope; all four targets remain
  supported and are checked in aggregate.

### Single source tree per module

Each Minecraft-facing module keeps exactly one hand-written source tree:

```text
modules/<module>/
  src/main/java/
  src/main/resources/
  src/test/java/
  build.fabric.gradle.kts
  build.neoforge.gradle.kts
  stonecutter.gradle.kts
  versions/<mc>-<loader>/     # Stonecutter node space; no hand-written business source
```

`versions/*` is target/node working space. It is not a second source of truth and must not receive
hand-written module logic.

### Controller model

The preferred model is a **module controller** — one Stonecutter controller per independently
shipped module, registered from the root build, e.g. `create(project(":modules:<module>"))` with a
per-loader build script. Because the module-controller form must be validated on the pinned
Gradle/Stonecutter combination before it is relied upon, the fallback defined by this record is:
promote each
`modules/<module>` controller to a Gradle **included build** and let the top-level composite build
aggregate. The fallback must not reintroduce duplicated `mc-*` source trees.

### Version differences in source

- **Identical code is the default** — do nothing.
- **Small, local API differences** may use Stonecutter guards, provided both branches have the same
  semantics, lifecycle, and fallback, and can share differential tests.
- **Large structural differences** (control flow, data ownership, thread model, injection structure)
  must be separate adapter/Mixin classes selected by Stonecutter — never a dense block of guards
  inside one class. As a rule of thumb, a single guarded branch beyond roughly 20 lines, or several
  nested guards in one method, means "split the class".
- Mixin classes whose descriptor, locals, ordinal, or host class differ across versions are always
  separate classes; fragile injections are never shared for the sake of a single file.

### Loader differences

Fabric and NeoForge specifics (entrypoints, metadata, Access Widener / Access Transformer) remain
physically separated under `<module>.fabric` / `<module>.neoforge` and the loader-specific build
scripts. Stonecutter's `fabric` / `neoforge` constants are reserved for tiny compile differences;
they do not replace the loader packages.

### Resources

Strict JSON (`fabric.mod.json`, `*.mixins.json`, schema/config fixtures) must not contain
Stonecutter comment guards. Final valid JSON is produced by loader-specific templates expanded in
`processResources`, by target-aware resource selection, or by a generation task. Access Widener /
Access Transformer files are selected per loader/target; version-independent access rules are not
duplicated four times.

### Nullability and static analysis per target

- Every project-owned package still declares `package-info.java` with JSpecify `@NullMarked`, per
  ADR-0001. If a package exists in any target, its `package-info.java` is maintained; it is not
  guarded away.
- Package coverage is checked **per target**.
- Every target node inherits Java 25, `-Xlint:all -Werror`, Error Prone, NullAway (including
  `OnlyNullMarked` and `RequireExplicitNullMarking=ERROR`), JUnit 6, and the Jackson 3 dependency
  guard. Stonecutter must never be a way to escape static analysis.

### Aggregate tasks

Each controller exposes at least two aggregate tasks with fixed semantics:

- **`chiseledCheck`** — every target: compile + static analysis + unit tests;
- **`chiseledBuildAndCollect`** — every target: remap/assemble, artifacts collected into a stable
  directory.

A pull request must not rely on `./gradlew build` alone, because that may only represent the active
node.

### Pinned toolchain

- Gradle wrapper: **9.7.1**
- Stonecutter: **0.9.8**
- Architectury Loom: **1.17.493** (release; no `SNAPSHOT` on the release baseline)
- Java toolchain: **25** for all four targets (no preview features)
- Fabric loader / Fabric API: `0.19.5` / `0.116.17+1.21.1` and `0.155.3+26.1.2`
- NeoForge: `21.1.251` and `26.1.2.109`
- `loom-back-compat` **0.4.2** is **not** enabled by default. It is only added if the pinned
  Architectury Loom fails a target, and only after all four targets pass with it. It is build
  tooling and never a runtime dependency.

Tooling versions are pinned in the root version catalog and the wrapper. A change to any pinned
version is recorded by superseding this ADR.

### Pilot scope

The version axis is proven on **FerrumCore** first. The remaining modules adopt the same controller
convention only after the pilot passes on all four targets. Until then, no other module is migrated.

## Alternatives considered

- **Duplicate `mc-1.21.1/` and `mc-26.1.2/` source trees per module** — rejected: unbounded drift
  and duplicated review.
- **One Gradle build per Minecraft version with `common` source sets** — rejected: still duplicates
  source trees and loses a single source of truth.
- **Adopting the archived Architectury-specific Stonecutter template** — rejected: its plugin
  versions are stale; only the current multiloader template's target/tree concept is used.
- **Enabling `loom-back-compat` unconditionally** — rejected: it is added only on evidence.

## Performance evidence

Not applicable. This record fixes build/version orchestration and makes no performance claim.

## Correctness / compatibility impact

A single source tree removes drift between Minecraft generations while the canonical active target
keeps a deterministic committed state. The aggregate tasks ensure that "it compiled on my target" is
never mistaken for correctness on all four variants. The separation of Stonecutter (version axis)
from Architectury Loom (loader axis) keeps the FFM ABI and Rust kernel version-agnostic, as required
by ADR-0001.

## Rollback

- If the module-controller model does not work on the pinned Gradle/Stonecutter combination, use the
  included-build fallback described above.
- If the version axis must be abandoned entirely, that is a new, higher-numbered ADR. Duplicated
  `mc-*` source trees must not be restored silently.

## Follow-up work

- Implement the FerrumCore pilot: root registration, four targets, split Fabric/NeoForge build
  scripts, and the chiseled aggregate tasks.
- Verify the canonical target round trip leaves a clean worktree (`git diff --exit-code`).
- Decide, on evidence, whether `loom-back-compat` is required.
- After the pilot passes, extract the controller convention into `build-logic` and migrate the
  remaining Minecraft-facing modules in dependency order.
