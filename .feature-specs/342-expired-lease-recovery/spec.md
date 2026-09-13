# Issue 342: Expired lease recovery for interrupted goal runs

## Intended Outcome

An interrupted goal run must not leave a decomposed goal permanently blocked
with "the existing process owner is ambiguous". When a recorded execution or
worker lease has `expires_at` in the past, the runtime treats the owner as dead,
reclaims through the existing takeover path, and clears stale
`runner_interrupted` pause residue on relaunch. Operators who still need manual
recovery can clear residual lease and pause wedges through `goal repair` with
the existing inspect/apply split.

## Acceptance Criteria

1. A parent execution lease whose `expires_at` is before the current clock is
   reclaimed on relaunch without requiring unambiguous process inspection.
2. A child worker lease whose `expires_at` is before the current clock is
   recovered through the existing takeover reservation path even when process
   inspection returns `OwnershipMismatch` or `Unsupported`.
3. A relaunch that reclaims an expired parent execution lease clears stale
   `runner_interrupted` pause flags (`paused`, `pause_requested`,
   `pause_consumed`, `pause_reason`) so the goal can continue without sqlite
   surgery.
4. A live, unexpired execution or worker lease still blocks a second foreground
   goal runner; duplicate-launch race handling is unchanged.
5. `goal repair` inspect reports a new wedge class for stale execution leases,
   stale child worker leases, and stale `runner_interrupted` pause residue, and
   `goal repair --apply` clears those wedges when no live unexpired lease holds
   the workflow.
6. `goal repair --apply` refuses to delete or overwrite a live unexpired worker
   lease and surfaces the existing live-lease refusal semantics.
7. Regression tests reproduce the expired-lease ambiguity block from issue 342
   and prove relaunch succeeds without manual database edits.
8. Focused engine, persistence, and CLI repair tests pass, and the
   dominant-stack quality check reports no new findings on touched modules.
9. The feature-spec manifest and all executable subtask specs remain
   schema-valid and acceptance-criteria extractable by the goal runtime.

## Constraints

- Reuse the existing generation-bump takeover reservation and execution-lease
  acquire paths; do not add a parallel lease table or weaken live-lease fencing.
- Treat `expires_at` as authoritative for dead-owner decisions when inspection
  is ambiguous; do not guess liveness from pid reuse alone.
- Keep `goal repair` confirm/apply semantics; new clearance actions follow the
  same inspect-then-apply pattern as existing wedge repairs.
- Prefer high-value regression tests over broad refactors.

## Non-Goals

- Changing heartbeat intervals, lease duration constants, or CHECK constraints
  on `lease_state`.
- Replacing `goal reset` or `goal replan` as heavy recovery for incompatible
  contract versions.
- Auto-terminating processes that inspection confirms are `ExactLive`.

## Affected Areas

- `runtime-kotlin/runtime-engine` goal execution and worker coordination.
- `runtime-kotlin/runtime-infra-sqlite` goal control and worker lease stores.
- `runtime-kotlin/runtime-cli` goal repair presentation.
- Focused engine, persistence, and CLI tests.

## Validation Strategy

- Run focused `GoalRunnerExecutionCoordinatorTest` and worker coordinator
  coverage for expired-lease reclaim.
- Run `GoalRunnerRepairTest` and CLI repair apply coverage for the new wedge
  class.
- Run the dominant-stack Kotlin quality check after implementation.

## Delivery Plan

1. Reclaim expired parent and child leases on relaunch and clear stale
   `runner_interrupted` pause residue.
2. Add `goal repair` inspect/apply clearance for residual stale lease and
   pause wedges.
