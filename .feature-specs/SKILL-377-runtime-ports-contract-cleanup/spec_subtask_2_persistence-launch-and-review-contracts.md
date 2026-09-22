# SKILL-377 subtask 2 - Persistence, launch, and review contracts

Parent: [spec.md](spec.md). Findings: F-004 (non-git rows), F-005, F-006, F-008, F-009, F-013, and the locator sentinel from F-012 in [investigation.md](investigation.md).

## Scope

Workflow state (F-005):

- Replace `FeatureVerifyWorkflowStateRepository`, `FeatureTaskRuntimeWorkflowStateRepository`, and the `WorkflowFamily` extension helpers (`save`, `saveRecord`, `get`, `getAll`, `list`, `latest`, `sessionSummary`) with family-keyed members on `WorkflowStateRepository`: `save(family, …)`, `get(family, workflowId)`, `getAll(family, workflowIds)`, `list(family, limit)`, `latest(family)`, and `sessionSummary(family, sessionId)`.
- The SQLite adapter owns table selection and batching. `WORKFLOW_SNAPSHOT_BATCH_SIZE` moves into SQLite as an internal constant.
- Update about 50 call sites from `family.get(repo, id)` to `repo.get(family, id)`.
- Delete `FeatureImplementWorkflowStateRepository`, `FeatureImplementSessionSummary`, both `toContract` mappers in `WorkflowRecordMapping.kt`, and the two contracts they map to (`FeatureImplementSessionSummaryContract`, `FeatureVerifySessionSummaryContract`, with their same-file keys, which nothing else uses), plus the SQLite code that exists only to implement them. Keep `terminalizeLegacyProseFeatureTaskWorkflow` and mode-agnostic `getFeatureTaskWorkflow` for legacy rows.
- Keep `toSnapshot`/`toRecord` as SKILL-372 subtask 1 leaves them.
- Update the stale "detekt threshold" KDoc on `WorkflowStateRepository` to describe the landed structure.

Review preparation (F-006): add one `ReviewPreparationFacts` value (scope, stack routing, lane selection, matched rules, learnings, build/test facts). `ParallelReviewPreparationCompiler` builds it, and `ReviewPreparationService` takes it. Delete the six interfaces in `ReviewPreparationPorts.kt` and `ReviewFactPorts`. Rewrite test doubles as data.

Nullable repositories (F-008): make `UnitOfWork.rejectedOutputDiagnostics` and `rejectedOutputDiagnosticPermissions` non-null. Delete the null branches, including the silent `emptyList()` returns in `GoalPlanningLogService`.

Launch facts (F-009):

- Replace `exitStatus`, `timedOut`, `interrupted`, and `spawnFailed` on `AgentRunLaunchFacts` with one sealed `AgentRunTermination` (`Exited(code)`, `TimedOut`, `Interrupted`, `SpawnFailed`). Drop the `require` checks the type makes redundant.
- `reviewProcessOutcome()` maps each termination to its current `ReviewProcessOutcome`, with the same `stdoutTruncated` precedence as today.
- The launcher adapter computes `stdoutByteSize` and `stdoutSha256` once and passes them in. Keep raw bytes out of data-class equality.
- Persisted and wire values derived from launch facts stay the same.

Remaining defaults (F-004): make abstract `FeatureTaskRuntimeWorkerRepository.releaseFeatureTaskRuntimeWorkerIfExpired`, and `GoalRunnerWorkflowOutcomeMutationStore.authoritativeOutcomes`. Delete `GoalSubtaskPlanRepository.firstMissingPlan` and any other default with no production caller. Keep defaults written only in terms of the interface's own members that hold no policy (for example `DatabaseSessionFactory.readIfPresent`).

More defaults (F-004): make `FeatureTaskRuntimeWorkerSupervisor.inspect` abstract. Delete `GovernedReviewEvidenceEndpointHandle.unbindListener`. `ParallelCodeReviewInlineCoverageContinuation` closes the endpoint it replaces before binding the next one, and its `finally` still closes the last one.

Locator sentinel (F-012): make the `FeatureTaskRuntimeSharedEvidenceLocatorReadPort` parameter nullable where production passes "no reader" (`ReviewPreparationService`, `ParallelReviewPreparationCompiler`). Replace the three `!== NONE` checks with null checks, and delete `NONE`. A caller that needs a reader and has none raises the same `ReviewHunkEvidenceLocatorMissingError` as today.

Decode error (F-013): `GoalPlanningPreparationState.fromWireValue` throws `InvalidGoalPlanningPreparationSchemaError`, or returns null and the caller raises it.

Review facts types: move `ReviewScopeFacts`, `ReviewStackRoutingFacts`, and `ReviewLaneSelection` into runtime-application beside `ReviewPreparationFacts`.

Decision record: add a `runtime-kotlin/agent/decisions.md` entry that supersedes 2026-09-06 (c), with the evidence from investigation F-005.

## Acceptance Criteria

1. `WorkflowStateRepository` has family-keyed members, and runtime-ports main declares no top-level function whose receiver is `WorkflowFamily` and no batch-size constant.
2. `FeatureImplementWorkflowStateRepository`, `FeatureTaskRuntimeWorkflowStateRepository`, `FeatureImplementSessionSummary`, the two `toContract` session-summary mappers, `FeatureImplementSessionSummaryContract`, and `FeatureVerifySessionSummaryContract` do not exist in any source set.
3. Workflow get, list, latest, and continue output from CLI and MCP is byte-identical to baseline, and a batch read above the SQLite bound-parameter limit returns every requested row.
4. `ReviewPreparationPorts.kt` and `ReviewFactPorts` do not exist. Review preparation output for the existing parallel and inline review fixtures is byte-identical to baseline.
5. `UnitOfWork` declares no nullable property, and `GoalPlanningLogService` has no silent empty-list branch for a missing diagnostics repository.
6. `AgentRunLaunchFacts` has a single termination property of a sealed type and no `ByteArray` constructor property. `reviewProcessOutcome` returns the same value as baseline for every existing launcher test case.
7. None of the non-git defaults listed in scope has a default body, and `releaseFeatureTaskRuntimeWorkerIfExpired` releases only through the SQLite fenced path.
8. `unbindListener` does not exist. A test that drives two coverage-continuation passes observes that the first endpoint's channel is closed before the run ends.
9. `FeatureTaskRuntimeSharedEvidenceLocatorReadPort` declares no `NONE`, and no production code compares a locator reader by identity. Review preparation without a locator produces the same output as baseline.
10. An unknown stored goal-planning preparation state raises `InvalidGoalPlanningPreparationSchemaError`, not `IllegalArgumentException`.
11. `runtime-kotlin/agent/decisions.md` has an entry that supersedes decision 2026-09-06 (c) and names the family-keyed repository shape.

## Non-Goals

- Changing the goal-runner store interfaces beyond the one default named in scope (SKILL-376 moves their implementations).
- Changing the SKILL-372 snapshot shape or mapping.
- Changing review verdicts, lane selection, or rubric content.
- Changing launcher process handling, timeouts, or stdout truncation rules.

## Dependency Notes

Depends on subtask 1 for branch order and the shared testFixtures doubles. Requires SKILL-378 subtask 1, which removes the experiment fields from `AgentRunLauncherModels.kt`, the file F-009 edits. Requires SKILL-372 subtask 1, which moves the strict step and artifact decoders into `WorkflowRecordMapping.kt`; keep them when you delete the `toContract` mappers. Also requires SKILL-370, which reworks the application review wiring near `ParallelReviewPreparationCompiler` and renames the package that holds `ParallelCodeReviewInlineCoverageContinuation`.

## Validation Strategy

Run the runtime-ports, runtime-application, runtime-engine, runtime-cli, runtime-mcp, runtime-infra sqlite and launcher test suites and the architecture suite. Capture CLI and MCP workflow JSON and review-preparation fixtures before the change and diff them after. Regressions the tests catch: a batch read losing rows after the batching move, a termination mapping to the wrong review outcome, and a worker-lease release racing a heartbeat. Changed tests go through `bill-unit-test-value-check`. The validate phase runs the routed pack quality gate.

## Next Path

`skill-bill goal SKILL-377`
