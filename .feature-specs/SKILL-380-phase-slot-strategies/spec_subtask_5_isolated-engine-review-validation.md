# SKILL-380 Subtask 5 - Isolated engine, phase:review, phase:validation

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Resolves the engine half of F-008. Ships the two print-only isolated programs.

**Engine (`skillbill.engine.featuretask.slot`).**
- `IsolatedPhaseRequest`: repo root, optional intake, program id, optional
  instructions and per-step scope, review target when program is review,
  invocation-time profile inputs. No `workflowId`. Not
  `FeatureTaskRuntimeRunRequest`.
- `IsolatedPhaseResult`: completed, blocked, or failed, with session text.
- `IsolatedPhaseExecutor` runs the program through `PhaseRunner` with an
  in-memory context and no workflow writer. Unknown program, slot, or strategy
  id fails loudly. `phase:commit_push` is a usage error.
- Telemetry uses `invocation_id`. No `workflow_id`. Every start, finish, fail,
  and fallback emits a record.
- CLI: `skill-bill [<intake>] phase:<name>`. `phase:` and `operation:` together
  is a usage error even before operations exist (reserve the token).

**`phase:review`.** Today's standalone `bill-code-review` driver:
`ParallelCodeReviewRunner`, not the skeleton `last-commit-fix` strategy. A
skeleton `code_review` profile value does not redirect this program. Prints.
No skill-bill artifacts. Intake optional. Target `HEAD`, `uncommitted`, or
commit sha/name. Omitted: uncommitted if dirty, else HEAD. Every dirty path is
owned. Accept `mode:auto|inline|delegated`; omit and `auto` resolve to inline.
Unknown `phase:` ids other than the closed catalog fail as usage errors.
`phase:commit_push` is a usage error. After this subtask the registered
programs are `review` and `validation` only.

**`phase:validation`.** Today's `bill-code-check` repair window: the dominant
pack `validation_gate` collect-all once, fix every finding in that session,
one cache-bypassing collect-all. Missing pack gate fails as today. Intake
optional.

Instructions prepend, no size cap, apply to every step unless scoped. No resume.
No listed-skill changes.

## Acceptance Criteria

1. Isolated execution inserts no row into `feature_task_workflows` or `feature_task_runtime_sessions` and writes no phase records or ledger.
2. `skill-bill phase:review` prints through `ParallelCodeReviewRunner`, does not run `last-commit-fix`, resolves omitted `mode` and `mode:auto` to inline, and resolves an omitted target to `uncommitted` when dirty and `HEAD` otherwise. Unknown targets are a usage error.
3. `skill-bill phase:validation` runs today's `bill-code-check` collect-all repair window. `skill-bill phase:commit_push` is a usage error.
4. Isolated telemetry carries `invocation_id` and no `workflow_id`. Subtask 3 skeleton fixtures still match. No listed skills are added or removed.
5. ARCHITECTURE.md documents isolated versus skeleton and the `phase:` CLI for review and validation.

## Non-goals

- `phase:plan`, `phase:implement`, `phase:pr` (subtasks 6 and 7).
- Operations, dispatcher, deleting `skills/bill-*` (8–13).
- Isolated resume, worktree locks, plugin UI, or a second `PhaseRunner`.

## Dependency notes

- Depends on subtask 3, not 4. Isolated review uses the existing standalone
  code-review driver. Recheck CLI registration anchors at start.

## Validation strategy

Catch: a workflow row on isolated review/validation; omitted target picking HEAD
on a dirty tree; `last-commit-fix` running for `phase:review`; commit_push
accepted. Cover with SQLite assertions, dirty/clean target tests, a mode-default
test, and a usage-error test for commit_push. Run
`cd runtime-kotlin && ./gradlew check` plus engine, core, CLI, infra-sqlite.
`bill-unit-test-value-check` on changed tests.

## Next path

Subtasks 6, 7, and 8 may start. Continue with
`spec_subtask_6_isolated-plan-and-implement.md` unless taking an operation.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_5_isolated-engine-review-validation.md
