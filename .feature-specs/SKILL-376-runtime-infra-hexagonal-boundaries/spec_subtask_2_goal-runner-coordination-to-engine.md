# SKILL-376 Subtask 2 - Goal-runner coordination moves to the engine

Parent spec: [.feature-specs/SKILL-376-runtime-infra-hexagonal-boundaries/spec.md](spec.md)
Issue key: SKILL-376

## Scope

Resolve F-001 in [investigation.md](investigation.md).

Move `runtime-infra/sqlite/…/sqlite/goalrunner/{control,manifest,outcome}` (20
files, 3,877 lines) into `runtime-engine` under `skillbill.engine.goalrunner`,
placing each file in the existing noun-family package that fits the 12-file
ceiling. The moved classes keep implementing `GoalRunnerManifestStore`,
`GoalRunnerWorkflowOutcomeStore`, `GoalRunnerAttemptLedgerStore`, and
`GoalRunnerChildRepairStore`, and their consumers do not change.

What stays in SQLite (callers outside the package):

- `reviewPolicyFromLegacyArtifacts` and `outOfBandAcceptancesFromLegacyArtifacts`
  move next to their only caller,
  `core/migration/goal/LegacyGoalRunnerControlLedgerMigration.kt`.
- `clearRunnerInterruptedPauseState` stays beside its only caller,
  `workflow/goalrunner/runner/GoalRunnerControlStore.kt`, as an internal function.

Twins. 36 top-level functions in the moved files and their SQLite helpers share
a name with an engine or application function. 20 bodies are identical and 16
have diverged (investigation F-001). `GoalContinuationArtifactCodec.kt`,
`GoalParentProjectionWriter.kt`, and `GoalRepositoryIdentity.kt` already exist
in engine.

- For an identical twin, delete the SQLite copy and call the existing function:
  engine, application, or the domain one after SKILL-370 subtask 2 (for
  example `withParentStatus`).
- For a diverged twin, keep the SQLite body as a `private` function of the
  moved file under its current name. Do not reconcile it here. List every kept
  twin in the commit body for SKILL-372 subtask 2.
- If SKILL-372 subtask 2 has already moved a rule into domain, call it and
  delete both copies.
- If SKILL-371 subtask 3 has landed, SQLite `goalRepositoryIdentity` is already
  replaced by the repository identity port.
- Do not create a second file with the same name in one engine package. Merge
  identical declarations into the existing file; diverged ones stay private in
  the moved class's file.

What the moved code takes from SQLite today, and where it goes:

- The `sqlite.workflow.decomposition` lookups and the `sqlite.decomposition`
  helpers use no JDBC and no `UnitOfWork`. The twin rule above decides each
  function: an identical one is deleted in favor of its application copy, and a
  diverged one moves privately. The migration's single use keeps a SQLite-local
  path, or goes through the SKILL-372 domain owner.
- `decodePhaseRecords`, `decodePhaseLedger`, and `encodeWorkflowArtifact`
  already forward to domain `skillbill.workflow.taskruntime.artifact`
  (`phaseRecordsFromWorkflowArtifacts`, `phaseLedgerFromWorkflowArtifacts`), so
  call those directly.
- `decodeArtifacts` is deleted by SKILL-372 subtask 1; read `snapshot.artifacts`.
  If 372.1 has not landed, carry it as a `private` function in the moved file.
- `resolveDecompositionManifest` imports `java.nio.file.Path` and ports types,
  so it cannot go to domain. It is an identical twin; call application's copy.
- Nothing in this subtask moves into runtime-domain.
- `recordDegradedValue` and `InternalSqliteDiagnostics`: use the engine's
  injected `RuntimeDiagnostics`.
- `generateWorkflowId`: use `IdentifierGeneratorPort` or the domain rule
  SQLite already calls.
- Status literals in the moved files use `DecompositionStatus.wireValue`.

Delete `WorkflowGoalRunnerOutcomeStoreDependencies`. Each moved class declares its
collaborators as constructor parameters. If `WorkflowGoalRunnerOutcomeStore`
exceeds the detekt constructor limit, split it along the three ports it
implements. Do not add a bag.

Wiring: bind the ports to the moved `@Inject` classes in runtime-core. If
SKILL-373 subtask 1 has landed, use its accessor rule. Otherwise update the
existing `RuntimeGoalRunnerStoreProvides` constructors. If SKILL-378 subtask 3
has set `includeDefaultPublic = true` in `RuntimeEngineBoundaryArchitectureTest`,
keep the two classes runtime-core binds public and add them to
`PINNED_ENGINE_INBOUND_API_TYPES`. Everything else moved is `internal`.

Sequence numbers: if SKILL-378 subtask 3 has already moved progress, ledger, and
observability sequence allocation into the outcome write, the `max + 1` read
becomes a method on the SQLite repository that owns the stream. It stays SQL;
the engine code does not compute it.

Tests: move the SQLite tests that exercise the moved classes into
runtime-engine next to their subject. Use `:runtime-infra:sqlite` test fixtures
(`sqliteSessionFactoryForTests`, `establishTemporarySchemaReadiness`) where a
real database is needed. SQLite keeps tests for the functions that stay.

Update the `ARCHITECTURE.md` Gradle Modules entries for runtime-engine and
runtime-infra/sqlite, and the SQLite public-surface list.

## Acceptance Criteria

1. The package `skillbill.infrastructure.sqlite.goalrunner` does not exist in any
   source set.
2. No class under `runtime-infra/sqlite/src/main` takes `WorkflowGitOperations`,
   `FeatureTaskRuntimeWorkerSupervisor`, `GoalRunnerChildRepairRunnerPort`,
   `DecompositionManifestStore`, `DecompositionManifestProjectionWriter`, or
   `GoalChildPlanningHydratorPort` as a constructor parameter.
3. `WorkflowGoalRunnerOutcomeStoreDependencies` does not exist, and no moved class
   takes a parameter whose only role is to carry other dependencies.
4. The four goal-runner port interfaces keep their signatures, and no engine or
   application consumer of them changes.
5. `runtime-infra/sqlite` production code declares no public top-level function.
   Its public types are the session factory, the phase-settlement repository, and
   any experiment store that still exists.
6. The moved crash-reconcile, stale-block displacement, scoped-replan, lease
   acquire/heartbeat/release, and child-repair tests pass in runtime-engine.
7. No engine package contains two top-level functions with the same name and
   signature, and none of the 20 identical twins has a SQLite copy.

## Non-goals

- Changing goal-runner behavior, transaction scopes, statement order, lease
  fencing, or stored bytes. Each moved transaction keeps its `database.transaction`
  or `database.read` scope.
- Collapsing the goal-runner port interfaces or the child-repair and hydrator
  ports (SKILL-378 follow-up).
- Moving `sqlite.workflow.goalrunner` (it issues SQL and stays).
- Restructuring the engine's goal-runner classes (SKILL-378 subtask 3).

## Dependency notes

- After subtask 1, SKILL-370 (domain `withParentStatus`), and preferably SKILL-372
  subtask 1. SKILL-372 subtask 2 follows and reconciles the kept diverged twins.
- SKILL-373 subtask 1 and this subtask both edit `RuntimeGoalRunnerStoreProvides`.
  Whichever lands second applies the rule "each port bound once to an `@Inject`
  implementation, no bag" to the classes present.
- SKILL-378 subtask 3 includes the moved classes in its step-class rule if this
  lands first.

## Validation strategy

- Run the runtime-engine, infra sqlite, runtime-application, runtime-core,
  runtime-cli, and runtime-mcp suites plus the architecture suite. The
  module-edge guard must pass with no new production edge.
- Build the CLI and run `skill-bill goal preflight` on a fixture bundle to
  confirm DI constructs the moved stores.
- Changed tests go through `bill-unit-test-value-check`. The validate phase runs
  the routed pack quality gate.

## Next path

Subtask 3 (`spec_subtask_3_package-layout.md`).
