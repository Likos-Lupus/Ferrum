# ADR-0011: Error Prone and NullAway pins

- Status: Accepted
- Date: 2026-09-23
- Affected modules: build chain; all Java modules

## Context

ADR-0001 (JQ-02) requires Error Prone + NullAway as compile blockers, with `NullAway=ERROR`,
`NullAway:OnlyNullMarked=true`, and explicit null-marking enforced at error level. Bringing the
configured toolchain up on Java 25 exposed two facts that the rule text could not anticipate:

- Error Prone **2.50.0** removes `com.google.errorprone.predicates.type.DescendantOf`, which
  NullAway **0.12.7** references, so NullAway fails to initialize (`NoClassDefFoundError`). Error
  Prone **2.48.0** is the newest release that keeps the class.
- Error Prone removed the `RequireExplicitNullMarking` checker; the current equivalent is
  `AddNullMarkedToPackageInfo` (package-level). `AddNullMarkedToClass` was rejected because it
  demands a class-level annotation, contradicting the package-level `@NullMarked` model frozen by
  ADR-0001.

## Decision

- Pin `com.google.errorprone:error_prone_core` to **2.48.0** (newest compatible with NullAway
  0.12.7)
  and `com.uber.nullaway:nullaway` to **0.12.7**.
- Enforce `NullAway` at error level with `-XepOpt:NullAway:OnlyNullMarked=true`, plus the
  `AddNullMarkedToPackageInfo` checker at error level.
- Enforce package-level `@NullMarked` coverage, including missing `package-info.java`, with the
  dedicated `ferrumPackageCoverage` verification task, which runs in `check` for every project and
  every target node.

Together these preserve the intent of JQ-02: a missing explicit null-marking is a build failure, and
NullAway only analyzes `@NullMarked` code.

## Alternatives considered

- **Keep Error Prone 2.50.0** — impossible with NullAway 0.12.7.
- **Enable `AddNullMarkedToClass`** — rejected: forces class-level annotations and is incompatible
  with the package-level model.
- **Drop the explicit-null-marking check** — rejected: the package coverage task replaces the
  removed checker instead.

## Performance evidence

Not applicable. Build-time analysis only.

## Correctness / compatibility impact

The compile gate remains a hard blocker for nullability and explicit null-marking. The exact Error
Prone version is a build-tooling pin; changing it (e.g., once NullAway supports Error Prone 2.49+)
is recorded by superseding this ADR.

## Rollback

Supersede this record when NullAway supports a newer Error Prone and the checkers exist again.

## Follow-up work

- Re-evaluate the Error Prone pin when NullAway ships support for Error Prone 2.49+.
