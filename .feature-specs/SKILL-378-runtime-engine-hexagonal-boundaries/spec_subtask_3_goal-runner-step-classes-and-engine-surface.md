# SKILL-378 Subtask 3 - Goal-runner step classes, sequences, reads, and engine surface

Parent spec: [spec.md](spec.md)
Issue key: SKILL-378

## Scope

Resolves investigation F-002 for `skillbill.engine.goalrunner`, plus F-003,
F-005, F-006, F-007, and F-009, and the raw-map part of F-008.

**Step classes.** Apply subtask 2's rule to `goalrunner/execution`, `planning`,
`launch`, `repair`, `status`, and `preflight`. Delete `GoalRunnerSharedArgs.kt`.
Collaborators such as `GoalRunnerObservabilityEmitter` and `GoalRunnerLedgerRecorder`
move into the constructors of the classes that use them. Replace the 32
`DefaultGoalPlanningSweep.*` extensions and their `Produce*Args`/`GoalPlanning*Context`
bags with members or planning classes that own their collaborators, and make the
sweep's `manifestFileStore` private. Make the constructor properties of
`GoalRunnerStatusProjectionAssembler`, `GoalRunnerStatusProjectionDataSources`,
and other goal-runner `@Inject` classes private. `GoalRunnerStatusService`,
`GoalPreflightService`, `GoalPlanningPreparationCheckpoint`,
`GoalChildPlanningHydratorPortAdapter`, and `GoalRunnerChildRepairOperations` take
their collaborators as constructor parameters where kotlin-inject can resolve
them. Extend the subtask 2 step-class rule and the engine inject-property rule
to `skillbill.engine.goalrunner`, including the classes SKILL-376 subtask 2 moved
into it.

**Sequences.** Each stream's SQLite repository (progress, ledger, observability)
gets one method that inserts the row and assigns its sequence as the stream's
max + 1 in the same statement or transaction. The engine write calls that method
and does not compute max + 1 itself. Callers stop supplying sequence numbers. Delete the counters in `GoalRunnerProgressEventEmitter`,
`GoalRunnerObservabilityEmitter`, and `GoalRunnerLedgerRecorder`, and the
per-workflow map in `DurableGoalPlanningAttemptRecorder`. Apply this wherever the
outcome store implementation lives, in runtime-infra/sqlite or in the engine if
the store has already moved. Do not wait for that move. Row shape and
column meaning stay the same.

**Read query.** Add a concrete read-only query over the existing recorder parts
exposing `existingWorkflowMode`, `workerOwnership`, `loadPhaseLedger`, and
`loadPhaseRecords`. The four goal-runner readers depend on it, and
`FeatureTaskRuntimePhaseRecorder` delegates those reads to it.

**Silent reads.** The four reads in investigation F-006 fail with their
family's typed error or emit one bounded record (seam, expected, used) through
`RuntimeDiagnosticsBestEffortWarning`. The caller's projection reflects the
degraded read, and interrupts are rethrown.

**Aliases.** Delete `goalrunner/model/GoalRunnerPersistenceModelAliases.kt` (17
ports targets; SKILL-372 subtask 3 deletes the domain-targeted `GoalContinuation`
alias), the 19 aliases in `work/model/IdeStatusModels.kt`, and the intra-engine
alias in `goalrunner/persist/DurableChildRecoveryClass.kt`. Re-census the targets
first, because SKILL-377 subtask 3 renames the ports `*Model` types behind its own
aliases. Import the owners in the engine,
CLI, MCP, core, and tests. Update the pinned inbound API list to engine-declared
types only.

**Visibility.** Make every public engine type `internal` when no other module
imports it and runtime-core's generated component does not need it. Compilation
decides. Classes SKILL-376 subtask 2 moved in are already `internal`, except
`WorkflowGoalRunnerManifestStore` and `WorkflowGoalRunnerOutcomeStore`, which
runtime-core binds. Add those two to the pinned engine types. Then set
`includeDefaultPublic = true` in `RuntimeEngineBoundaryArchitectureTest`.

**Raw maps.** Make each remaining public engine declaration with
`Map<String, Any?>` in its signature internal or typed, and add runtime-engine main
to the path filter of `RuntimeRawMapArchitectureTest`.

## Acceptance Criteria

1. `GoalRunnerSharedArgs.kt` does not exist. No file other than
   `DefaultGoalPlanningSweep`'s own declares a `DefaultGoalPlanningSweep.` extension.
2. The step-class rule scans `skillbill.engine.goalrunner` and fails on a
   synthetic `FooArgs(val ledger: GoalRunnerLedgerRecorder)` there. The
   inject-property rule covers all of runtime-engine with an empty baseline and
   fails on a synthetic goal-runner `@Inject` class with a public collaborator
   property.
3. A test that records planning attempts and progress events for one workflow
   in one run, through the production recorders, reads back distinct, strictly
   increasing progress sequence numbers. The same holds across two recorder
   instances.
4. No engine class holds a sequence counter or a per-workflow sequence map.
5. The four named goal-runner readers do not reference `FeatureTaskRuntimePhaseRecorder`,
   and status projection output for the existing fixtures is unchanged.
6. Each silent read in F-006 has a test feeding malformed durable input that
   asserts the typed error, or the record fields together with the degraded
   projection value.
7. runtime-engine main declares no typealias, and the pinned inbound API lists
   only engine-declared types. The inbound API
   test passes.
8. The engine visibility guard counts default-public declarations and passes.
9. The raw-map guard reads runtime-engine main files and passes. A synthetic
   public engine function returning `Map<String, Any?>` fails it.
10. Existing goal-runner, planning, status, repair, preflight, and CLI goal
    suites pass, with test changes limited to construction, imports, visibility,
    and the new tests.

## Non-goals

- Collapsing `GoalRunnerChildRepairRunnerPort`, `GoalRunnerChildRepairStore`, or
  `GoalChildPlanningHydratorPort` (SKILL-376 follow-up).
- Deleting `fun interface` seams with test substitutes.
- Changing status output, control verbs, or planning semantics.
- Typealiases in other modules.

## Dependency notes

Depends on subtask 2. It does not wait for another issue. Restructure the goal-runner classes where they live, including coordination classes that are still in sqlite only when this subtask's criteria require editing them in place. If raw-map or inbound-API scanners still skip files, repair those scanners here so criteria 7 and 9 observe real source. Reconcile diverged private copies that this subtask restructures when a criterion requires one definition. Delete aliases and rename ports types that this subtask's surface criteria still trip over. If slot packages already exist, include them in the visibility pass.

## Validation strategy

The regressions to catch are a status projection that changes when read through
the query, a dissolved bag that drops a ledger or observability emission,
duplicate or reordered sequence numbers, an import resolving through a removed
alias, and a narrowed type that runtime-core's component still needs (a compile
failure). Use the goal-runner and status suites over real SQLite, compile all
modules, and add the tests listed above. Run `./gradlew check`, the engine, CLI,
MCP, core, and infra-sqlite suites, and `bill-unit-test-value-check`.

## Next path

Goal complete. If the child-repair or planning-hydrator ports still block this subtask's surface criteria, collapse them in this commit.

## Spec Path

.feature-specs/SKILL-378-runtime-engine-hexagonal-boundaries/spec_subtask_3_goal-runner-step-classes-and-engine-surface.md
