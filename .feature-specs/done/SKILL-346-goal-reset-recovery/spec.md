# SKILL-346: Goal reset leaves the branch in a state the runtime always refuses

## Intended Outcome

Operators can recover decomposed goals after soft or hard reset without hitting
recovery commands that refuse to run or relaunching into an unrecoverable
ambiguous subtask-commit span. Recovery guidance must match subtask status and
CLI gates. Hard reset must not report success while leaving a trailer-carrying
branch tip that the commit gate always rejects when durable checkpoint identity
was cleared.

## Acceptance Criteria

1. Soft reset, repair refusal, and child-wedge diagnosis never advertise
   `goal reset --subtask … --delete-child-workflow` unless the selected subtask
   is `blocked` and scoped child deletion would succeed.
2. When scoped child deletion is not runnable, recovery output names a command
   that will run for the observed subtask status and child classification (for
   example hard reset with confirmation, replan for stale planning bytes, or
   resume when the child is resumable).
3. Hard reset either refuses before mutating durable state when the feature
   branch tip still carries a `Skill-Bill-Subtask` trailer for this goal with no
   durable checkpoint identity to prove ownership, or it completes only after
   coordinating branch state so the next run can settle the active subtask span
   without the ambiguous-span operator refusal.
4. Subtask N>1 resume after identity wipe can proceed when HEAD legitimately
   carries the prior subtask trailer as review base: a supported path re-adopts
   a trailer-matching owned commit into durable checkpoint identity instead of
   telling the operator to restore identity with no command.
5. Regression tests cover the WE-4789-style repro (soft recommendation refused,
   hard reset then ambiguous span) and dominant-stack quality check reports no
   new findings on touched modules.
6. Feature-spec manifest and subtask specs remain schema-valid and
   acceptance-criteria extractable by the goal runtime.

## Constraints

- Preserve loud failure when ownership of HEAD cannot be proved; do not infer
  spans from stale review bases or squash unrelated history.
- Scoped child deletion stays blocked for non-`blocked` subtasks; fix
  recommendations, not the guard.
- Prefer CLI-visible operator paths over hand-editing `review-metrics.db`.
- High-value regression tests over broad refactors.

## Non-Goals

- Redesigning decomposition commit policy or review accounting broadly.
- Automatic force-push or history rewrite on shared branches without existing
  finalisation semantics.
- Changing unrelated peak-hours, telemetry, or planning checkpoint contracts.

## Affected Areas

- `../../../runtime-kotlin/runtime-engine` goal reset, recovery classification, subtask
  commit resolver and checkpoint identity persistence.
- `../../../runtime-kotlin/runtime-cli` reset and recovery messaging.
- Focused engine and CLI tests (`GoalChildRecoveryTest`, `GoalRunnerRepairTest`,
  reset execution tests).

## Validation Strategy

- Add or extend engine tests for recovery command selection and hard-reset
  branch coordination.
- Run focused CLI reset tests and dominant-stack Kotlin quality check.

## Delivery Plan

1. Align recovery recommendations with runnable scoped deletion and alternatives.
2. Close the hard-reset orphan-commit trap and support trailer-based re-adoption
   when proof is available.
