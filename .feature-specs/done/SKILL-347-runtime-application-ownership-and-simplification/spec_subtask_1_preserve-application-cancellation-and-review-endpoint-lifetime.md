# SKILL-347 Subtask 1 - Preserve application cancellation and review endpoint lifetime

Parent spec: [.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec.md](spec.md)
Issue key: SKILL-347

## Scope

Own F-001 and F-002. Start in review/ParallelCodeReviewRunnerLaneLaunch.kt, review/ParallelCodeReviewRunnerFailureAdmission.kt, review/SpecIntentProjectionResolver.kt, review/SpecIntentProjectionExtractor.kt, runtimepersistence/RuntimeOwnedPersistenceBoundary.kt, idestatus/AgentActivityStampWriter.kt, updatecheck/UpdateCheckService.kt, and the touched telemetry service and sync paths. Trace adjacent port defaults such as TelemetrySettingsProvider.loadOrNull when they consume cancellation before the application sees it.

Establish endpoint ownership immediately after bind. Provider staging and launch must execute within the same cleanup scope. Keep provider-specific file mechanics behind the existing staging adapter; do not add a strategy framework for this one cleanup fix.

Use the existing Kotlin cancellation type and rethrow interruption before ordinary failure mapping. The existing InterruptSignalPort now carries restoration through telemetry. Move JvmInterruptSignalPort out of runtime-ports into the existing outer JVM adapter module, retain the contract in runtime-ports, and wire its implementation in runtime-core. Remove concrete JVM defaults from TelemetrySyncRuntime and DrainRequest so application callers provide the port. Do not create another thread-control abstraction. Keep restoration and original-exception propagation on every interrupted exit. Preserve the current telemetry cancellation and claim-settlement behavior. Ordinary optional failures still return their documented result and emit bounded, payload-free diagnostics. Diagnostic failure must not replace the primary failure or recurse into the failed persistence path.

Use the retained probe source as reproduction evidence only. Permanent tests exercise the same public use cases and assert intended outcomes, without reflective mutation of production fields.

The earlier application Thread-reference test now passes. Extend the existing boundary guard to reject concrete environment operations in inward port sources and adapter defaults in application code. Test the guard through its normal source-scan entry point with an invalid inward implementation and a valid outer implementation; a green import scan alone does not prove ownership.

## Acceptance Criteria

1. A throwing Cursor staging adapter after a successful endpoint bind closes the handle exactly once and launches no worker. Successful staging, unsupported launch, throwing launch, cancellation, and a simultaneous cleanup failure retain correct ownership and the primary failure.
2. CancellationException and InterruptedException propagate through the named spec, review, activity, update-check, persistence, and telemetry boundaries. Ordinary unreadable input or I/O failure retains its documented typed result and diagnostic behavior.
3. Review broker or endpoint binding interruption does not become an Unbound result, and interrupted lane execution does not become a normal failed-lane result.
4. Telemetry delivery and reconciliation retain stale-owner fencing, current-time claims, finite transport deadlines, and cancellation propagation. InterruptSignalPort stays in runtime-ports, its JVM implementation belongs to an outer adapter, and runtime-core supplies it. Application defaults do not select that implementation. A substituted interrupt port observes restoration and the original InterruptedException still escapes.
5. Required persistence errors retain the original cause, and optional failure diagnostics remain observable through an existing independent diagnostic path when the primary store fails. Diagnostic failure preserves the primary exception.
6. Behavioral regressions cover the reproduced spec-read, optional-persistence, update-check, and activity cancellation cases plus staging-before-launch cleanup. Existing intended-behavior cancellation and evidence-boundary tests remain compatible.
7. The existing architecture guard rejects an inward concrete thread-restoration implementation and application defaults that select it, while accepting the same operation in its outer adapter owner. Existing baselines and exclusions do not grow.

## Non-Goals

- No provider capability redesign, concurrency framework, generic exception policy engine, or new thread-control port.
- No activity cache lifecycle changes or projection-owner changes beyond the cancellation handling owned here.

## Dependency Notes

Depends on: none
No prerequisite. This commit can ship independently. Subtask 2 may edit AgentActivityStampWriter afterward; preserve the cancellation behavior introduced here.

## Validation Strategy

Use the existing application evidence-boundary and telemetry cancellation tests plus focused new failure-path tests. Run :runtime-application:test and the affected runtime-core layer-boundary check. Where thread handling changes in an outer adapter, run that adapter's interruption tests. Verify endpoint closure and suppressed failures as behavior, not source-text shape. Recheck the ports source boundary and the three former concrete defaults. Keep interruption flag tests in the JVM adapter tests and restoration-call tests in application tests; do not infer either behavior from import absence.

## Next Path

.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec_subtask_2_bind-activity-and-projection-state-to-their-owners.md

## Spec Path

.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec_subtask_1_preserve-application-cancellation-and-review-endpoint-lifetime.md
