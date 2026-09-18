# SKILL-356 Subtask 3 - Ledger-owned schema evolution

Parent spec: [.feature-specs/SKILL-356-runtime-infra-sqlite-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-356

## Scope

Resolve F-002 and F-011 in [investigation.md](investigation.md).

Own `runtime-kotlin/runtime-infra-sqlite/src/main/kotlin/skillbill/infrastructure/sqlite/core/` (`DatabaseRuntime.kt`, `DatabaseWriteReadinessGate.kt`, `DatabaseIdentity.kt`, `DatabaseMigrations.kt`, `MigrationLedger.kt`, `DatabaseMigrationSteps.kt`, `DatabaseMigrationEntriesEarly.kt`, `DatabaseMigrationEntriesLate.kt`, `DatabaseSchemaStatementsEarly.kt`, `DatabaseSchemaStatementsLate.kt`, `DatabaseColumnMigrations.kt` and its `Ensure`, `EnsureFeatureTask`, `Conditional`, `WorkList`, `WorkListRecovery` siblings, `DiagnosticEvidenceRepairTurnMigration*.kt`), `goalrunner/LegacyGoalRunnerControlMigration.kt`, `goalrunner/GoalContinuationArtifactCodec.featureTaskRecordForLegacyControls`, the eleven call sites of `migrateLegacyGoalRunnerControls` and the three legacy-artifact read fallbacks (in the manifest store after subtask 2, `WorkflowGoalRunnerChildWorkflowPersistence`, `WorkflowGoalRunnerManifestProjectionPersistence`, `GoalRunnerControlCoordinator*`, `WorkflowGoalRunnerManifestLoader`), `review/ReviewHealthPayloadLoading.kt` and `review/ReviewHealthPayloadMaterialization.kt` for the legacy telemetry restamp, `SQLiteRepositories.SQLiteTelemetryReconciliationRepository`, `DatabaseMigrationsTest`, `DatabaseMigrationsTestSupport`, `DatabaseWriteReadinessTest`, `DatabaseWriteMaintenanceRegressionTest`, `GoalRunnerControlStoreTest`, `GoalModeAttributionTest`, `../../../docs/observability-policy.md` if the migration record shape needs a line, `../../../runtime-kotlin/ARCHITECTURE.md` (Database Readiness And Routine Work, boundary rule 14, the SKILL-239 enforcement row), and `runtime-kotlin/agent/decisions.md`.

Make `DatabaseMigrations.apply` stamp `PRAGMA user_version` with the highest applied version inside the same transaction that records the ledger rows. Register the current unconditional column-ensure, diagnostic-evidence heal, and work-list heal work as the final ledger migration under a new immutable name and delete the unconditional calls from `establishSchemaReadiness`. Add two named ledger migrations: one walks every decomposed goal parent row and moves legacy review-policy and out-of-band-acceptance artifacts into `goal_runner_controls`, emitting a `record_kind: migration` diagnostics record per migrated row; one performs the legacy telemetry restamp and regenerated-row persistence once. Delete `migrateLegacyGoalRunnerControls`, its eleven call sites, the three read fallbacks, `restampUnsyncedLegacyTelemetry`, `persistLegacyTelemetryRewrites`, and `persistLegacyReviewFinishedRow`. Make `readUserVersion` raise `DatabaseAccessError(READ)`. Merge the two Early/Late pairs into one file each, grouped by area.

## Acceptance Criteria

1. After `DatabaseMigrations.apply` records pending migrations, `PRAGMA user_version` equals the highest applied migration version; `DatabaseIdentity.read` reports that value; a test proves that appending a migration changes `DatabaseIdentity.userVersion` and that `DatabaseWriteReadinessGate.ensureReady` re-establishes once on that change and not again.
2. `DatabaseRuntime.establishSchemaReadiness` calls `DatabaseSchema.createBaseSchema` and `DatabaseMigrations.apply` and nothing else; the column-ensure, evidence-key heal, and work-list heal work is one ledger migration with a new immutable name; a test proves that a second `ensureReady` on a warm database and a fresh process opening an up-to-date database run zero `pragma_table_info` queries, observed through a counting `Connection` wrapper in the fixture, not through timing.
3. `DatabaseIdentity.readUserVersion` raises `DatabaseAccessError(READ)` on any `SQLException`; the `runCatching...getOrNull()` shape is gone; a test proves an unreadable database file surfaces the typed error from `ensureReady` instead of re-establishing schema.
4. `migrateLegacyGoalRunnerControls`, `LegacyGoalRunnerControlMigration.kt`, `featureTaskRecordForLegacyControls`, the eleven call sites, and the three legacy-artifact read fallbacks are deleted; one named ledger migration moves review-policy and out-of-band-acceptance artifacts from `feature_task_workflows` parent rows into `goal_runner_controls` and emits one `record_kind: migration` record per migrated row naming the parent workflow id and the artifact keys moved; a legacy fixture built by `DatabaseMigrationsTestSupport` with both artifacts on a parent row reads the same policy and acceptances through `GoalRunnerControlRepository` after one establishment, and a second establishment migrates nothing.
5. `restampUnsyncedLegacyTelemetry`, `persistLegacyTelemetryRewrites`, and `persistLegacyReviewFinishedRow` are deleted; one named ledger migration performs that work once; `SQLiteTelemetryReconciliationRepository.reconcileStaleSessions` calls only `reconcileStaleTelemetrySessions`; `TelemetryOutboxStaleSettlementTest` and `ReviewHealthDeliveryGrainTest` pass with unchanged assertions and a legacy outbox fixture is restamped exactly once.
6. `DatabaseMigrationEntriesEarly.kt` and `DatabaseMigrationEntriesLate.kt` are one file; `DatabaseSchemaStatementsEarly.kt` and `DatabaseSchemaStatementsLate.kt` are one file; each is under 1,200 lines and grouped by area; every migration name already in the ledger is unchanged; `DatabaseMigrations.requireDeterministicMigrations` still passes.
7. All 51 `DatabaseMigrationsTest` cases pass with unchanged assertions plus the new cases above; `DatabaseSchemaTest` passes; `ARCHITECTURE.md` describes readiness as keyed by a ledger-stamped `user_version` plus file identity, lists the two passes `establishSchemaReadiness` runs, and rule 14 names the one-time legacy migrations; `decisions.md` records the `user_version` stamp and the retirement of migrate-on-access.

## Non-goals

No change to the base DDL text, WAL and pragma setup, connection-per-operation design, the one-identity-read-per-`ensureReady` behaviour, `MigrationLedger.ensureNameKeyed`, or any existing migration name or body. No deletion of legacy fixture tests. No change to the `runtime-application` near-duplicate of the legacy control migration.

## Dependency notes

Depends on: subtask 1. It reshapes `DatabaseRuntime`, the readiness gate's diagnostics, and `readUserVersion`'s error channel, and provides the recording diagnostics fixture these migrations' records are asserted through. Independent of subtask 2 except for the file that hosts the manifest review commands; rebase on the branch head and re-census the `migrateLegacyGoalRunnerControls` call sites before editing.

## Validation strategy

Name the regression before each test: a new migration landing without changing the readiness key, a warm database re-probing 151 columns on every write, an unreadable `user_version` silently forcing full re-establishment, a goal parent whose legacy review policy stops being honoured, a legacy telemetry row restamped on every reconciliation or never, two migrations sharing a name. Build each legacy shape with the existing fixture builders, open it through the real readiness path, assert through repositories, and assert the migration records through the recording diagnostics port. Run the module suite, `runtime-engine` goal-runner suites, `runtime-core` guards, and `./gradlew check` on runtime-kotlin; run the pack-declared quality gate and bill-unit-test-value-check for changed tests.

## Next path

Goal complete after this subtask and subtask 2 settle; the runtime finalises history and decisions entries.

## Spec Path

.feature-specs/SKILL-356-runtime-infra-sqlite-boundaries-and-simplicity/spec_subtask_3_ledger-owned-schema-evolution.md
