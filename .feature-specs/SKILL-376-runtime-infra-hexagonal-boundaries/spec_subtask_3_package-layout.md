# SKILL-376 Subtask 3 - Package layout and a live package-cycle guard

Parent spec: [.feature-specs/SKILL-376-runtime-infra-hexagonal-boundaries/spec.md](spec.md)
Issue key: SKILL-376

## Scope

Resolve F-003 and F-009 in [investigation.md](investigation.md). Apart from the
guard's prefix line, this subtask only moves files: package declarations,
imports, and paths change, and no declaration changes.

Guard (F-003):

- In `PrincipleEnforcementInventory.packagePrefixForModule`, return
  `"skillbill.infrastructure.<module>."` for each `runtime-infra:<module>` (the
  `else` branch at L53 covers them today). If SKILL-373 has moved the suite,
  make the same change where the prefix lives.
- Close the SQLite cycles with moves:
  - `DatabaseMigrationEntries`' five area migrations (`GoalTelemetryMigration`,
    `TelemetryOutboxDeliveryIdentityMigration`, `TelemetryOutboxLastErrorMigration`,
    `FeedbackEventMigration`, `FeatureTaskPhaseSettlementsMigration`) and
    `migrateLegacyTelemetryOutboxLedger` move into `sqlite.core.migration`.
  - `StaleSessionReconciler` and `StaleReconciliationCandidateQuery` move to
    `sqlite.telemetry`.
  - `FEATURE_VERIFY_WORKFLOW_CONTRACT_VERSION` moves to `sqlite.core.schema`.

  Subtask 2 has already removed the goal-runner cycles.
- Break two skills cycles with moves:
  - `FileSystemScaffoldGateway.kt`, `FileSystemScaffoldInstallLink.kt`, and
    `FileSystemScaffoldOrchestrator.kt` move from `skills.scaffold` to
    `skills.install.scaffold`. This removes every scaffold → install import.
    If SKILL-377 subtask 3 has already deleted the dead
    `FileSystemScaffoldInstallLink`, move only the other two.
  - `InstallNativeAgentPlatformPackLoader.kt` moves from
    `skills.install.nativeagent.install.native` to
    `skills.nativeagent.platformpack`. This removes the only nativeagent →
    install import.
- Record `nativeagent ↔ scaffold` as the one row in the skills package-cycle
  baseline.

Packages (F-009):

- Collapse to one package:
  - `contracts.workflow.featuretask.**` → `contracts.workflow.featuretask`
  - `contracts.workflow.goal.**` → `contracts.workflow.goal`
  - `contracts.workflow.workflow` → `contracts.workflow`
  - `workflow.review.specialists.**` → `workflow.review.specialists`
  - `sqlite.review.stage.**` → `sqlite.review.stage`
  - `sqlite.review.stats.**` → `sqlite.review.stats`
  - `sqlite.workflow.workflow` → `sqlite.workflow`
  - `sqlite.decomposition` into `sqlite.workflow.decomposition`
  - `sqlite.featuretask.artifact` into `sqlite.workflow.featuretask`, or a child
    package if that would exceed the ceiling
- Collapse to two packages:
  - `sqlite.telemetry.lifecycle.telemetry.**` → `sqlite.telemetry.lifecycle` +
    `sqlite.telemetry.lifecycle.session` (the four `*SessionAdapter` files and
    `LifecycleTelemetryMeasurementAdapter`)
  - `sqlite.core.migration.**` (including the moved migrations) →
    `sqlite.core.migration` + one noun-family child per 12 files (for example
    `column` for the `DatabaseColumnMigrations*` files)
  - `skills.install.staging.staging.**` → `skills.install.staging` +
    `skills.install.staging.content`
  - `skills.install.nativeagent.install.**` and `skills.install.nativeagent` →
    `skills.install.nativeagent` + `skills.install.nativeagent.link`
- Tests:
  - Each test file in an orphan package (83 files in 38 packages; the list is
    in the investigation) moves to the production package of the class it
    exercises.
  - Tests for moved production files move with them.
- Update the inventories and baselines that name a moved package or path, such
  as package-cycle baselines, `parseBoundarySites`, `WireVocabularyGovernedSeamInventory`
  path markers, and ambient baselines, plus the `ARCHITECTURE.md` package
  references, wherever they live when this runs.

## Acceptance Criteria

1. The infra package-cycle tests use prefix `skillbill.infrastructure.<module>.`.
   The SQLite baseline is empty and the test passes. The skills baseline holds
   exactly one row, `nativeagent ↔ scaffold`. The test detects that cycle, which
   proves the scan now sees infra edges, and no `skills.scaffold` file imports
   `skills.install`.
2. No production or test package under `runtime-infra` has two adjacent equal path
   segments, and every infra `src/test` package also exists as a production
   package in the same module. Fixture packages `testsupport`/`testing` and
   `src/repoTest` suites, which check repository content, are exempt.
3. Each subtree listed in scope uses the fewest noun-family packages that keep
   every package at 12 Kotlin files or fewer, measured on the tree at start. The
   counts in scope are the baseline targets; `skillbill.infrastructure.contracts.schema`
   (added by SKILL-374) is outside the list.
4. `skillbill.infrastructure.sqlite.decomposition`,
   `skillbill.infrastructure.sqlite.featuretask`, and
   `skillbill.infrastructure.sqlite.workflow.workflow` do not exist.
5. The touched modules run the same number of test cases as before the moves, and
   all pass.

## Non-goals

- Renaming classes, files, or functions, or changing visibility.
- Promoting `skills.scaffold.platformpack`, or breaking `nativeagent ↔ scaffold`.
- Adding a guard for orphan test packages or stutter paths.
- Packages outside the listed subtrees and the orphan test files.

## Dependency notes

- After subtask 2, so the goal-runner edges are gone and this commit rebases on
  both earlier commits.
- Before SKILL-374 subtask 2, which lands after this subtask and uses the
  collapsed infra/contracts paths. It adds `skillbill.infrastructure.contracts.schema`,
  which is outside this subtask's collapse list. SKILL-374 subtask 1 edits the
  cycle test only for the runtime-contracts case.
- SKILL-372 subtask 3 edits SQLite `SQLiteRepositories.kt`, `TelemetryOutboxStore`,
  `ReviewTelemetryState.kt`, and `LifecycleTelemetryEmit.kt` (runtime version
  injection). The last two move here, so the later commit rebases on the paths
  present.
- Before SKILL-373 subtasks 2 and 3, which rewrite and move the suite this
  subtask edits.
- SKILL-372 subtask 3 also edits `ArchitectureScanSupport.packageImportEdges`,
  for domain granularity only. The second to land rebases, and infra keeps area
  granularity with the corrected prefix.
- SKILL-373 may have moved the architecture suite and collapsed per-module
  baselines into one file per rule. Apply the prefix change and the cycle row
  there.

## Validation strategy

- `git diff -M50% --stat` for the commit shows renames with package and import
  edits, plus the prefix line and baseline row.
- Run the infra contracts, skills, workflow, and sqlite suites, every consumer
  whose imports change, and the architecture suite.
- The validate phase runs the routed pack quality gate.

## Next path

Goal complete. During finalization, record the package layout rule and the infra
cycle prefix in `runtime-kotlin/agent/decisions.md`.
