# SKILL-245 · Subtask 1 — Goal purge command

## Scope

Add `skill-bill goal purge <issue-key>` as a confirmed operator command that
makes a decomposed goal look unexecuted in skill-bill databases, then restores
the authored spec tree to its unlaunched prepared shape.

Extend persistence so purge deletes the parent `feature_task_workflows` row,
every `goal_child` owned by that parent, and goal-scoped satellites (planning
preparations / shared preplan / subtask plans, execution identities, worker
leases, phase settlements, `goal_runner_controls`, out-of-band acceptances,
`goal_run_sessions`, `goal_subtask_events`, `goal_issue_progress`). Cascade
where foreign keys already do; delete the rest in the same transaction.

Restore `.feature-specs/<issue-key>-*/decomposition-manifest.yaml` to all
`pending` subtasks, null `workflow_id` / `commit_sha` / `branch` / blocked
fields, and `current_subtask_intent` of start on the first subtask. Recreate
missing subtask spec files from git HEAD when those paths are tracked (linear
scratch deletion). Leave authored `spec.md` and surviving subtask markdown
bodies in place when they already exist.

Prune `refs/skill-bill/checkpoints/<issue-key>/` the same way hard reset
prunes reset subtask namespaces. Do not delete the feature branch or rewrite
commits.

Wire CLI next to `goal reset` with `--confirm-issue-key` or `--force`/`--yes`.
Refuse while a live parent or child worker lease is held. Leave standalone
feature-task workflows for the same issue key. Leave telemetry outbox rows.

## Acceptance Criteria

1. After `goal purge` on a seeded decomposed goal, the parent workflow id and
   every owned goal-child workflow id are absent from `feature_task_workflows`,
   and planning / control / goal-progress / session / lease / identity rows
   for those ids are gone.
2. `skill-bill goal preflight <issue-key> --format json` after purge reports
   `verdict: new_work` with no resumable candidate.
3. The spec directory still contains parent spec, each subtask spec, and a
   schema-valid unlaunched `decomposition-manifest.yaml`.
4. Without `--confirm-issue-key <issue-key>` or `--force`/`--yes`, purge
   refuses and leaves DB and spec unchanged.
5. A standalone feature-task row for the same issue key survives purge.
6. Focused sqlite, engine, and CLI tests pass for the behaviors above.

## Non-Goals

- Changing `goal reset --hard` to delete the parent workflow.
- Telemetry forget, branch deletion, or Linear issue deletion.
- Purging in-progress leased workers without an explicit refuse.

## Dependency Notes

- None. First subtask on `main`.

## Validation Strategy

1. Add a sqlite test that seeds parent, two children, planning, progress, and
   a standalone sibling, then asserts only the goal-owned rows disappear.
2. Add CLI tests for confirmation refusal and successful purge plus spec
   restore.
3. Run dominant-stack quality check on touched Kotlin modules.

## Next Path

None. This is the only subtask.
