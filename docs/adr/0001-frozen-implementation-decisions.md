# ADR-0001: Frozen implementation decisions

- Status: Accepted
- Date: 2026-09-23
- Affected modules: all

## Context

Ferrum must move batch, flat, vectorizable, or allocation-heavy work from the JVM into a Rust native
kernel reached through Java 25 FFM, while keeping vanilla Java behavior as the fallback. Before
scaffolding the repository, the implementation-phase rules need to be frozen so that later work
cannot silently fall back to earlier, softer wording. This record freezes the platform, tooling,
quality, architecture, and release decisions for the whole project. Technical corrections that
warrant independent review are split into ADR-0002 through ADR-0007.

## Decision

### Platform and runtime

- **FD-01 Java**: Java 25 is the baseline. Startup still checks the runtime feature version; below
  Java 22 the native path is disabled automatically.
- **FD-02 Native library**: one `libferrum` for the first phase. Its ABI is independent of the
  Minecraft version; features are enabled by symbol presence plus Java module switches.
- **FD-03 Java fallback**: the vanilla Java path is always retained. It is used when native loading
  fails, when the ABI mismatches, and when a runtime circuit breaker trips.
- **FD-04 Loaders**: Fabric and NeoForge. Platform code is a thin layer; core implementation must
  not depend on any loader.
- **FD-05 Game versions**: Minecraft 1.21.1 and 26.1.2. Each has its own Minecraft adapter and Mixin
  set.
- **FD-06 FFM binding**: `Linker`, `SymbolLookup`, and `MethodHandle` are bound once at startup. No
  symbol lookup is performed at runtime.
- **FD-07 FFI granularity**: only batch entry points are allowed. Thresholds are determined by JMH
  and are configurable.
- **FD-08 Native parallelism**: the first version does not start internal Rust thread pools, to
  avoid oversubscription with Minecraft/ForkJoinPool. Threads are revisited only as a separate,
  evidence-backed experiment.
- **FD-09 Configuration**: a single `config/ferrum.json` with per-module switches and thresholds. A
  bad configuration falls back to defaults and never leaves native initialization half-initialized.
- **FD-10 Observability**: startup prints native build, ABI, platform, and module status. Debug mode
  counts calls, fallbacks, and failure codes.
- **FD-11 Release policy**: in Alpha/Beta the native path can be disabled per module. Stable builds
  only enable functionality that has passed both the correctness and performance gates.
- **FD-12 Java namespace**: the Gradle `group` and all project-owned Java packages use the root
  `top.likoslupus.ferrum`. No second project root package is allowed.
- **FD-13 Nullability**: every project-owned package has a `package-info.java` marked with JSpecify
  `@NullMarked`; nullable positions use JSpecify `@Nullable`.
- **FD-14 Static analysis**: Error Prone + NullAway are compile blockers, with `OnlyNullMarked` and
  `RequireExplicitNullMarking=ERROR`.
- **FD-15 Data formats**: JSON/YAML/TOML and other structured data go through **Jackson 3** only.
  Jackson 2 databind/dataformat and other data-binding stacks are forbidden in business code.
- **FD-16 Tests**: Java tests use JUnit 6 / Jupiter. External Minecraft smoke/GameTest is an
  additional integration layer.
- **FD-17 Java style**: Java 25, no preview features by default, `-Xlint` + `-Werror`. Prefer
  records/sealed types/pattern matching, but avoid raw types, wildcard imports, and unexplained
  suppressions.
- **FD-18 Dual-loader build**: Fabric and NeoForge share the `common` layer. Architectury Loom is
  the preferred build chain; Architectury API is only introduced at suitable loader abstraction
  boundaries, never in FFM or algorithm hot paths.

### Architecture invariants

- **AI-01**: the Minecraft adapter may change; the native ABI stays as stable as possible. Version
  differences are absorbed in the Java adapter layer.
- **AI-02**: data ownership is explicit. Every pointer must answer who allocates it, who frees it,
  how long it lives, and whether it may cross threads.
- **AI-03**: errors may degrade, memory errors may not be tolerated. All length, multiplication, and
  offset arithmetic is checked.
- **AI-04**: every fast path must be provably equivalent. Each module ships a reference
  implementation used by differential tests.
- **AI-05**: nested parallelism is forbidden by default. When Minecraft already owns thread pools,
  the Rust kernel only performs data-local optimization/SIMD within a single call.

### Java engineering quality freeze

- **JQ-01 `@NullMarked` placement**: every project-owned package under `src/main/java`,
  `src/test/java`, and `src/testFixtures/java` gets a `package-info.java` in the standard form:

  ```java
  @NullMarked
  package top.likoslupus.ferrum.<...>;

  import org.jspecify.annotations.NullMarked;
  ```

  Sub-packages do not inherit the annotation; each is declared. CI scans for both "source exists but
  `package-info.java` is missing" and "`package-info.java` exists but lacks `@NullMarked`".
  Generated sources and Minecraft/Loader third-party sources are excluded.

- **JQ-02 NullAway / Error Prone**: configured once in a Gradle convention plugin and never
  downgraded per module. `NullAway=ERROR`, `NullAway:OnlyNullMarked=true`,
  `RequireExplicitNullMarking=ERROR`, fail-fast on compiler warnings, no global disabling of an
  Error Prone checker without an ADR. `@SuppressWarnings` is minimal-scope and names the specific
  checker; `@SuppressWarnings("all")` is forbidden.
- **JQ-03 Jackson 3 only**: `ferrum.json`, native manifests, benchmark/report JSON, and repro
  metadata, plus any future YAML/TOML config or tool files, go through a single project
  `DataFormats`/`ObjectMapper` facade. Jackson 3 uses the `tools.jackson.*` namespace; its
  transitive Jackson 2 `jackson-annotations` dependency is tolerated and does not permit Jackson 2
  databind.
- **JQ-04 modern Java is not "abstractions in hot paths"**: records are preferred for immutable
  DTOs, sealed interfaces for closed state sets, switch expressions / pattern matching to reduce
  branch boilerplate. FFM, noise, palette, and codec hot loops may and should use explicit `for`
  loops, primitive arrays, and preallocated buffers. Streams/Optional/boxing must not be forced into
  per-sample hot paths.

## Alternatives considered

- Leaving the rules only in early design material without a repo-owned record: rejected because the
  rules would not survive into a fresh checkout and would be easy to reinterpret.
- A single monolith document instead of records: rejected in favor of one record per decision so
  decisions can be superseded independently without rewriting history.

## Performance evidence

Not applicable. This record freezes rules; it makes no performance claim.

## Correctness / compatibility impact

These decisions preserve the vanilla Java path as the final fallback and keep the native ABI
independent of Minecraft, which is what allows a single native library to serve every Java release
variant. The Java quality freeze is a hard compile gate, so it prevents unsound nullability and
split data stacks from entering the codebase.

## Rollback

Individual rules are revised by adding a new, higher-numbered ADR and marking the affected rule
superseded. A project-wide rollback is not expected; the fallback path (`native.enabled=false`)
always restores pure Java behavior at runtime.

## Follow-up work

- Scaffold the repository layout and Gradle convention plugins that encode FD-12 through FD-18.
- Implement the platform/FFM/fallback foundation for FerrumCore.
- Add CI checks for package coverage, Error Prone/NullAway, and the Jackson 3 dependency guard.
