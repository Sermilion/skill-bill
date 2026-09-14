# SKILL-346 · Subtask 1 — Runnable recovery recommendations

## Scope

Fix defect 2 from issue 346: soft reset and related recovery surfaces recommend
`skill-bill goal reset <key> --subtask <id> --delete-child-workflow` even when
the subtask is `in_progress` (or otherwise not `blocked`), so running the
printed command always fails with scoped child deletion requires a blocked
subtask.

Audit every path that emits `scopedChildRecoveryCommand` or equivalent recovery
text: soft reset output, child wedge repair refusal, durable child classification
for incompatible terminal workflows, and CLI test fixtures. Gate the scoped-delete
recommendation on manifest subtask status and the same preconditions
`GoalRunnerResetReplanCoordinator` enforces before deletion.

When scoped delete is not advertised, emit the next runnable recovery step:
confirmed hard reset when planning/runtime must be wiped, replan when stale
planning bytes conflict, or explicit resume/stop guidance when the child is
active or resumable.

## Acceptance Criteria

1. No recovery output recommends `--delete-child-workflow` unless the target
   subtask status is `blocked`.
2. For `in_progress` subtasks with incompatible terminal child workflows, recovery
   output recommends a command that succeeds without manual manifest edits (for
   example `--hard --confirm-issue-key` or `--hard --yes` per existing CLI
   conventions).
3. Existing blocked-subtask scoped deletion behavior and tests remain green;
   update expectations only where the previous recommendation was incorrect.
4. Regression tests assert refused vs recommended commands for at least
   `in_progress` + incompatible terminal and `blocked` + incompatible terminal.
5. Focused engine and CLI tests pass for touched modules.

## Non-Goals

- Changing hard reset branch behavior (subtask 2).
- Weakening the blocked-only guard on scoped child deletion.

## Dependency Notes

- None. First subtask on `main`.

## Validation Strategy

1. Run `GoalChildRecoveryTest`, relevant `GoalRunnerRepairTest` cases, and CLI
   goal reset execution tests.
2. Run dominant-stack quality check on touched Kotlin modules.

## Next Path

After commit, subtask 2 addresses hard reset leaving a trailer-carrying tip and
ambiguous subtask span on relaunch.
