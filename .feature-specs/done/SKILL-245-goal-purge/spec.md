# SKILL-245: Goal purge forgets a run except the authored spec

## Intended Outcome

Operators can purge a decomposed goal so skill-bill databases and runtime
continuation state treat that issue as never executed, while the authored spec
tree is restored to its unlaunched prepared shape. The next `skill-bill goal`
or `bill-feature` launch for the same issue opens a new parent workflow.

## Acceptance Criteria

1. `skill-bill goal purge <issue-key>` deletes the parent feature-task
   workflow row, every goal-child workflow owned by that parent, and all
   goal-scoped satellite rows keyed by those workflow ids or the issue key
   (planning checkpoints, execution identities, worker leases, phase
   settlements, goal-runner controls, acceptances, goal run sessions, subtask
   events, and issue progress).
2. After a successful purge, goal lookup and preflight for that issue report
   new work: no resumable parent, no goal-child candidates, no planning
   checkpoints. The next launch allocates a new parent workflow id.
3. Purge restores `.feature-specs/<issue-key>-*/` to an unlaunched prepared
   bundle: parent spec and every subtask spec file present, and
   `decomposition-manifest.yaml` with all subtasks `pending`, null runtime
   identities, and `current_subtask_intent` ready to start subtask 1.
4. Purge requires the same confirmation gate as hard reset
   (`--confirm-issue-key` matching the issue key, or `--force` / `--yes`).
5. Purge does not rewrite published git history, delete the feature branch, or
   remove telemetry outbox / remote lifecycle events. It does prune
   `refs/skill-bill/checkpoints/<issue-key>/` for the purged issue.
6. Standalone feature-task rows for the same issue key are left untouched.
7. Regression tests cover a seeded multi-subtask goal whose DB rows and
   mutated manifest disappear, whose spec files remain or are restored, and
   whose confirmation gate refuses without `--confirm-issue-key` or `--force`.
8. Dominant-stack quality check reports no new findings on touched modules.

## Constraints

- Keep hard reset as recovery of the same parent workflow. Purge is a
  separate command and must not change `--hard` semantics.
- Refuse purge while a live parent or child worker lease is held.
- Loud-fail when the spec directory cannot be restored to a schema-valid
  unlaunched manifest; do not leave DB deleted and spec half-restored, or
  spec restored and DB rows remaining.
- Prefer CLI-visible operator paths over hand-editing `review-metrics.db`.

## Non-Goals

- Deleting Linear issues or remote git commits / tags / PRs.
- Force-push or checkout of the feature branch back to `main`.
- Purging telemetry outbox or remote telemetry (optional `--forget-telemetry`
  is out of scope).
- Changing soft reset, scoped child deletion, or `--restore-after-hard-reset`.
- Purging unrelated workflows that share the repository database.

## Affected Areas

- `../../../runtime-kotlin/runtime-infra-sqlite` goal-scoped row deletion including the
  parent workflow.
- `../../../runtime-kotlin/runtime-engine` goal runner purge orchestration and spec
  restore.
- `../../../runtime-kotlin/runtime-cli` `goal purge` command, confirmation, and tests.
- `../../../runtime-kotlin/runtime-ports` purge persistence port.

## Validation Strategy

- SQLite tests that a seeded parent, children, and satellites are absent after
  purge and that a standalone row for the same issue remains.
- Engine or CLI tests that the spec directory is an unlaunched prepared
  bundle and that confirmation is required.
- Dominant-stack Kotlin quality check on touched modules.

## Delivery Plan

1. Add an atomic purge write that deletes goal-scoped DB state and restores
   the spec bundle.
2. Expose `skill-bill goal purge` with the hard-reset confirmation gate.
