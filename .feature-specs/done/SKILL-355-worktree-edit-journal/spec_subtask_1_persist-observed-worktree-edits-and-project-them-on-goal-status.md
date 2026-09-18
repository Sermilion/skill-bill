# SKILL-355 Subtask 1 - Persist observed worktree edits and project them on goal status

Parent spec: [.feature-specs/SKILL-355-worktree-edit-journal/spec.md](spec.md)
Issue key: SKILL-355

## Scope

Deliver the whole feature in this commit.

Add a SQLite journal table and port for per-path worktree numstat ticks. On each `WorktreeActivityProbe` / wait-loop activity tick, if the measured per-path numstat set changed, persist rows for the changed paths. Read `phase_id` from the active workflow step, not from the agent. Include untracked files as insertions. Skip binaries and `../../../.skill-bill` paths. Cap rows per workflow and record truncation.

Project the latest tick and `audit_ac_retry_count` (from existing `audit_ac_retry` ledger entries via `auditGapIterationCount`) onto `GoalRunnerStatusProjection` and `goal status` / `--monitor` / `goal watch`. Declare new wire keys in `runtime-contracts`. Record in `../../../runtime-kotlin/agent/decisions.md` that observed edits are runtime-owned and that agents must not write them. Do not add a file census to `ARCHITECTURE.md`. Leave MCP tool inventory without a write tool; a read tool is allowed only if it reuses the status projection shape.

## Acceptance Criteria

1. Each activity-probe tick whose per-path numstat set differs from the last persisted tick for that workflow appends bounded rows: `workflow_id`, `phase_id` (the workflow `current_step_id` at persist time), `recorded_at`, repo-relative `path`, `lines_added`, `lines_removed`, and `source=worktree_probe`.
2. Line counts are measured by the runtime from git numstat against HEAD (staged and unstaged) plus untracked files counted as insertions; binary paths are omitted. No hunk ranges, diff bodies, or file contents are stored. Consecutive ticks with an unchanged numstat set insert nothing.
3. `goal status` and `goal status --monitor` (and `goal watch`) print the latest tick's timestamp, a bounded path sample, and net insertions/deletions, plus `audit_ac_retry_count` measured from the child phase ledger. An unmeasured journal or retry count is omitted with availability, never emitted as zero.
4. No MCP tool accepts agent-declared edit progress. If a later declared-progress hint exists, it does not insert journal rows and does not count as durable workflow progress.
5. Idle detection and `agent_activity_stamps` keep using the existing probe token and one-row pulse. Journal rows do not keep a run fresh and do not change progress-idle timeout.
6. Runtime-private paths under `../../../.skill-bill` are excluded. The journal is capped per workflow; overflow drops oldest rows and emits an observability record. No new suppression, baseline row, or exemption.

## Non-goals

No MCP write tool, no hunk storage, no remaining-criteria text on status, no change to audit retry settlement or idle policy, no replacement of activity stamps.

## Dependency notes

None. Starts from `main`.

## Validation strategy

Name the regression before each test: a file that grew while status stayed `live` with no paths; a second tick with no tree change that inserted another row; MCP `tools/list` gaining an edit-write tool; `--monitor` still omitting retry count; a `../../../.skill-bill` path in the sample; idle timeout treating journal persistence as durable progress. Add SQLite round-trip and cap-truncation tests. Run targeted module tests, then the pack-declared quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

None. This is the only subtask.

## Spec Path

.feature-specs/SKILL-355-worktree-edit-journal/spec_subtask_1_persist-observed-worktree-edits-and-project-them-on-goal-status.md
