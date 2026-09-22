# SKILL-370 Subtask 2 - Ownership moves and namespace hygiene

Parent spec: [.feature-specs/SKILL-370-runtime-application-boundary-cleanup/spec.md](spec.md)
Issue key: SKILL-370

## Scope

Resolves F-006 through F-010 and F-012 from [investigation.md](investigation.md). This subtask is mechanical: it moves, renames, deletes aliases, and narrows visibility, and it changes no behavior.

Domain ownership (F-012). Move the manifest invariants into `runtime-domain` under `skillbill.workflow.decomposition`, with their tests: `withParentStatus` and `intentFor` from `DecompositionManifestRuntimeStateDerivation.kt`, and `withBlockedSubtask` and `withRetriedSubtask` from `DecompositionManifestSubtaskTransitions.kt`. Delete the emptied transitions file. runtime-domain bans every `java.nio` import, so the `Path`-based spec, manifest, and branch layout rules stay in application, together with `withRuntimeFields`, `statusFromUpdate`, `currentSubtaskIdForUpdate`, and the planning builders. Replace restated status tokens in the moved code with `DecompositionStatus.wireValue`. Keep the `CurrentSubtaskIntent.action` strings, which have no owning enum, as they are. Switch the derivation and the engine to the existing domain `normalizedBlockedReason` and delete `telemetry/service/BlockedReasonNormalizer.kt`. Port-driven helpers and `DecompositionManifestWriter` stay in application. Engine callers import the domain functions.

Engine ownership (F-006). Move `idestatus/WorktreeEditJournalWriter.kt` with its test, `agentoutput/AgentOutputJsonScan.kt`, and `stderrExcerpt` from `agentoutput/AgentFailureExcerpt.kt` into `runtime-engine` packages next to their callers. `agentFailureExcerpt` and `isHarnessStatusBanner` stay in application because application review code uses them. Change `runtime-engine/build.gradle.kts` from `api(project(":runtime-application"))` to `implementation(...)`. Update the `runtime-engine` edge in `RuntimeModuleCatalog` and the Gradle Modules section of `runtime-kotlin/ARCHITECTURE.md`.

Test placement (F-007). Move the tests of engine types (`application.work` `IdeStatus*` suites, `IdeStatusServiceTestSupport`, and `idestatus/IdeStatusModelsTest`) into `runtime-engine/src/test` under the package of the code they test. For the remaining application tests that import engine doubles (`WorkflowServiceTest`, `WorkflowIssueKeyPersistenceTest`, `FeatureSpecPreparationRuntimeTest`, `FeatureSpecPreparationWriterTest`, `SpecSourceResolverTest`, `FeatureTaskRuntimeSharedReviewEvidenceResolverTest`), take the accepting validator or similar double from application or domain testFixtures, or move the test to engine when it exercises engine behavior. Remove `testImplementation(project(":runtime-engine"))` and `testImplementation(testFixtures(project(":runtime-engine")))` from `runtime-application/build.gradle.kts`.

Typealiases (F-008). Delete `workflow/model/WorkflowPersistenceModelAliases.kt`, the alias in `workflow/service/WorkflowFamilyKindMapping.kt`, `decomposition/model/DecompositionPersistenceModelAliases.kt`, `uninstall/UninstallModels.kt`, `continuation/GoalContinuationCandidate.kt`, `reviewevidence/ReviewEvidenceScopeModels.kt`, and `review/model/CodeReviewExecutionMode.kt`. Point every import in application, engine, CLI, MCP, core, and their tests at the owning type, for example `skillbill.ports.workflow.model.WorkflowFamily`. Where an alias hid an import-alias rename such as `ModelUninstallRequest`, keep the owning type's real name.

Packages (F-009). Replace `review/parallel/core/code/review/{runner,evidence,inline,pass}` with `review/parallel/runner`. The `runner/model` package disappears once subtask 1 deletes its only file. Keep `review/parallel/planning` and `review/parallel/verification`. Rename `review/review` to `review/snapshot`. Move the 21 orphan-package test files into their main-source package, or the area package for cross-cutting suites: `featurespec`, `specsource`, `evidence`, `workflow.workflow`, `telemetry.telemetry`, `review.parallel.core.review`, and `review.parallel.core.code.review.{bundled,claim,end,integration,regression,spec,stage,standalone}`. Respect `PackageSiblingCountArchitectureTest` limits (12 non-model, 20 model). Update path-bearing architecture tests and docs that name the old packages.

Visibility (F-010). Make every runtime-application top-level declaration that has no reference outside the module `internal`, or `private` when a single file uses it, including the three `WorkflowService` helper classes. Delete any declaration left with no reference. Move `GoalLifecycleTelemetryEmitter.NONE` into application testFixtures and update its test users. Rely on the compiler to confirm references, not on the investigation's name census.

Record the ownership changes in `runtime-kotlin/runtime-application/agent/history.md`.

## Acceptance Criteria

1. `WorktreeEditJournalWriter`, `topLevelJsonObjectCandidates`, and `stderrExcerpt` are declared in runtime-engine, and runtime-application main no longer declares them.
2. `runtime-engine/build.gradle.kts` declares runtime-application with `implementation`, and `RuntimeModuleCatalog` and `ARCHITECTURE.md` match.
3. `runtime-application/build.gradle.kts` has no dependency on runtime-engine or its testFixtures in any configuration, and no runtime-application test source imports `skillbill.engine`.
4. runtime-application main source contains no `typealias` declaration, and no module imports one of the deleted alias names from an application package.
5. No runtime-application main package path contains `core/code/review` or `review/review`, and every runtime-application test file's package exists in main or is the area package of the suite.
6. Every runtime-application top-level declaration without a cross-module reference is `internal` or `private`, and `GoalLifecycleTelemetryEmitter` declares no `NONE` member in main source.
7. `withParentStatus`, `intentFor`, `withBlockedSubtask`, and `withRetriedSubtask` are declared in runtime-domain `skillbill.workflow.decomposition` without any `skillbill.ports` or `java.nio` import. runtime-application declares none of them and declares no `normalizedBlockedReason`.
8. The moved tests keep their test names and assertions and pass in their new module.

## Non-Goals

- Behavior, wire, schema, or contract-version changes.
- Typealias or package cleanup outside runtime-application.
- Enabling `explicitApi()` or adding new visibility scanners.
- Moving the `reviewevidence` diff parser into runtime-domain.
- Typing manifest `status` or intent `action` as enums.
- Migrating raw `artifacts[KEY]` reads to domain accessors, which SKILL-372 owns. The moved derivation keeps its current artifact reads.

## Dependency Notes

Depends on: subtask 1
Subtask 1 deletes and reshapes review and install wiring files. Relocating them first would move code that subtask 1 removes.

## Validation Strategy

Run the runtime-domain, runtime-application, runtime-engine, runtime-cli, runtime-mcp, and runtime-core test tasks. The test count summed across domain, application, and engine must equal the pre-move total except for tests of symbols this subtask deletes, and each deletion is named in the history entry. This catches a test lost during relocation. Run the package-sibling, package-cycle, module-layering, spillover-name, and documentation architecture tests. The validate phase runs the routed pack gate.

## Next Path

After this commit, the goal is complete. Continue through the goal runtime completion path.

## Spec Path

.feature-specs/SKILL-370-runtime-application-boundary-cleanup/spec_subtask_2_ownership-moves-and-namespace-hygiene.md
