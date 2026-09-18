# SKILL-355 - worktree-edit-journal

## Mode

single_spec

## Intended outcome

The agent wait loop records a runtime-owned worktree edit journal from observed git numstat, and `goal status` / `goal watch` project those writes plus the audit remaining-criteria retry count. An operator can tell a live phase that is changing files from one that is only reprinting remaining criteria. The agent does not write the journal.

## Scope

Own persistence of per-path line counts observed during a feature-task agent session, and the status projection that reads them. Reuse the existing git `--numstat` path in `runtime-infra-fs` (`parseDiffStat` / combined staged+unstaged, plus untracked files the current aggregate misses). Persist in SQLite through a new port and store. Project on `GoalRunnerStatusProjection` and both full and `--monitor` `goal status` output.

Do not replace `agent_activity_stamps` (one-row pulse) or `WorktreeActivityProbe`'s idle token. The journal is additive. Do not add a child MCP write tool. Do not store hunks, file bodies, or remaining-criteria text.

Prepared in local mode on 2026-09-17. Baseline is `main` at `d8a103a1e`. This bundle prepares work only; the subtask starts pending.

## Acceptance Criteria

1. Each activity-probe tick whose per-path numstat set differs from the last persisted tick for that workflow appends bounded rows: `workflow_id`, `phase_id` (the workflow `current_step_id` at persist time), `recorded_at`, repo-relative `path`, `lines_added`, `lines_removed`, and `source=worktree_probe`.
2. Line counts are measured by the runtime from git numstat against HEAD (staged and unstaged) plus untracked files counted as insertions; binary paths are omitted. No hunk ranges, diff bodies, or file contents are stored. Consecutive ticks with an unchanged numstat set insert nothing.
3. `goal status` and `goal status --monitor` (and `goal watch`) print the latest tick's timestamp, a bounded path sample, and net insertions/deletions, plus `audit_ac_retry_count` measured from the child phase ledger. An unmeasured journal or retry count is omitted with availability, never emitted as zero.
4. No MCP tool accepts agent-declared edit progress. If a later declared-progress hint exists, it does not insert journal rows and does not count as durable workflow progress.
5. Idle detection and `agent_activity_stamps` keep using the existing probe token and one-row pulse. Journal rows do not keep a run fresh and do not change progress-idle timeout.
6. Runtime-private paths under `../../../.skill-bill` are excluded. The journal is capped per workflow; overflow drops oldest rows and emits an observability record. No new suppression, baseline row, or exemption.

## Constraints

- Follow `../../../runtime-kotlin/ARCHITECTURE.md` design principles, `docs/code-principles.md`, `docs/observability-policy.md`, and AGENTS.md. Journal records are counts and paths only.
- Wire and payload keys live in `runtime-contracts`; do not inline them at persist or status seams.
- SQLite persistence stays in `runtime-infra-sqlite` behind a port. The wait loop and status projection do not import the store.
- Reuse existing git numstat parsing; do not add a second numstat grammar.
- Kotlin under `runtime-kotlin` carries no `//` comments and no non-KDoc block comments.

## Non-goals

- An MCP write tool in the child briefing, or treating implement/audit receipts as edit evidence.
- Per-hunk start/end ranges, selected diff lines, or remaining-criteria text on status.
- Redefining execution liveness, lease freshness, or idle timeout from journal rows.
- Replacing `agent_activity_stamps` or the worktree activity token.
- Changing audit retry caps, remaining-criteria settlement, or checkpoint owned-path rules.

## Validation strategy

Name the regression before each test: a dirty Kotlin file that status still reports only as `live`; a quiet tree that keeps inserting rows; an MCP client that can stamp journal rows; `--monitor` omitting retry count; `../../../.skill-bill` paths in the sample; idle timeout firing because journal writes were treated as durable progress; a missing measurement emitted as `0`. Cover persist, skip-unchanged, untracked insertions, private-path exclusion, cap truncation, SQLite restart, CLI full and `--monitor` projection, and MCP registry inventory with no new write tool. Run targeted engine, sqlite, CLI, and MCP tests, then the pack-declared quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Run `skill-bill goal SKILL-355`. The prepared manifest is the goal runner's input.
