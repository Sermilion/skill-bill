# Boundary History — runtime-kotlin/runtime-ports

## [2026-09-25] SKILL-377 subtask 1 — git operations port contract
Areas: runtime-ports/workflow/gitops (+model, readiness, worktree, testFixtures), runtime-ports/experiment/navigation, runtime-infra/workflow git adapters, runtime-engine goalrunner and featuretask, runtime-application, runtime-core DI, runtime-cli tests
- `WorkflowGitOperations` is now a pure aggregate: no properties, no sub-port getters, no top-level extension functions with it as receiver, and no default bodies on the eight git capability members. The composition root binds exactly one `WorkflowGitOperations`.
- Pattern: string-encoded port payloads replaced by sealed results — `WorkflowGitNameListResult`, `WorkflowGitCommitResult`, `WorkflowGitIndexSnapshotResult` (value class `WorkflowGitIndexSnapshot`), `WorkflowPathContentIdentitiesResult`, `WorkflowReadinessTreeIdentityResult`. Absence is a `Failed`/`NothingToCommit` variant, never a successful-but-empty value. reusable
- NUL-delimited git output parsing is now adapter-private (`GIT_NUL` in `git/scoped/GitScopedStagingOperations.kt`); no engine or application main source decodes git wire text. Goal-runner finalization reads `WorkflowGitCommitResult.NothingToCommit` instead of matching a marker string.
- Removed: `ReadinessTreeIdentityPayloadCodec`, `recordsNothingToCommit` (adapter-private predicate renamed `reportsGitNothingStaged`), `WorkflowGitOperationResult.fromWire` and its string-status `invoke`.
- Port null-object guards forbid new `Unavailable*`/`Noop*` objects in main source, so the five lost capability default bodies were relocated byte-identically onto `WorkflowGitOperationsTestBase`; `createCommit` and `headCommitSha` stay abstract. reusable
- Deleted the orphaned experiment navigation ports/models and the unreferenced `TestRepositoryFingerprintOperations` once the sub-port getters went; unused-declaration and orphan-model guards would otherwise fail.
- Known limitation: adapter tests exercise each typed result against a real temporary git repository, but byte-identical checkpoint refs, commit messages, pushed branches, and persisted artifacts (AC-007) are asserted by the existing goal-runner/feature-task suites at validate time, not by this phase.
Feature flag: N/A
Acceptance criteria: 7/7 implemented
