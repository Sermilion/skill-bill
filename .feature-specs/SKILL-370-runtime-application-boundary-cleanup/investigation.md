# runtime-application architecture investigation (SKILL-370)

## Judgment

`runtime-application` has the right shape at the module level. Production code depends only on `runtime-contracts`, `runtime-domain`, and `runtime-ports`. It has no package cycles. Ambient-clock and ambient-environment baselines are empty. Infrastructure leakage is limited to `java.nio.file.Path` used as a value type. I would keep the module graph, the ports, and the area packages.

The remaining problems are in wiring, ownership, and namespace hygiene:

1. The module still contains a hand-written dependency graph. `ParallelCodeReviewRunnerComposition` builds the review collaborators from a 19-field `@Inject data class` bag. SKILL-347 subtask 3 set out to remove this pattern ("No replacement dependency bag"). The bag was moved instead of removed, and a source-shape architecture test now requires it to exist.
2. Adapter and entry-point details sit inside use cases. The module matches SQLite error strings, names a use case `...CliSession` and puts CLI flag text in its errors, and exposes a validator getter that CLI and MCP use to decode domain artifacts twice.
3. Code and tests sit in the wrong module. Engine-only helpers live in application. Fourteen application test files exercise `runtime-engine` types, which means application tests depend on the module above them.
4. Two behaviors belong to other layers. `UpdateCheckService` is a GitHub REST client that uses a generic HTTP port: it holds the URL, headers, and JSON field names. The decomposition manifest's invariants (parent-status and intent derivation, blocked and retry transitions) live in application, while domain holds only the manifest's data, validator, and codec. The engine imports them, along with other pure helpers, from application.
5. The namespace has accumulated indirection: 20 typealiases that re-export port and domain types under application names, stutter packages such as `review.parallel.core.code.review.runner`, 21 test files in packages that don't exist in main, and 88 public symbols that nothing outside the module uses.

None of these is a correctness defect. Each one makes the next change harder: a reader has to follow an alias, a bag, or a composition class to find the real dependency. The fix removes those layers and adds one purpose-built port, `ReleaseCatalogPort` (F-011). It adds no new modules or frameworks.

## Scope and method

- Baseline: commit `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b` on `feat/SKILL-368-build-logic-architecture-cleanup`, 2026-09-22.
- Production: 179 Kotlin files, 18,208 lines, including imports and blank lines. That is 6 files and 1,615 lines more than the SKILL-347 census at `2ef12ad7`.
- Tests: 79 files and 25,680 lines under `src/test`, plus 17 files and 1,686 lines under `src/testFixtures`.
- Method: I ran grep and Python censuses across every `runtime-*` module for imports, top-level declarations, cross-module symbol references, visibility, typealiases, constructor-exposed properties, and test and main package alignment. I read in full every file a finding cites. I did not delegate a line-level code review. The deliverable covers structure and boundaries.
- Prior work: SKILL-347 (runtime-application ownership, 2026-09-16) fixed endpoint lifetime, cancellation propagation, activity-state ownership, and projection identity. This investigation does not revisit those fixes. It keeps SKILL-347's retention decisions (see "What stays").

## Module position

```text
runtime-cli, runtime-mcp ──► runtime-application ──► runtime-ports ──► runtime-domain ──► runtime-contracts
                                   ▲
runtime-engine ─── api ────────────┘   (74 of 335 engine main files import application; 56 distinct symbols)
runtime-core (composition) ──► application, engine, ports, infra adapters
```

| Module | Main files | Main lines |
| --- | ---: | ---: |
| runtime-engine | 335 | 51,116 |
| runtime-domain | 349 | 30,385 |
| runtime-application | 179 | 18,208 |
| runtime-cli | 113 | 11,705 |
| runtime-ports | 254 | 7,351 |
| runtime-contracts | 112 | 4,691 |
| runtime-mcp | 30 | 2,994 |
| runtime-core | 25 | 1,322 |

Application symbols imported from other modules' main code:

| Consumer | Distinct application symbols | Main packages used most |
| --- | ---: | --- |
| runtime-cli | 90 | review.model, workflow.model, install, scaffold, learning.model |
| runtime-engine | 56 | workflow.model (26 imports), review.model, telemetry.model, decomposition helpers |
| runtime-mcp | 41 | workflow.model, telemetry.model, review.model |
| runtime-core | 23 | services for DI binding |

Engine sits on top of application as a second use-case layer. It owns the long-running run loop, goal runner, and planning. Application owns standalone use cases and the shared decomposition, workflow, telemetry, and review services the engine calls. The edge has no cycle and is documented. Merging the two modules or inverting the edge would cost far more than it returns. This investigation keeps the edge. It narrows its Gradle declaration from `api` to `implementation` and moves the engine-only code across it.

## Principle assessment

| Principle | Assessment | Evidence |
| --- | --- | --- |
| Dependency rule / hexagonal | Holds at module level; leaks at four seams | Production deps inward only; empty cycle baseline. Leaks: SQLite error sniffing (F-003), CLI-shaped use case (F-004), adapters decoding domain artifacts through a service getter (F-002), GitHub REST protocol in a use case (F-011). |
| Rich domain vs anemic domain | Decomposition manifest is anemic | Domain owns the manifest data, validator, and codec. Application owns its invariants (parent status, intent, blocked and retry transitions), and the engine reaches them through application (F-012). The `Path`-based layout rules stay in application because domain bans `java.nio`. |
| Dependency inversion / DI | Violated inside review | `ParallelCodeReviewRunnerComposition` is a second composition root inside application (F-001). The DI container can wire these classes directly. |
| Interface segregation | Real ports justified | `TelemetrySettingsProvider` has 10 test substitutes, `GoalLifecycleTelemetryEmitter` has 9, and `GoalRunnerSubtaskLauncher` has 12. Keep them. |
| SRP / cohesion | Good by area; engine-only code misplaced | Engine-only writers and scanners live in application (F-006). IdeStatus tests live in application but exercise `skillbill.engine.work` (F-007). |
| YAGNI | Pass-through indirection | 20 re-export typealiases (F-008), `GoalLifecycleTelemetryEmitter.NONE` has no main caller, 88 public module-only symbols (F-010). |
| Naming and packages | Stutter and orphans | `review.parallel.core.code.review.*`, `review.review`, and 21 test files in packages absent from main (F-009). |
| Test placement | Upward test dependency | `runtime-application` `testImplementation(project(":runtime-engine"))` and engine testFixtures (F-007). |

Common practice in large Kotlin codebases that use Dagger, Hilt, Anvil, or kotlin-inject points the same way. Each class declares the dependencies it uses through constructor injection, and the container wires the graph. Types are `internal` unless another module consumes them. Code imports types from the package that owns them, without module-local aliases. Tests live in the module that owns the code under test. The findings below are the places where this module departs from those norms.

## Findings

Priority reflects change cost and boundary damage. None is a production incident.

### F-001. High. A second composition root and a 19-field dependency bag inside application

Evidence:

- `review/parallel/core/code/review/runner/model/ParallelCodeReviewRunnerBoundaries.kt`: an `@Inject data class` with 19 public fields. It holds 17 ports and services, a `(String) -> ParallelReviewParseResult` function, and a `Clock`.
- `review/parallel/core/code/review/runner/ParallelCodeReviewRunnerComposition.kt` (64 lines): an `@Inject` class that takes the bag and constructs `RuntimeOwnedPersistenceBoundary`, `ParallelCodeReviewRunnerFailureAdmission`, `ParallelCodeReviewRunnerRubricPlanning`, `ParallelCodeReviewRunnerPlanning`, `ParallelCodeReviewRunnerLanePlanRecording`, `ParallelCodeReviewRunnerLaneLaunch`, `ParallelCodeReviewRunnerResultAssembly`, and `ParallelCodeReviewRunnerVerificationStages` by hand. `ParallelCodeReviewRunner` then reads six `internal val`s off it.
- `install/InstallPlanningPorts.kt` and `install/InstallReconcilePorts.kt`: `@Inject` classes whose only content is three public port fields each. `InstallService` reads through them (`planningPorts.planningFactsPort...`).
- `runtime-core/src/test/kotlin/skillbill/architecture/RuntimeLayerBoundaryArchitectureTest.kt:469-500` ("parallel review composition root owns collaborator wiring") requires the composition and boundaries files to exist.
- SKILL-347 subtask 3 acceptance criteria 1-2 asked for the runner to take executable collaborators from the existing composition root, with "No replacement dependency bag". The landed code created one bag and one composition class.

Why it matters: kotlin-inject already builds graphs. A hand-written sub-graph hides which collaborator uses which port. Any new review dependency passes through three places (bag, composition, collaborator). The `RuntimeCompositionGuardArchitectureTest` census only covers types that appear in `@Provides`, so it doesn't see these constructions. Tests build the bag with 19 arguments even when they exercise a single stage.

Fix: make each review collaborator an `@Inject` class that takes only the ports it reads. `ParallelCodeReviewRunner` takes the collaborators it calls. Delete `ParallelCodeReviewRunnerBoundaries`, `ParallelCodeReviewRunnerComposition`, `InstallPlanningPorts`, and `InstallReconcilePorts`. `runtime-core` keeps its existing `(String) -> ParallelReviewParseResult` binding. Replace the source-shape test with a general rule in the existing `InjectConstructorDefaultsArchitectureTest` scanner: an `@Inject` class in `runtime-application` does not expose constructor parameters as non-private properties. The rule catches both dependency bags and dependency getters (F-002), and its baseline is empty.

### F-002. Medium. `WorkflowService` exposes a dependency so adapters can decode domain artifacts

Evidence:

- `workflow/service/WorkflowService.kt:74` declares `val goalObservabilityEventValidator` as a public constructor property.
- `runtime-cli/.../workflow/WorkflowCliCommands.kt:93,160` and `runtime-mcp/.../workflow/McpWorkflowRuntime.kt:93,115` read it and pass it into `toCliMap` and `toMcpMap`.
- `runtime-cli/.../WorkflowGoalObservabilityCliMapping.kt` and `runtime-mcp/.../WorkflowGoalObservabilityMcpMapping.kt` each call `goalObservabilityLatestEventFromArtifacts(artifacts, validator)`. That call validates and decodes a domain artifact. The two files differ only in MCP's extra progress and ledger fields.

`runtime-kotlin/ARCHITECTURE.md` says a port "describes operations its consumer needs, not getters for another object's entire dependency graph". The same principle applies to services. Validating a durable artifact is application work. Entry adapters should only map results.

Fix: the workflow get and open results carry the decoded goal-observability summary as a typed field, computed in application with the validator it already holds. CLI and MCP map that field. Remove the public property. The wire output stays byte-identical.

### F-003. Medium. SQLite error strings are matched in application

Evidence:

- `idestatus/SelfManagedWriteBusyRetry.kt` (36 lines) retries `selfManagedWrite` when an exception message in the cause chain contains `SQLITE_BUSY` or `database is locked`.
- The attempt count comes from `runtime-ports/.../db/ReviewMetricsDatabasePolicy.SELF_MANAGED_WRITE_BUSY_ATTEMPTS`. That object's other constant, `BUSY_TIMEOUT_MILLIS`, is read only by `runtime-infra/sqlite/.../DatabaseRuntime.kt:177`.
- Callers: `AgentActivityStampWriter` and `WorktreeEditJournalWriter`.

The application layer depends on a driver's message text. That text is an adapter detail and can change with the JDBC driver version.

Fix: the SQLite adapter owns busy handling. `selfManagedWrite` in `runtime-infra/sqlite` retries busy failures with the same attempt count. Application calls plain `selfManagedWrite`. Delete `SelfManagedWriteBusyRetry.kt` and move both policy constants into the SQLite module. No new exception type or port is needed. The engine's `[SQLITE_BUSY]` checks in `FeatureTaskRuntimeRunLoopPhaseRunner` inspect persisted block-reason text, so they are out of scope.

### F-004. Low. A use case named and worded for the CLI

Evidence: `diagnostics/RejectedOutputDiagnosticCliSession.kt` and `diagnostics/model/RejectedOutputDiagnosticCliResult.kt`. The session formats each diagnostic into a display line (`safeLine`) and throws a retrieval error that tells the user to "add --repair-turn". Its only caller is `runtime-cli/.../RejectedOutputCommands.kt`.

Fix: rename the class to a transport-neutral name and return the typed metadata records and raw bytes. Return a typed ambiguous-selector error that carries the match count. The CLI renders the lines and the flag hint. The privacy filtering that `safeLine` applies stays in application: the returned records contain metadata only, never raw content.

### F-005. Low. Ambient monotonic time in `AgentActivityStampWriter`

Evidence: `idestatus/AgentActivityStampWriter.kt:77,92` call `System.nanoTime()` next to an injected `Clock`. The ambient-clock guard bans `Instant.now()` and similar calls but not `nanoTime`. As a result, tests can't drive the non-evidence debounce window.

Fix: inject `kotlin.time.TimeSource` (bound to `TimeSource.Monotonic` in `runtime-core`) and use its marks. Behavior stays the same.

### F-006. Medium. Engine-only code lives in application, and the engine edge is `api`

Evidence from the cross-module symbol census (only the engine references these outside their own file):

- `idestatus/WorktreeEditJournalWriter.kt` (152 lines). Consumers are `FeatureTaskRuntimeRunLoop`, `FeatureTaskRuntimeProbeWriters`, and `GoalRunnerLaunchReconciler`. Its only application reference is its own test.
- `agentoutput/AgentOutputJsonScan.kt` (`topLevelJsonObjectCandidates`, 31 lines) and `stderrExcerpt` in `agentoutput/AgentFailureExcerpt.kt`. `agentFailureExcerpt` in the same file is also used by application review code, so it stays.
- `runtime-engine/build.gradle.kts` declares `api(project(":runtime-application"))`. All engine consumers (`runtime-core`, `runtime-cli`, `runtime-mcp`) already declare application directly. `RuntimeModuleCatalog` pins the edge as `api`.

Fix: move the engine-only code into `runtime-engine` next to its callers, together with its tests. Change the edge to `implementation` and update `RuntimeModuleCatalog` and the `ARCHITECTURE.md` Gradle Modules text.

### F-007. Medium. Application tests exercise engine code

Evidence: 14 files under `runtime-application/src/test` import `skillbill.engine.*`. Seven in package `skillbill.application.work` (`IdeStatusService*`, `IdeStatusSelectionPolicyTest`, `IdeStatusFreshnessTest`, `IdeStatusTimestampTest`) and `idestatus/IdeStatusModelsTest` test engine types only: `skillbill.engine.work.*`, with zero or one application import. `WorkflowServiceTest`, `WorkflowIssueKeyPersistenceTest`, `FeatureSpecPreparationRuntimeTest`, `FeatureSpecPreparationWriterTest`, `SpecSourceResolverTest`, and `FeatureTaskRuntimeSharedReviewEvidenceResolverTest` use engine test doubles such as `AcceptingFeatureTaskRuntimeWireArtifactValidator`. The build file carries `testImplementation(project(":runtime-engine"))` and `testImplementation(testFixtures(project(":runtime-engine")))`.

Fix: move tests of engine types into `runtime-engine/src/test`. For application tests that only need an accepting validator, use a double from application or domain testFixtures. Delete both upward test edges. Then application's own test classpath no longer contains the module that depends on it.

### F-008. Medium. Twenty pass-through typealiases

Evidence: 20 `typealias` declarations in 8 files rename port or domain types into application packages:

- `workflow/model/WorkflowPersistenceModelAliases.kt`: `WorkflowFamily` and three goal-observability inputs.
- `workflow/service/WorkflowFamilyKindMapping.kt`: a second `WorkflowFamily` alias for the same port type.
- `decomposition/model/DecompositionPersistenceModelAliases.kt`: six port types.
- `uninstall/UninstallModels.kt`: five aliases for `uninstall/model/UninstallModels.kt`.
- `continuation/GoalContinuationCandidate.kt`: an alias for `continuation/model/GoalContinuationCandidate.kt`.
- `reviewevidence/ReviewEvidenceScopeModels.kt`: two aliases for `reviewevidence/model/ReviewEvidenceScopeModels.kt`.
- `review/model/CodeReviewExecutionMode.kt`: one domain alias.

The engine imports `skillbill.application.workflow.model.WorkflowFamily` in 26 main files. That type is actually `skillbill.ports.workflow.model.WorkflowFamily`. The alias makes the engine look more coupled to application than it is, and readers see two names for one type.

Fix: delete the alias files and import the owning types directly in every module. Rename the four alias-plus-model duplicate file pairs so each type has one file.

### F-009. Low. Package stutter and orphan test packages

Main-source evidence:

- `review/parallel/core/code/review/{evidence,inline,pass,runner,runner/model}`: 5 packages and 11 files beneath `review/parallel`, next to `review/parallel/{planning,verification}`. The path segment `core/code/review` adds no information.
- `review/review/ReviewSnapshotPruneService.kt`: a package named after its parent.

Test-source evidence: 21 test files sit in packages that don't exist in main. These are `application.featurespec`, `application.specsource`, `application.evidence` (3), `application.workflow.workflow`, `application.telemetry.telemetry` (2), `application.review.parallel.core.review` (5), and eight single-file `application.review.parallel.core.code.review.{bundled,claim,end,integration,regression,spec,stage,standalone}` packages.

Fix: flatten to `review/parallel/{runner,planning,verification}` with the evidence, inline, and pass files placed under `runner` and the runner models under `review/model`. Rename `review/review` to `review/snapshot`. Move test files to their main-source package, or to the area package for cross-cutting suites. Respect the existing 12-sibling and 20-model package limits.

### F-010. Low. Public by default

Evidence: 432 non-private top-level declarations. 224 are referenced from another module and 208 only from inside `runtime-application`. Of those 208, 120 are already `internal` and 88 are public. `GoalLifecycleTelemetryEmitter.NONE` has no main-source caller; all 9 users are tests.

Fix: make the 88 module-only declarations `internal`, deleting any that have no remaining reference. Move `NONE` into a test fixture. Don't enable Kotlin `explicitApi()`: a one-time narrowing plus review is enough for an application module that nothing publishes.

### F-011. Medium. A GitHub REST client inside a use case

Evidence: `updatecheck/UpdateCheckService.kt`.

- Line 193 holds `RELEASES_URL = "https://api.github.com/repos/Sermilion/skill-bill/releases"`.
- Lines 82-85 build `GET` with `Accept: application/vnd.github+json` and a user agent.
- Line 111 calls `JsonCodec.parseJsonArrayStrict(response.body)`.
- Lines 147-159 read the GitHub fields `prerelease`, `tag_name`, `html_url`, and `body` with `as?` casts.

The request goes through `RemoteTransportPort`, a generic `(method, url, bodyJson, headers)` port declared under `ports.telemetry.transport`. This is the only application use of that port. Telemetry goes through `HttpTelemetryClient`, and the sibling `SkillBillUpdateService` uses a purpose-built `InstallerScriptFetchPort`. Update check is the outlier: the protocol, the vendor schema, and the anti-corruption mapping all sit in application.

Fix: add `ReleaseCatalogPort` to `runtime-ports`. It returns typed releases (tag, url, notes, prerelease) or a typed unavailable or malformed outcome. Implement it in `runtime-infra/http` next to `HttpInstallerScriptFetchAdapter`, reusing `RemoteTransportPort` internally. `UpdateCheckService` keeps the application policy: installed-version checks, prerelease filtering, semver selection, and status. Its `UpdateCheckResult` values and messages stay identical. Application stops depending on `RemoteTransportPort`.

### F-012. Medium. The decomposition manifest's rules live in application

Evidence:

- `decomposition/DecompositionManifestRuntimeStateDerivation.kt` (225 lines) holds `withRuntimeFields`, `currentSubtaskIdForUpdate`, `statusFromUpdate`, `intentFor`, and `withParentStatus`. `withParentStatus` derives the parent status from the subtasks. That derivation is the aggregate's invariant.
- `decomposition/DecompositionManifestSubtaskTransitions.kt` (53 lines) holds `withBlockedSubtask` and `withRetriedSubtask`.
- `decomposition/DecompositionManifestWriterPlan.kt`, `DecompositionManifestPaths.kt`, `DecompositionManifestWriterPaths.kt`, and `DecompositionManifestBranchNames.kt` hold spec-path, manifest-path, and default-branch layout rules plus planning-result builders.
- All of these functions are pure: they take no ports and do no I/O. Domain `skillbill.workflow.decomposition` holds only the models, validator, wire codec, and continuation selector.
- The engine imports 21 top-level application functions. 13 are pure rules: `withParentStatus`, `decompositionManifestPath`, `parentSpecPath`, `resolvedParentSpecPath`, `repoRelativePath`, `defaultFeatureBranch`, `decompositionPlanningResult`, `decompositionPlanningSubtask`, `normalizedBlockedReason`, `agentFailureExcerpt`, `stderrExcerpt`, `topLevelJsonObjectCandidates`, and `toProjectionPayload`.
- `telemetry/service/BlockedReasonNormalizer.kt` duplicates `runtime-domain/.../workflow/decomposition/runtime/BlockedReasonNormalizer.kt` line for line.

Of the 13 pure functions, eight are decomposition functions. Domain bans every `java.nio` import (`RuntimeContractModuleImportRulesTest`), so only the aggregate invariants that need neither `Path` nor port types can move: parent-status derivation, intent derivation, and the blocked and retry transitions. The path and branch layout rules, `parseSubtasks`, `parentSpecPath`, and `defaultFeatureBranch` use `Path`. They are filesystem-layout rules and stay in application. So do `withRuntimeFields`, `statusFromUpdate`, and `currentSubtaskIdForUpdate`, which consume the port-owned `DecompositionManifestRuntimeUpdate` and match subtasks by `Path`, and the planning builders, which map application option models onto the contracts wire type. `agentFailureExcerpt`, `stderrExcerpt`, and `topLevelJsonObjectCandidates` are handled by F-006. `toProjectionPayload` is review wire mapping and stays in application.

In a hexagonal design, the domain aggregate owns its state transitions, and application sequences I/O around them. Here the rules sit one layer too high, so the engine depends on application for pure domain behavior.

Fix: move `withParentStatus`, `intentFor`, `withBlockedSubtask`, and `withRetriedSubtask` into `runtime-domain` under `skillbill.workflow.decomposition`, next to the model. Use `DecompositionStatus.wireValue` where the moved code restates status tokens. Keep the `Path`-based layout rules, the update derivation, the planning builders, and the port-driven functions in application (`loadManifestOrNull`, `resolveDecompositionManifest`, `loadValidatedDecompositionManifestPersistingRepair`, `encodeValidatedDecompositionManifestYaml`, `decompositionRuntime`, `goalContinuationFor`, `updateGoalParentForBlockedPhaseRetry`, `generateWorkflowId`, and the `DecompositionManifestWriter` class). Delete the application copy of `normalizedBlockedReason`. Don't change the manifest's field types. Typing `status` and `action` as enums would ripple through the wire codec, SQLite, and every consumer, so it belongs in its own spec.

## Coordination with concurrent bundles

Four sibling bundles were prepared the same day and touch runtime-application or its seams. All four sequence after SKILL-370.

- **SKILL-371 (runtime-cli).** It sequences itself after this bundle. Its subtask 1 restores architecture scanners that resolve module paths against the repository root and scan nothing. It also fixes the public raw-map function `review/stats/ReviewAccountingOutput.kt:5` that those scanners missed, so this bundle leaves that function alone. I checked the three guards this investigation relies on (package acyclicity, ambient clock, inject-constructor defaults). All three resolve `runtime-kotlin/runtime-application/src/main/kotlin` through `PrincipleEnforcementInventory.RUNTIME_APPLICATION_MAIN` and read real files. SKILL-371 subtask 3 adds a scaffold payload decoder to application. That doesn't conflict with this bundle.
- **SKILL-372 (runtime-domain).** It migrates raw `artifacts[KEY]` reads, including the literal-key reads in `DecompositionManifestRuntimeStateDerivation.kt`. It moves several pure rules into domain, deletes application `normalizedBlockedReason` and `decodeWorkflowArtifacts`, and removes aliases of domain types in application. This bundle lands first. F-012 moves the derivation file into domain, where SKILL-372 then applies its artifact accessors. F-008 deletes all application alias files, including the four domain-type aliases, so those SKILL-372 items become no-ops on the post-SKILL-370 tree. SKILL-372 should recheck its anchors before implementation.
- **SKILL-373 (runtime-core).** It briefly shared this key and has been renumbered. It starts after SKILL-370 and keeps this bundle's decisions: composition removal, the `TimeSource` binding, and the parse-function binding. It moves the architecture suite out of runtime-core, so this bundle's inject-property rule lands in `InjectConstructorDefaultsArchitectureTest` first and moves with the suite. Its relocated core tests enter application test sources after subtask 2 here has aligned test packages, and they must follow main-matching packages.
- **SKILL-374 (runtime-infra, in preparation).** Its investigation records that this bundle moves SQLite busy retry into the SQLite adapter without touching `sqlite.goalrunner`. The `ReleaseCatalogPort` adapter in `runtime-infra/http` (F-011) is a new file, and no SKILL-374 finding claims it.

## Over-engineering register

Paths relative to `runtime-kotlin/runtime-application/src/main/kotlin/skillbill/application`.

- `review/parallel/core/code/review/runner/model/ParallelCodeReviewRunnerBoundaries.kt`: delete. 19-field dependency bag.
- `review/parallel/core/code/review/runner/ParallelCodeReviewRunnerComposition.kt`: delete. Hand-written DI sub-graph.
- `install/InstallPlanningPorts.kt`, `install/InstallReconcilePorts.kt`: delete. Port bags.
- `idestatus/SelfManagedWriteBusyRetry.kt`: move into the SQLite adapter. Driver-string retry.
- 8 typealias files: delete. Re-export aliases.
- `telemetry/service/BlockedReasonNormalizer.kt`: delete. Verbatim copy of the domain function.
- `updatecheck/UpdateCheckService.kt` lines 76-193, GitHub request and JSON mapping: move to a `runtime-infra/http` adapter. Protocol code in a use case.
- `GoalLifecycleTelemetryEmitter.NONE`: move to testFixtures. Test-only null object in production.
- `runtime-core/.../RuntimeLayerBoundaryArchitectureTest` "parallel review composition root owns collaborator wiring": delete. It pins the bag in place. The general non-private-constructor-property rule replaces it.

Estimate: roughly 250 to 350 production lines removed net, most of it from the composition, bag, alias, and retry files. This is an estimate, not a measured diff.

## What stays unchanged

- The module graph, including engine depending on application. No new module, no merge, no inversion.
- Ports that tests substitute: `TelemetrySettingsProvider`, `GoalLifecycleTelemetryEmitter`, `GoalRunnerSubtaskLauncher`, and the adapter ports. A port with one production implementation is justified when tests use its substitutes.
- SKILL-347 retention decisions: `AgentRunGoalRunnerSubtaskLauncher`, `InstallAgentService`, `RuntimeOwnedPersistenceBoundary`, and the diff parser in `reviewevidence`.
- `WorkflowService` size and the three helper classes it constructs (`WorkflowServiceBlockedPhaseRetry`, `WorkflowServiceFeatureTaskAbandon`, `WorkflowServiceFeatureTaskIdentityRepair`). It builds `WorkflowEngine` (a domain object built the same way across 7 sites) and helpers that share its dependencies. That is ordinary composition, not a DI bypass. Those helper classes become `internal`, and nothing else changes. Don't split the service by line count.
- Shared contract mappers (`toReviewStatsPayload`, `toLearningListContract`, etc.) and use cases that return contract payloads (`LifecycleTelemetryService`, `SystemService`). CLI and MCP share these mappings, and `ARCHITECTURE.md` assigns presenter-to-contract mapping to application. A typed result per endpoint would add a type for each single consumer.
- kotlin-inject annotations (`@Inject`, `@RuntimeSingleton`) in application code. This is the standard constructor-injection annotation dependency.
- `java.nio.file.Path` in 44 imports. It is a value type here, and application performs no I/O through it.
- `runtime-application` testFixtures that wire real SQLite and contract adapters for integration tests. The dependency is test-scoped and sits below the composition root.
- No inbound-port interfaces in front of application services. CLI and MCP call concrete, constructor-injected services. Their tests substitute the outbound ports, not the services. A use-case interface per service would have one implementation and no substitute. The inbound boundary is the service's public method set, which F-010 narrows.
- `UnitOfWork` as a registry of 12 repositories behind one transaction. This is the standard unit-of-work pattern. Its two nullable repositories are a ports-module question and outside this scope.
- The error model. Typed `SkillBillRuntimeException` subclasses and sealed results are used consistently. There are no broad `catch (Exception)` or `catch (Throwable)` sites, and SKILL-347 fixed cancellation propagation.
- Port-driven decomposition helpers that the engine calls as top-level functions (see F-012). They take their ports as arguments and have no state. Wrapping them in a service class would add a type without adding a seam any test needs.
- Agent worker-output parsers (`parseWorkerResult`, `parseCitations`, `parseAdjudicationWorkerResult`) as private functions of their runners. Each has one caller, and domain already owns the shared finding parser.
- The existing architecture test suite. This work adds one rule to an existing scanner and deletes one source-shape assertion. It adds no new scanner classes.

## Limits

This is a static census. I didn't run tests, probes, or the quality gate while preparing it. A second pass checked the module against a hexagonal checklist: inbound ports, outbound port granularity, vendor protocol leakage, domain richness, unit-of-work and transaction ownership, error model, mutable state, serialization, raw maps, the API the engine consumes, test placement, and guard validity. That pass added F-011 and F-012 and the coordination section. The line-savings figure is an estimate. The symbol census matches names by word boundary, so a name that is also used by an unrelated symbol can hide a module-only declaration. The implementer should confirm each narrowed or deleted symbol with the compiler, not with this list.
