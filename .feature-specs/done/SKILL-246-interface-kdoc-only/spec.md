# SKILL-246: Interface KDoc only

## Mode

single_spec

## Intended Outcome

Kotlin sources under `runtime-kotlin`, `intellij-plugin`, and
`../../../runtime-kotlin/build-logic` contain no line comments and no non-KDoc block
comments. The only remaining documentation comments are KDoc on interfaces and
their members. An architecture test keeps that rule from regressing.

## Overview

The tree currently mixes `//` line comments, `/* */` blocks, and KDoc on
classes, functions, and tests. AGENTS.md already forbids new comments; this
work removes the existing ones and leaves KDoc only where it documents an
interface contract.

## Acceptance Criteria

1. Authored `.kt` and `.kts` sources under `runtime-kotlin`, `intellij-plugin`,
   and `../../../runtime-kotlin/build-logic` contain no `//` line comments and no
   `/* */` block comments, including trailing end-of-line comments.
2. Remaining `/** */` KDoc is attached only to `interface` declarations and
   members declared on those interfaces (functions, properties, and nested
   types that belong to the interface).
3. KDoc on classes, objects, enums, annotation classes, files, functions, and
   properties that are not interface members is gone.
4. An architecture test fails the build when a scanned Kotlin file violates
   that rule, covering the same roots as the inline-FQN scan.
5. Architecture-test fixtures that previously embedded live comment syntax to
   prove scanners ignore comments are rewritten so the ban can be total, or a
   named allow-list records each remaining fixture file with a one-line why.
6. Generated sources and build output are not scanned or rewritten.
7. AGENTS.md, CLAUDE.md, `../../../docs/code-principles.md`, and the shared shell
   ceremony comment section state the same rule: no line or block comments;
   KDoc only on interfaces and their members.

## Constraints

- Acceptance criteria must be checkable from the current tree, tests, and
  policy docs during implement and audit. Do not require a later phase's
  receipt or gate (validate quality-check evidence, review clearance, commit
  sha, or PR URL) as a criterion. Those belong in Validation Strategy or in
  that later phase.
- Do not change runtime behavior, control flow, names, or types except where
  a comment was the only carrier of a why that must move into a name or test.
- Keep KDoc on interface members; do not strip an interface down to a bare
  type when that KDoc documents a non-obvious public contract.
- Do not keep why-not-what line comments as an exception. If a why cannot be
  expressed in a name or test, record it in `../../../agent/decisions.md` for that
  area, not beside the code.
- Skip generated sources, Gradle build directories, and checked-in fixtures
  that are not Kotlin.
- Rewrite rather than exempt architecture tests that currently use comments as
  live syntax unless the compiler or scanner contract physically requires it.

## Non-Goals

- Removing or rewriting comments in Markdown, YAML contracts, JSON, shell, or
  TypeScript.
- Adding KDoc to interfaces that currently have none.
- Enabling Detekt `UndocumentedPublicClass` / `UndocumentedPublicFunction`.
- Reformatting or splitting files except as required to remove comments
  without breaking compilation.

## Affected Areas

- `../../../runtime-kotlin` authored Kotlin sources and architecture tests
- `../../../intellij-plugin` authored Kotlin sources
- `../../../runtime-kotlin/build-logic` authored Kotlin sources
- `../../../AGENTS.md`, `../../../CLAUDE.md`, `../../../docs/code-principles.md`,
  and the shared shell-ceremony comment section
- `../../../runtime-kotlin/runtime-core/src/test/kotlin/skillbill/architecture/PrincipleEnforcementInventory.kt`

## Validation Strategy

- Architecture test covering the comment ban on the inline-FQN scan roots.
- Compile of affected modules after the sweep.
- Validate-phase dominant-stack Kotlin quality check on touched modules. That
  gate is not an implement or audit acceptance criterion.

## Delivery Plan

1. Add the architecture test and inventory the current violations.
2. Strip line and block comments and non-interface KDoc in the same pass.
3. Align the authored comment-policy docs with the enforced rule.
