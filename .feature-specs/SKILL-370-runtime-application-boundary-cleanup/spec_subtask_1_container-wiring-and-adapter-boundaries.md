# SKILL-370 Subtask 1 - Container wiring and adapter boundaries

Parent spec: [.feature-specs/SKILL-370-runtime-application-boundary-cleanup/spec.md](spec.md)
Issue key: SKILL-370

## Scope

Resolves F-001 through F-005 and F-011 from [investigation.md](investigation.md).

Release catalog (F-011). Add `ReleaseCatalogPort` to `runtime-ports`. It lists releases as typed values (tag name, url, notes, prerelease flag), or returns a typed outcome for transport failure and malformed payload that carries today's reason text. Implement it in `runtime-infra/http` next to `HttpInstallerScriptFetchAdapter`: move the releases URL, the `Accept` and `User-Agent` headers, `JsonCodec.parseJsonArrayStrict`, and the `prerelease`/`tag_name`/`html_url`/`body` mapping out of `UpdateCheckService` into the adapter, which calls `RemoteTransportPort` internally. Bind the adapter in `runtime-core`. `UpdateCheckService` keeps installed-version validation, prerelease filtering, semver selection, and status assembly, and takes `ReleaseCatalogPort` instead of `RemoteTransportPort`. Move the existing update-check tests that feed raw JSON to the adapter's tests. Application tests substitute the new port.

Review wiring (F-001). Delete `ParallelCodeReviewRunnerBoundaries` and `ParallelCodeReviewRunnerComposition`. Annotate `ParallelCodeReviewRunnerPlanning`, `ParallelCodeReviewRunnerRubricPlanning`, `ParallelCodeReviewRunnerLanePlanRecording`, `ParallelCodeReviewRunnerLaneLaunch`, `ParallelCodeReviewRunnerFailureAdmission`, `ParallelCodeReviewRunnerResultAssembly`, and `ParallelCodeReviewRunnerVerificationStages` with `@Inject`, and give each one the ports it reads. Give `RuntimeOwnedPersistenceBoundary` an `@Inject` constructor. It holds only its database and diagnostics ports, so the number of instances has no behavioral effect. `ParallelCodeReviewRunner` takes the collaborators it calls plus `ReviewNativeAgentPreflightPort`. Keep the existing `(String) -> ParallelReviewParseResult` binding. Update `ReviewRecordingHarness`, `ParallelCodeReviewRunnerTest`, and `ParallelCodeReviewRegisterSeamTest` to construct collaborators directly and drop the 19-argument bag.

Install wiring (F-001). Delete `InstallPlanningPorts` and `InstallReconcilePorts`. `InstallService` takes `InstallPlanningFactsPort`, `InstallPlatformSkillMaterializationPort`, `InstallStagingIntentPort`, `InstallReconcilePort`, `InstallReconcileApplyPort`, and `BaselineManifestPersistencePort` directly. `InstallServiceTest` follows.

Guard (F-001, F-002). Extend `InjectConstructorDefaultsArchitectureTest`, reusing its existing constructor walk, with a rule that fails when an `@Inject` class under the runtime-application scan root declares a primary-constructor parameter as a `val` or `var` that is not `private`. The baseline stays empty. Delete the "parallel review composition root owns collaborator wiring" test in `RuntimeLayerBoundaryArchitectureTest`.

Workflow observability (F-002). Remove the public `goalObservabilityEventValidator` property from `WorkflowService`. The results behind `WorkflowCliCommands` and `McpWorkflowRuntime` (the open and get snapshots) carry a typed goal-observability summary, decoded in application with the validator the service already holds. `WorkflowGoalObservabilityCliMapping` and `WorkflowGoalObservabilityMcpMapping` map the summary field instead of calling `goalObservabilityLatestEventFromArtifacts`. MCP keeps adding its progress and attempt-ledger fields. Update the `runtime-core` DI site that reads `validation.goalObservabilityEventValidator` if the change affects it.

SQLite busy retry (F-003). Move busy retry into the SQLite adapter's `selfManagedWrite` implementation, keeping 3 attempts and the same cause-chain detection. Move `SELF_MANAGED_WRITE_BUSY_ATTEMPTS` and `BUSY_TIMEOUT_MILLIS` from `runtime-ports` `ReviewMetricsDatabasePolicy` into `runtime-infra/sqlite`, and delete the ports object if nothing else reads it. `AgentActivityStampWriter` and `WorktreeEditJournalWriter` call `selfManagedWrite`. Delete `idestatus/SelfManagedWriteBusyRetry.kt`.

Diagnostics (F-004). Rename `RejectedOutputDiagnosticCliSession` to `RejectedOutputDiagnosticInspection` and `RejectedOutputDiagnosticCliResult` to `RejectedOutputDiagnosticInspectionResult`. The metadata variant carries typed metadata records instead of preformatted lines, and the records contain no raw content. A raw read that matches more than one diagnostic raises a typed error carrying the match count. `runtime-cli` `RejectedOutputCommands` renders the lines and the `--repair-turn` hint with the same text as today.

Monotonic time (F-005). `AgentActivityStampWriter` takes a `kotlin.time.TimeSource`. `runtime-core` binds `TimeSource.Monotonic`. Replace the two `System.nanoTime()` reads with time marks and keep the debounce semantics.

## Acceptance Criteria

1. `ParallelCodeReviewRunnerBoundaries.kt`, `ParallelCodeReviewRunnerComposition.kt`, `InstallPlanningPorts.kt`, and `InstallReconcilePorts.kt` are deleted, and review collaborators are `@Inject` classes whose constructors list only dependencies they read.
2. `ParallelCodeReviewRunner`'s constructor lists the collaborators it calls.
3. `InstallService`'s constructor lists its six install ports directly.
4. `InjectConstructorDefaultsArchitectureTest` fails for an `@Inject` class in runtime-application with a non-private constructor property, passes on the current tree with an empty baseline, and the composition-root source-shape test is removed.
5. `WorkflowService` has no public dependency property. CLI and MCP workflow open and get output is unchanged, and neither adapter calls `goalObservabilityLatestEventFromArtifacts`.
6. `SelfManagedWriteBusyRetry.kt` is deleted. The SQLite adapter retries busy self-managed writes up to 3 attempts, and runtime-application source contains no `SQLITE_BUSY` or `database is locked` text.
7. runtime-application contains no type or file whose name contains `Cli`. The CLI rejected-output commands print the same metadata lines and ambiguous-selector message as before.
8. `AgentActivityStampWriter` contains no `System.nanoTime()` call and receives an injected `TimeSource`.
9. runtime-application main source contains no `api.github.com` URL, no `application/vnd.github` header, no `tag_name` or `html_url` literal, and no reference to `RemoteTransportPort`. `UpdateCheckService` depends on `ReleaseCatalogPort`, whose production implementation lives in runtime-infra/http.
10. Every existing update-check outcome keeps its status and reason text: up to date, update available, ahead of release, prerelease filtering, malformed payload, malformed entry, transport failure, and unversioned build.
11. Existing review-runner, install, workflow CLI and MCP, rejected-output, and activity-writer tests pass with their expectations unchanged, apart from constructor setup.

## Non-Goals

- Moving `WorkflowService`'s helper classes or `WorkflowEngine` construction into DI.
- Changing review stage order, tiering, persistence, evidence, or verdict behavior.
- New interfaces, parameter objects, or a busy-retry abstraction shared beyond the SQLite adapter.
- Changing engine `[SQLITE_BUSY]` reason-text checks.

## Dependency Notes

Depends on: none
First subtask. It changes constructors and result shapes that subtask 2 later relocates and renames.

## Validation Strategy

Run the runtime-application, runtime-cli, runtime-mcp, runtime-infra sqlite, runtime-infra http, and runtime-core test tasks. Existing entry-point suites are the behavior baseline. Add one SQLite adapter test: a busy failure followed by success returns the result after retry, and a non-busy failure is not retried. That test catches a regression where moving the retry drops it or broadens it. Add one scanner test for the new inject-property rule through its real entry point. Relocated release-catalog adapter tests protect the GitHub mapping. One application test with a substituted port that returns a malformed entry among valid releases catches loss of the malformed-entry reason during the split. The validate phase runs the routed pack gate.

## Next Path

After this commit, continue with subtask 2 through `skill-bill goal SKILL-370`.

## Spec Path

.feature-specs/SKILL-370-runtime-application-boundary-cleanup/spec_subtask_1_container-wiring-and-adapter-boundaries.md
