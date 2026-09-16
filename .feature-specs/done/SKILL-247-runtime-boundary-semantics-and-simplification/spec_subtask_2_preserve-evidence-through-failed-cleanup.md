# SKILL-247 Subtask 2 - Preserve evidence through failed cleanup

Parent spec: [.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec.md](spec.md)
Issue key: SKILL-247

## Scope

Resolve F-004 in investigation.md. Own SQLiteDatabaseSessionFactory transaction cleanup, core/ConnectionTransactions, ProcessRunLifetime, JvmAgentRunProcessRunner, CappedUtf8Drain teardown, ProcessRunDegradationRecorder, and the smallest existing diagnostic seams needed by those owners. Keep acquisition, cleanup, primary failure, and secondary evidence together.

## Acceptance Criteria

1. A failed body or commit still exits with its primary failure when rollback also fails. Attach rollback failure as suppressed evidence and emit the required bounded diagnostic. A successful rollback adds no false failure. Read and write transaction paths follow the same precedence without changing their snapshot or BEGIN mode.
2. Process cleanup records are exported when setup, a callback, waiting, or terminal lifecycle publication throws. The exception path cannot discard the local degradation recorder before anyone observes it. Retain the original callback or cancellation throwable as the primary failure.
3. Endpoint close failure remains visible when the output sink also fails. Use an existing independent bounded diagnostic path; diagnostic failure must not recursively call the same failed sink or outbox.
4. Every cleanup wait introduced or changed is bounded. Audit stream-close ordering for a child or drain that has not stopped; do not call a potentially blocking close on the owner thread and describe a preceding timed join as a total cleanup bound.
5. Use deterministic injected rollback and cleanup failures, plus the existing real-process callback test. Assert primary identity, secondary evidence, process release, immutable incomplete capture, and retained interruption. Keep tests that protect previously fixed SKILL-239 behavior.

## Non-Goals

- No repository-wide exception wrapper or catch rewrite.
- No claim that a SQLite commit and file projection form one transaction.
- No changes to process selection, ownership fencing, or normal shutdown policy unrelated to these paths.

## Dependency Notes

Depends on: none
Can ship independently. Use existing RuntimeDiagnostics and process diagnostic facilities. Coordinate shared telemetry diagnostic signatures with subtask 1 if it changed them.

## Validation Strategy

Run SQLiteDatabaseSessionFactoryTest, SQLiteDatabaseSessionFactoryTypedFailureTest, DatabaseMigrationsTest, JvmAgentRunProcessRunnerTest, and the new failure-injection cases. Confirm normal rollback and projection recovery remain intact. Use the repository test-value gate for new cases.

## Next Path

Continue with subtask 3 after cleanup failure evidence is preserved.

## Spec Path

.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec_subtask_2_preserve-evidence-through-failed-cleanup.md
