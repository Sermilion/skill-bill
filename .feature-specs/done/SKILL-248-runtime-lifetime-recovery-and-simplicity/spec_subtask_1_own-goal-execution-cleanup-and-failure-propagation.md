# SKILL-248 Subtask 1 - Own goal execution cleanup and failure propagation

Parent spec: [.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec.md](spec.md)
Issue key: SKILL-248

## Scope

Address F-001 and F-004 in investigation.md. Own runtime-engine goalrunner/GoalRunnerExecutionCoordinator.kt, GoalRunnerLedgerRecorder.kt, GoalRunnerProgressEventEmitter.kt, and GoalRunnerObservabilityEmitter.kt. Adjust existing process and diagnostic ports or runtime-core bindings only if the repair needs them.

Establish lease cleanup immediately after acquisition. Acquire heartbeat and shutdown-hook registrations under the same cleanup owner. Release acquired resources in reverse order on startup failure, body failure, interruption, cancellation, and normal completion. One failed cleanup must not skip later cleanup. Preserve the original exception identity and attach or independently record secondary cleanup failures. Use the existing fenced release operation and bounded shutdown facilities. Do not invent a generic resource framework.

Use the existing application cooperative-failure helpers where their semantics fit. Cancellation must escape goal progress and ledger callbacks. Interruption must preserve the signal through the existing interrupt port when needed. Ordinary optional publication failures must produce bounded diagnostics without replacing the primary outcome. A failed watermark read is not an empty history: fail visibly before recording a fabricated zero sequence, or recover the same authoritative watermark through the existing store before emitting another entry. Null workflow identity may remain a genuine absence result.

Keep the existing lease duration, generation checks, expired-owner policy, ledger wire representation, and successful goal output. The change owns exceptional lifetime and recording behavior, not a new supervision state machine.

## Acceptance Criteria

1. If heartbeat startup fails after lease acquisition, the coordinator releases the exact acquired owner token and generation and preserves the original failure. The goal body does not run.
2. If shutdown-hook registration fails, the coordinator attempts heartbeat stop and fenced lease release. If unregister or heartbeat stop later fails, remaining cleanup still runs.
3. A goal-body exception or cancellation remains the primary failure even when cleanup also fails. Secondary failures remain inspectable, and a successful body does not report success when required teardown fails.
4. Goal progress, ledger, and observability callbacks propagate CancellationException and interruption rather than treating them as absent identity or optional write failure. Normal missing-workflow results retain their existing meaning.
5. A failed ledger watermark read cannot silently initialize a resumed ledger at zero. A healthy resume records above the existing maximum and preserves cumulative edge counts.
6. Ordinary optional write failures emit a bounded diagnostic independent of the failed store. Diagnostic failure does not mask the primary exception or prevent remaining cleanup.
7. Regressions exercise startup failure, failure at each teardown step, primary-error preservation, callback cancellation and interruption, and healthy versus failed watermark reads through the actual coordinator and recorders.

## Non-Goals

- No changes to expired-lease takeover rules, heartbeat cadence, workflow phase order, or ledger schema.
- No new catch-all failure framework, dependency bag, or generic lifecycle abstraction.

## Dependency Notes

Depends on: none
No prerequisite. This commit ships independently because it changes only goal execution lifetime and recording failure behavior.

## Validation Strategy

Run GoalRunnerExecutionCoordinatorTest and goal ledger, progress, observability, and resume tests. Convert the six engine probes into intended-behavior regressions, replacing the deliberately broken expectations. Add controlled interruption and diagnostic-failure cases. Run affected composition and inward-layer architecture guards. Preserve transaction and fencing behavior; do not assert private fields or incidental call ordering except where acquisition and cleanup order defines resource ownership.

## Next Path

.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec_subtask_2_bound-git-process-lifetime.md

## Spec Path

.feature-specs/SKILL-248-runtime-lifetime-recovery-and-simplicity/spec_subtask_1_own-goal-execution-cleanup-and-failure-propagation.md
