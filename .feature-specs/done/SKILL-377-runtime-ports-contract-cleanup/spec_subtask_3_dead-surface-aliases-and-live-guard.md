# SKILL-377 subtask 3 - Dead surface, aliases, and live ports guard

Parent: [spec.md](spec.md). Findings: F-001, F-007, F-010, F-011, the test-only part of F-012, and the F-004 guard rule in [investigation.md](investigation.md).

## Scope

Delete dead surface (F-007):

- Ports never injected, with their runtime-core bindings: `ScaffoldGeneratedStagingPort`, `ScaffoldInstallLinkPort`, `ScaffoldManifestPersistencePort`, `ScaffoldSourceLoaderPort`, `ScaffoldRepoValidationPort`, `DeclaredReviewSpecialistsPort`, and `ReviewLaunchIsolationResolver`.
- `InstalledWorkspaceBaselineStatusPort` and its binding, plus the `RuntimeComponent.installedWorkspaceBaselineStatusPort` accessor and its `PrincipleEnforcementInventory` entry if SKILL-373 subtask 1 has not already removed them.
- Dead adapters and their tests: `FileSystemScaffoldGeneratedStaging`, `FileSystemScaffoldInstallLink`, `FileSystemScaffoldManifestPersistence`, `FileSystemDeclaredReviewSpecialists`, `AgentRunReviewIsolationResolver`, and `FileSystemInstalledWorkspaceBaselineStatus`. `FileSystemScaffoldSourceLoader` and `FileSystemScaffoldRepoValidation` stay as concrete classes that `FileSystemScaffoldOrchestrator` uses. SKILL-376 subtask 3 moves `FileSystemScaffoldInstallLink`, `FileSystemScaffoldGateway`, and `FileSystemScaffoldOrchestrator` to `skills.install.scaffold`, so delete the install-link adapter at whichever path it has. Its only references are its own file, the `RuntimeScaffoldProvides` binding, and the port.
- Dead declarations: `ports/featuretask/model/FeatureTaskRuntimeProcessInspection.kt`, `GoalRunnerChildRepairApplyStateInit`, `FeatureTaskRuntimeSnapshot`, `NativeAgentSourceProjection`, and `NormalizedGoalPlanningPreparationRepository`.
- Delete the five `LegacyGoalPlanningPreparationRepository` members that no application or engine code calls (`findByGoalAndSubtask`, `listPreparedByGoalOrdered`, `preparedCount`, `firstMissingOrIncompleteSubtask`, `preparedStatus`) from the port, along with their SQLite overrides. SQLite's internal record helpers stay only if a remaining SQLite test needs them.
- Move `LEGACY_FEATURE_TASK_PROSE_WORKFLOW_STATUSES` into `SQLiteWorkListRepository`'s package, and `RepoValidationIssue.fromRawIssue` into `infra/skills` `RepoValidationRuntime`. These are their only callers, and both import only domain and ports types.
- Test-only `NONE` substitutes (F-012):
  - Move into runtime-ports testFixtures: `FeatureTaskRuntimeSharedEvidenceResolverPort.NONE`, `GoalPlanningContextDiscovery.NONE` (with its private `EMPTY*` helpers), `ReviewNativeAgentPreflightPort.NONE`, and `ReviewLaunchAgentStagingPort.NONE`.
  - Delete `GoalPlanningBoundaryBodyResolver.NONE` and `InstalledPlatformPackCatalogPort.NONE`. `DeclaredReviewSpecialistsPort.NONE` goes with its port.
  - Extend the companion-`val` rule that SKILL-378 subtask 1 adds to `PortNullObjectCensus` (scoped there to runtime-engine main) to runtime-ports main as well.
- Move the two orphan test files (`test/…/ports/architecture/PortsFooMapRawMapFixtureTest.kt`, `testFixtures/…/ports/review/empty/EmptyReviewAttributionPort.kt`) into packages that exist in main.
- Remove `implementation(libs.kotlinx.serialization.json)` from `runtime-ports/build.gradle.kts`.

Aliases and untyped members (F-010):

- Delete every typealias still in runtime-ports, including experiment and linked-worktree aliases if they are still present. Rename each target type to the alias name if the target exists only to be aliased.
- Replace `Any` in `recoverMissingResultPrefixOutput(output)`, `HistoryArtifactAppend.entryMap`, the goal-runner reconcile-request `artifacts`, and `GoalChildPlanningHydrationResult.stepUpdates`/`artifacts` with the concrete types their producers pass. Use `DurableWorkflowArtifacts` and `WorkflowStepState` when those types exist. Otherwise use the concrete types the producers pass today. Check whether `stepUpdates` carries states or update maps before choosing.

Live guard (F-001, F-004):

- Resolve the scan roots in `PortsDeclarationArchitectureTest` and in the testFixtures case of `PortNullObjectAbsenceArchitectureTest` so a named root that does not exist fails and each named root contributes at least one file. Edit the tests where they live.
- Both tests fail when a root is missing and assert that they read at least one Kotlin file.
- The first case of `PortNullObjectAbsenceArchitectureTest` maps `runtime-infra:<name>` module ids to their directories, so each infrastructure module's main source is scanned.
- Extend `PortNullObjectCensus` to flag `class` declarations with the same name prefixes, and companion `val` declarations when those are the form that still passes the census.
- Extend the existing interface-default check so that, outside `fun interface` declarations, a default body that is a bare constant result fails. Constant results are `true`, `false`, `null`, `Unit`, empty collection constructors, and `WorkflowGitOperationResult.Ok`/`Failed`. The baseline stays empty.
- Fix every violation the live guard reports. Expected: `IdeStatusProblemDetails` becomes a `@JvmInline value class` that keeps its private constructor, which avoids exposing a public raw map. Subtask 2 has already deleted `unbindListener`. If `ReviewMetricsDatabasePolicy` is still present and the guard reports it, delete it here.

Documentation (F-011): update the `runtime-ports` Gradle Modules entry and the `FileLocation` sentence in `../../../runtime-kotlin/ARCHITECTURE.md` to the landed state (`Path` is the port path type; `FileLocation` is a domain value with two bridges). Record the default-body rule and the flat git aggregate in `runtime-kotlin/agent/decisions.md`.

## Acceptance Criteria

1. None of the ports, bindings, component accessor, adapters, or declarations listed in scope exists in any source set, and `runtime-ports/build.gradle.kts` declares no kotlinx.serialization dependency.
2. The five unused legacy planning members, the legacy prose-status constant, and `fromRawIssue` are not declared in runtime-ports. No `NONE` companion in runtime-ports main lacks a main-source reference, and no `NONE` companion in runtime-ports main is referenced only by tests.
3. runtime-ports main declares no `typealias`, and no port interface member or port model property has type `Any` or a collection of `Any`.
4. `PortsDeclarationArchitectureTest` reads every Kotlin file under runtime-ports main, fails on a missing root, and reports no violations on the tree.
5. Fixtures prove that the extended interface-default check rejects a `true`-returning default and a `WorkflowGitOperationResult.Ok` default in a non-`fun` interface, and accepts a derived default and a `fun interface` default.
6. Every case of `PortNullObjectAbsenceArchitectureTest` reads files from each root it names, including all seven `runtime-infra` modules and runtime-ports testFixtures, and fails on a missing root. A fixture proves the census flags a `class Noop…` declaration.
7. Every runtime-ports test and testFixtures file sits in a package that exists in runtime-ports main.
8. `../../../runtime-kotlin/ARCHITECTURE.md` describes `Path` as the port path type and lists no removed ports item in the runtime-ports module entry.

## Non-Goals

- New architecture-test classes, or rule changes beyond the interface-default extension and `class` detection in the null-object census.
- Repackaging for granularity, or visibility narrowing across ports.
- Anything under experiment support (SKILL-378).
- Typealias cleanup outside runtime-ports.

## Dependency Notes

Depends on subtask 2. The live guard must find the defaults and codec that subtasks 1 and 2 remove already gone. It does not wait for another issue. Apply the scan-root convention in this subtask if the ports walkers still scan nothing. Edit the tests where they live. Delete experiment aliases that are still present. If `ReviewMetricsDatabasePolicy` is still present and the guard reports it, delete it here. Do not stop for another issue.

## Validation Strategy

Run the runtime-ports, runtime-infra skills, launcher, workflow, and sqlite test suites, runtime-core component tests, and the full architecture suite. Record how many files each repaired guard reads before (zero) and after. Regressions the guard tests catch: a missing scan root passing silently, and a constant-result default re-entering a port. Changed tests go through `bill-unit-test-value-check`. The validate phase runs the routed pack quality gate.

## Next Path

`skill-bill goal SKILL-377`
