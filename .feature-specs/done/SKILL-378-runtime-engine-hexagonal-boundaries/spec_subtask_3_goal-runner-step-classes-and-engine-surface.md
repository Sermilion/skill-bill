# SKILL-378 Subtask 3 - Goal-runner sequences, reads, and silent reads

Parent spec: [spec.md](spec.md)
Issue key: SKILL-378

## Scope

Resolves investigation F-003, F-006, and F-007. The goal-runner step classes,
aliases, visibility, and raw-map work (F-002 for `skillbill.engine.goalrunner`,
F-005, F-009, and the raw-map part of F-008) moved to
`followup_goal-runner-step-classes-and-engine-surface.md`, because one implement
pass carried only the sequence work of the combined scope.

Already on the branch (`5b0ab9016`): durable sequence allocation inside the
write transaction and the AC-1 allocation test. Recheck them; record them as met
where they hold, and finish them where the recheck finds a gap.

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

## Acceptance Criteria

1. A test that records planning attempts and progress events for one workflow
   in one run, through the production recorders, reads back distinct, strictly
   increasing progress sequence numbers. The same holds across two recorder
   instances.
2. No engine class holds a sequence counter or a per-workflow sequence map.
3. The four named goal-runner readers do not reference `FeatureTaskRuntimePhaseRecorder`,
   and status projection output for the existing fixtures is unchanged.
4. Each silent read in F-006 has a test feeding malformed durable input that
   asserts the typed error, or the record fields together with the degraded
   projection value.
5. Existing goal-runner, planning, status, repair, preflight, and CLI goal
   suites pass, with test changes limited to construction, imports, and the new
   tests.

## Non-goals

- Goal-runner step classes, `GoalRunnerSharedArgs.kt`, the `DefaultGoalPlanningSweep`
  extensions, the step-class and inject-property guards, alias deletion, the
  visibility pass, and the raw-map pass. All of that is
  `followup_goal-runner-step-classes-and-engine-surface.md`.
- Collapsing `GoalRunnerChildRepairRunnerPort`, `GoalRunnerChildRepairStore`, or
  `GoalChildPlanningHydratorPort` (SKILL-376 follow-up).
- Deleting `fun interface` seams with test substitutes.
- Changing status output, control verbs, or planning semantics.
- Typealiases in other modules.

## Dependency notes

Depends on subtask 2. It does not wait for another issue. Apply the sequence
change wherever the outcome store implementation lives now. Build the read query
over the existing `FeatureTaskRuntimePhaseRecorder` parts as they are now.

## Validation strategy

The regressions to catch are a status projection that changes when read through
the query, duplicate or reordered sequence numbers, and a silent read that still
swallows malformed input. Use the goal-runner and status suites over real SQLite,
compile all modules, and add the tests listed above. Run `./gradlew check`, the
engine, CLI, MCP, core, and infra-sqlite suites, and `bill-unit-test-value-check`.

## Next path

Goal complete. Then run `followup_goal-runner-step-classes-and-engine-surface.md`,
then `followup_feature-task-step-classes.md`.

## Spec Path

.feature-specs/SKILL-378-runtime-engine-hexagonal-boundaries/spec_subtask_3_goal-runner-step-classes-and-engine-surface.md
