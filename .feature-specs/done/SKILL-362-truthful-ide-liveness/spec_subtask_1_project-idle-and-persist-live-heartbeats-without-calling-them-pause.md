# SKILL-362 Subtask 1 - Project idle and persist live heartbeats without calling them pause

Parent spec: [.feature-specs/SKILL-362-truthful-ide-liveness/spec.md](spec.md)
Issue key: SKILL-362

## Scope

Deliver the whole feature in this commit. Resolve F-001, F-002, and F-003 in [investigation.md](investigation.md).

Own `IdeStatusProjector.goalLifecycleFromProjection`, `GoalRunnerStatusProjectionAssembler.resolveParentExecutionLiveness` / `resolveChildExecutionLiveness` / `livenessOfLeaseOwner`, `AgentActivityStampWriter.persist`, `WorktreeEditJournalWriter` persist, `DatabaseRuntime` busy_timeout if the retry policy changes, plugin mapping only if an idle payload currently cannot reach Idle, `IdeStatusServiceGoalProjectionTest` and sibling engine liveness tests, golden IDE-status fixtures that treat expired-lease as paused, and a decision entry in `../../../runtime-kotlin/agent/decisions.md`.

Stop mapping idle liveness to paused. Consult process inspect when the lease is expired or the heartbeat write failed so a live parent or child JVM stays `live`. Keep operator pause as paused with `paused_at`. Make stamp and journal writes survive SQLITE_BUSY beyond the current 5s wait, or retry in-process, without splitting the metrics database.

## Acceptance Criteria

1. An expired parent lease with `paused: false` projects `lifecycle_state: idle` or `active` if inspect reports a live owner, never `paused`, and the wire has no `paused_at`.
2. `GoalRunnerControlState.paused == true` (or a consumed operator pause) still projects `paused` with `paused_at` and the existing pause reason.
3. A running parent or child JVM whose lease heartbeat failed with SQLITE_BUSY still projects `execution_liveness: live` and not paused.
4. The plugin Idle headline is used for `lifecycle_state: idle`; Paused is used only for `paused`.
5. A decision entry records that idle liveness is not operator pause, and records any busy_timeout or retry change relative to 2026-06-26.

## Non-goals

No SKILL-361 package moves. No per-project database. No change to progress-idle kill timers.

## Dependency notes

None. Starts from the branch head. SKILL-361 may still be in progress in the same checkout; do not reopen its packaging files except if an import path this subtask already owns moved.

## Validation strategy

Name the regression before each test: expired-lease fixture still expecting paused; operator pause dropped; inspect skipped on expiry; plugin treating idle JSON as Paused; busy writer still dropping the last heartbeat used for liveness. Run ide-status service tests, goal status liveness tests, sqlite session tests, plugin mapper tests, and the pack-declared quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

None. This is the only subtask.

## Spec Path

.feature-specs/SKILL-362-truthful-ide-liveness/spec_subtask_1_project-idle-and-persist-live-heartbeats-without-calling-them-pause.md
