# Issue 342 · Subtask 1 — Expired lease reclaim on relaunch

## Scope

Fix the permanent "the existing process owner is ambiguous" block that strands
decomposed goals after a killed run leaves expired leases behind.

In `GoalRunnerExecutionCoordinator.reclaimableOwnerToken`, treat a parent
execution lease with `expires_at` before the injected clock as a confirmed-dead
owner and reclaim through the existing generation-bump acquire path without
requiring `NotRunning` process inspection.

Align child worker recovery in `FeatureTaskRuntimeWorkerCoordinator` so an
expired worker lease always proceeds to takeover reservation even when
inspection is ambiguous, matching the `leaseIsActive` gate already present but
closing any parent/child gaps that still block relaunch.

On successful reclaim of an expired parent execution lease, clear stale
`runner_interrupted` pause residue in `goal_runner_controls` (`paused`,
`pause_requested`, `pause_consumed`, `pause_reason`) so relaunch does not require
manual sqlite edits.

Preserve fail-closed behavior for unexpired live leases and unchanged
duplicate-launch race handling.

## Acceptance Criteria

1. An expired parent execution lease with ambiguous process inspection reclaims
   on relaunch and the goal body runs.
2. An expired child worker lease with ambiguous process inspection recovers
   through takeover reservation and continues the subtask workflow.
3. Stale `runner_interrupted` pause flags are cleared when an expired parent
   lease is reclaimed on relaunch.
4. An unexpired lease that inspection confirms as `ExactLive` still blocks a
   second foreground goal runner.
5. Regression tests reproduce the issue 342 failure mode and would fail if
   expired leases still report ambiguity instead of reclaiming.
6. Focused `GoalRunnerExecutionCoordinatorTest`, worker coordinator, and
   control-store tests pass for the touched paths.
7. The dominant-stack quality check reports no new findings on touched modules.

## Non-Goals

- Adding new wedge classes or `goal repair` actions (subtask 2).
- Changing lease duration, heartbeat cadence, or SQLite CHECK constraints.
- Terminating processes confirmed live by inspection.

## Dependency Notes

- None. Runs from `main` as the first subtask.

## Validation Strategy

1. Run focused execution-coordinator and worker-coordinator tests for expired
   lease reclaim and pause clearing.
2. Run control-store tests if pause clearing touches persistence encoding.
3. Run the dominant-stack quality check for changed Kotlin modules.

## Next Path

After this subtask commits, subtask 2 adds `goal repair` inspect/apply clearance
for residual stale lease and pause wedges that automatic relaunch does not
fully resolve.
