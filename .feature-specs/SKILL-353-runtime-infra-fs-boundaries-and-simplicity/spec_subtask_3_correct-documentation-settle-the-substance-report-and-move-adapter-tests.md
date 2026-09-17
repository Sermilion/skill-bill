# SKILL-353 Subtask 3 - Correct documentation, settle the substance report, and move adapter tests off the engine

Parent spec: [.feature-specs/SKILL-353-runtime-infra-fs-boundaries-and-simplicity/spec.md](./spec.md)
Issue key: SKILL-353

## Scope

Resolve F-010 and F-012 in [investigation.md](investigation.md). F-001, F-009, and F-011 are owned by SKILL-354.

Own the scaffold and infra-fs sections of `runtime-kotlin/ARCHITECTURE.md`, `scaffold/substance/**` and the `platformPackSubstanceReport` task in `runtime-kotlin/runtime-infra-fs/build.gradle.kts`, the test dependency and `friendPaths` lines of that build script, the two test files that import `skillbill.engine.featuretask` types and the four that import application types, their destinations in `runtime-engine` or `runtime-core`, the `install/support` package and the owners it dissolves into (`install/plan` for config paths, `install/apply` for cleanup and symlink replacement, and the owners of pointer rendering and legacy skill names), and `runtime-kotlin/agent/decisions.md`.

Rewrite the scaffold section to the current typed `ScaffoldGateway` under the 2026-09-03 decision, name the subtask that closed the raw-map migration, and inventory the adapter-internal raw-map functions under `scaffold/`. Make the substance report a `skill-bill` CLI command reaching the catalog through the existing gateway and remove its Gradle task, or delete `scaffold/substance/**` and the task; record the choice with its evidence. Dissolve `install/support` into its owners so that SKILL-354 moves files from named areas only. Remove the `runtime-engine` test dependency and the `friendPaths` entry, relocate the engine-dependent tests to the module whose behaviour they prove, and let application types reach this module's tests only through `runtime-ports` test fixtures or public application API. Leave the root package, the area source sets, and public visibility as found.

## Acceptance Criteria

1. `ARCHITECTURE.md` describes `ScaffoldGateway` as a typed port consumed by the CLI under the 2026-09-03 decision, names the subtask that closed the raw-map migration, and inventories the adapter-internal raw-map functions under `scaffold/`; `RuntimeArchitectureDocumentationTest` passes.
2. The substance report is either a `skill-bill` CLI command reaching the catalog through the existing gateway with its Gradle task removed, or `scaffold/substance/**` and the task are deleted; the choice and its evidence are recorded in `runtime-kotlin/agent/decisions.md`.
3. `install/support` no longer exists; its eight files live with the owners named in the scope; every import of `skillbill.infrastructure.fs.install.support` is rewritten and the module compiles.
4. `build.gradle.kts` declares no `runtime-engine` test dependency and no `friendPaths` entry; tests that drove engine model types live in `runtime-engine` or `runtime-core`; application types reach this module's tests only through `runtime-ports` test fixtures or public application API; the module suite and `repoTest` pass.
5. The root package, the ten area source sets, `verifyInfraFsAreaCompile`, and the public visibility of every name outside `install/support` are unchanged from the branch head after subtask 2.
6. No new suppression, baseline row, or exemption. Files stay under 1,200 lines and 40 functions without count splits.

## Non-goals

No behaviour change to any adapter. No root clustering, layer-proof move, source-set deletion, or visibility narrowing: SKILL-354 owns those. No change to the validators or to process, vocabulary, environment, or primitive ownership. No rewrite of the test suite beyond relocation and the assertions relocation requires.

## Dependency notes

Depends on: subtasks 1 and 2. Both edit files in `install/support` and tests this subtask relocates, so it starts from the branch head after they land.

## Validation strategy

Name the regression before each test: a documentation claim the scaffold code no longer supports, a substance report deleted while a caller remains, an `install/support` function moved to a package its callers cannot import without an upward edge, an engine test silently dropped, an application internal still reached through `friendPaths`. Rely on compilation of all modules, `RuntimeArchitectureDocumentationTest`, `ImplementationOwnershipArchitectureTest`, and `./gradlew check` on runtime-kotlin including `repoTest`, plus the governed quality gate. Apply bill-unit-test-value-check to relocated tests.

## Next path

This is the final subtask of SKILL-353. SKILL-354 (module split) runs after this goal lands.

## Spec Path

.feature-specs/SKILL-353-runtime-infra-fs-boundaries-and-simplicity/spec_subtask_3_correct-documentation-settle-the-substance-report-and-move-adapter-tests.md
