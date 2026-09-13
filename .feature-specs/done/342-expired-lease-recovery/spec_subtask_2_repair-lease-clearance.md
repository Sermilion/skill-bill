# Issue 342 · Subtask 2 — Repair lease and pause clearance

## Scope

Give operators a supported recovery path for stale lease and pause residue
without raw sqlite edits.

Add a new `GoalRunnerWedgeClass` (and durable diagnosis/apply wiring) for:

- stale parent execution leases whose `expires_at` is in the past,
- stale child worker leases whose `expires_at` is in the past, and
- stale `runner_interrupted` pause residue on the parent goal runner controls.

Extend `GoalRunnerRepairCoordinator` and the child repair store so `goal repair`
inspect reports these wedges and `goal repair --apply` clears them atomically
when no live unexpired lease holds the workflow. Reuse the existing
`LIVE_LEASE_REFUSED` path when inspection confirms an unexpired live owner.

Surface inspect output and refusal messages that name the wedge and affected
workflow ids without exposing internal table names.

## Acceptance Criteria

1. `goal repair` inspect reports the new stale-lease and stale-pause wedge
   classes for fixtures matching the issue 342 sqlite workaround inputs.
2. `goal repair --apply` clears diagnosed stale execution leases, stale child
   worker leases, and stale `runner_interrupted` pause flags in one apply pass
   when no live unexpired lease is present.
3. `goal repair --apply` refuses with `LIVE_LEASE_REFUSED` when an unexpired
   lease is confirmed live, preserving existing safety semantics.
4. Clearing stale pause residue does not remove an operator-initiated
   `operator_stop` pause reason.
5. Regression tests cover inspect-only and apply paths, including a fixture that
   previously required manual `DELETE`/`json_set` against the runtime database.
6. Focused `GoalRunnerRepairTest` and CLI repair apply tests pass.
7. The dominant-stack quality check reports no new findings on touched modules.

## Non-Goals

- Clearing wedges that require contract-version hard reset or review-base
  recovery.
- Weakening global SQLite locking or adding raw-sql escape hatches in the CLI.
- Changing which child phase-output wedges repair already handles.

## Dependency Notes

- Depends on subtask 1 so expired-lease semantics and automatic relaunch reclaim
  are settled before repair clearance lands.

## Validation Strategy

1. Run `GoalRunnerRepairTest` focused on the new wedge inspect and apply paths.
2. Run CLI repair apply coverage for stale lease and pause clearance.
3. Run the dominant-stack quality check for changed Kotlin modules.

## Next Path

After this subtask commits, the decomposed goal is complete when all parent
acceptance criteria are satisfied.
