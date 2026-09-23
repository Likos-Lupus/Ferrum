# ADR-0010: Stonecutter module controller and the 26.x toolchain

- Status: Accepted
- Date: 2026-09-23
- Affected modules: build chain; `ferrum-core` (pilot); later all Minecraft-facing modules

## Context

ADR-0009 made Stonecutter the Minecraft version axis and preferred a **module controller** per
independently shipped module, with an included-build fallback if unsupported. The FerrumCore pilot
validated the toolchain on Gradle 9.7.1 with Stonecutter 0.9.8 and Architectury Loom 1.17.493. Three
concrete incompatibilities surfaced and were resolved; this record fixes the working configuration
so later modules copy it rather than rediscovering it.

## Decision

### Controller reference must be a path string

A subproject controller is registered with a **path string**:

```kotlin
stonecutter {
    create(":modules:ferrum-core") {
        version("1.21.1-fabric", "1.21.1").buildscript("build.fabric.gradle.kts")
        // ...
        vcsVersion = "26.1.2-fabric"
    }
}
```

Passing a `Project`/`ProjectDescriptor` via `project(":modules:…")` is **forbidden**: in Stonecutter
0.9.8 `ProjectDescriptor.resolve` maps a descriptor to the *receiver's* path, so it silently
registers the root project as the controller (generating root `stonecutter.gradle.kts` and root
`versions/`) and fails with "Project : is not registered". A `CharSequence` path is the supported
reference form. The included-build fallback from ADR-0009 is therefore **not** required.

### 26.x is unobfuscated: Loom variant selection

Minecraft 26.1.2 publishes no `client_mappings`, and no Yarn builds exist for it; the 26.x line is
unobfuscated. Architectury Loom must therefore run its **no-remap** variant for 26.x and its
**remap**
variant for 1.21.1. This is selected with `dev.kikugie.loom-back-compat` **0.4.2**, configured (in
`stonecutter.properties.toml`) to use the Architectury Loom variants:

```toml
loomx.loom_version = "1.17.493"
loomx.loom_unobf_plugin = "dev.architectury.loom-no-remap"
loomx.loom_remap_plugin = "dev.architectury.loom-remap"
```

The four targets compile/build with `loom-back-compat` enabled, so it is frozen into the build. It
is build tooling only and is never a runtime dependency.

### Loader platform is a per-node project property

Architectury Loom chooses its platform from the Gradle property `loom.platform` and resolves it
during plugin application. Setting it from the loader build script body is too late (the value is
cached as
`FABRIC`). It is set from the settings `gradle.beforeProject` hook, derived from the target's loader
suffix (`…-fabric` → `fabric`, `…-neoforge` → `neoforge`).

### Architectury Loom serves both loaders

- **Fabric**: `minecraft(...)` + `loomx.applyMojangMappings()` (a no-op on 26.x, official mappings
  on 1.21.1) + `modImplementation(fabric-loader)`.
- **NeoForge**: with `loom.platform=neoforge`, Loom provides the `neoForge` configuration. The mod
  declares `neoForge("net.neoforged:neoforge:<version>")` and adds the NeoForged Maven repository.
  NeoForm mappings for 26.x arrive through that dependency. No ModDevGradle is used.

### Aggregate tasks

Stonecutter 0.9.8 removed the `registerChiseled` DSL. The aggregate tasks are registered on the
controller with the current API:

```kotlin
tasks.register("chiseledCheck") { dependsOn(stonecutter.tasks.named("check").map { it.values }) }
tasks.register("chiseledBuildAndCollect") {
    dependsOn(
        stonecutter.tasks.named("buildAndCollect")
                .map { it.values })
}
```

`chiseledCheck` runs compile, static analysis, and unit tests on every target;
`chiseledBuildAndCollect`
remaps/assembles and collects every target into `build/libs/<target>/`.

### Canonical target

The canonical active target remains `26.1.2-fabric`. Switching away and back restores the controller
byte-for-byte; the switch tasks (`Set active project to <target>`, `Reset active project`) are the
only supported way to change it.

## Alternatives considered

- **Passing a `Project`/`ProjectDescriptor` to `create`** — rejected: registers the root as
  controller in 0.9.8.
- **Included-build fallback** — not needed once the path-string reference was found.
- **`net.neoforged.moddev` for NeoForge** — rejected: the frozen build mandate is Architectury Loom
  for both loaders.
- **Duplicating `mc-*` source trees** — rejected (ADR-0009).
- **Relying on `officialMojangMappings()` for 26.x** — impossible; the mappings do not exist.

## Performance evidence

Not applicable. This record fixes build orchestration and the loader toolchain.

## Correctness / compatibility impact

One source tree per module serves all four targets; the same `src/` compiles for Fabric and NeoForge
and for both Minecraft generations via the no-remap/remap variants. Aggregate tasks prevent "it
built on my target" from being mistaken for all-target correctness. The successful pilot
demonstrates all four targets compile, statically analyze, test, remap, and collect.

## Rollback

If a future Stonecutter or Loom release changes these behaviours, supersede this record. Do not
restore duplicated source trees.

## Follow-up work

- Extract the controller + loader conventions into `build-logic` before migrating the other modules.
- Add Mixin-applied assertions and client/server smoke to the pilot gate.
- Verify NeoForge metadata completeness (`neoforge.mods.toml` dependencies) when the first real mod
  content lands.
