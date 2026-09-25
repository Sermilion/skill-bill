# runtime-ports architecture investigation (SKILL-377)

## Execution rule

This bundle runs on the current tree. It does not wait for a subtask of another issue. Ordering notes later in this file are overlap context. If a change this bundle's acceptance criteria need is missing, make it here. If it is already present, keep it.

## Judgment

`runtime-ports` sits in the right place in the graph. It depends only on `runtime-contracts` and `runtime-domain`, nothing inward depends on an adapter through it, and 148 of its 198 interfaces have an infrastructure or composition-root implementation. That is a working hexagonal boundary. Keep the module, its position, and the `UnitOfWork` plus repository shape.

The problems are in the contracts themselves. Several ports were shaped to satisfy tools or tests, not their consumers:

1. The guard that is supposed to keep ports honest scans a directory that does not exist, so it passes on every tree (F-001).
2. `WorkflowGitOperations` is an aggregate interface that also exposes eight sub-port getters and 23 forwarding extension functions. Its results carry NUL-delimited git output that the engine decodes in 13 places (F-002, F-003).
3. Twenty interface default bodies return success, empty, or "unsupported" values. The single production adapter overrides every one of them, so the defaults exist only for test fakes. Some are unsafe: a lease port whose default grants every lease, and a `stageAll` whose default reports success without staging. One no-op default, `unbindListener`, has no production override and hides an endpoint that is never closed (F-004).
4. Ports contain code that belongs to one side of the boundary: storage dispatch and a SQLite batch limit in `WorkflowStateRepository`, git stderr matching, a payload codec, and six review "ports" that application implements with anonymous objects returning precomputed values (F-005, F-006, F-003).
5. Dead surface: seven ports that nothing injects, five adapters that nothing uses, a dead feature-implement persistence family, a duplicated sealed type, 21 pass-through typealiases, and an unused library dependency (F-007, F-010).

None of these is a production incident today, but F-001 means the listed rules are not enforced, and F-004 means a new adapter or a refactor can silently get "success" behavior from a port. The fix removes layers and makes contracts explicit. It adds no module, framework, or new scanner class.

## Scope and method

- Baseline: commit `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b` on `feat/SKILL-368-build-logic-architecture-cleanup`, 2026-09-22.
- Production: 254 Kotlin files, 7,351 lines, 111 packages (56 hold one file). 571 non-private top-level declarations: 272 data classes, 156 interfaces, 42 fun interfaces, 31 enums, 44 top-level functions, 21 typealiases, 2 objects, 1 sealed class, 1 class, 1 top-level `val`. None are `internal`.
- Tests: 5 test files and 39 testFixtures files (1,758 lines).
- Method: Python and grep censuses across every `runtime-*` module for declarations, implementers (supertype parsing plus SAM constructors), injection consumers, imports, interface default bodies, and DI bindings. I read in full every file a finding cites. I ran the four ports-related architecture tests (`PortsDeclarationArchitectureTest`, `PortNullObjectAbsenceArchitectureTest`, `RuntimeContractModuleImportRulesTest`, `PlanningProjectionNoopValidatorGuardTest`) to confirm F-001. I did not delegate a line-level review.
- Context read: CLAUDE.md, ARCHITECTURE.md (Design Principles, Gradle Modules, Boundary Rules), `../../../docs/code-principles.md`, and the runtime-ports entries in `runtime-kotlin/agent/decisions.md` (runtime-ports has no `agent/` directory of its own). The relevant entries are SKILL-358 (2026-09-18), SKILL-233 subtask 2 (a)- (c), the audit-gap remediation (2026-09-06), the guard recalibration (2026-09-04), and SKILL-231 subtask 3 (2026-09-03).
- Prior work on this module: SKILL-358 "runtime-ports boundaries and simplicity" (landed 2026-09-18, `8fd33cfd4`). I read its investigation and spec in full. "SKILL-358 landing check" below lists what landed. This bundle keeps SKILL-358's retention decisions except one, which F-005 supersedes with new evidence.
- HEAD was still `dbf9f4830` at the gap pass.
- Sibling work on the same baseline: SKILL-370 (application), SKILL-371 (CLI, including repair of other vacuous guards), SKILL-372 (domain, typed workflow aggregate), SKILL-373 (composition root, `RuntimeContext`, architecture-suite move to `src/repoTest`), SKILL-374 (contracts), SKILL-375 (MCP), SKILL-376 (infra, goal-runner coordination to engine), SKILL-378 (engine, including deletion of experiment support). This investigation does not repeat their findings. "Coordination" below lists each shared seam.

## Module position

```text
runtime-cli, runtime-mcp ─► runtime-application ─► runtime-ports ─► runtime-domain ─► runtime-contracts
runtime-engine ──────────────────────────────────► runtime-ports
runtime-infra/* ─── implements ──────────────────► runtime-ports
runtime-core (composition) ── binds adapters to ─► runtime-ports
```

Ports Gradle: `api(runtime-contracts)`, `api(runtime-domain)`, `implementation(kotlinx.serialization.json)`. No ports main or test file imports kotlinx.serialization.

Interface implementers, by location of the production implementation:

| Production implementer | Interfaces | Reading |
| --- | ---: | --- |
| infrastructure or runtime-core | 148 | Real outbound ports. |
| application or engine only | 21 | 13 are callbacks an adapter calls back (agent-run probes, sinks, `GoalRunnerSubtaskLauncher`). Those are fine. 6 are the review preparation "ports" (F-006). |
| none (test only) | 7 | All are experiment ports, which SKILL-378 deletes. |
| sealed result hierarchies | 22 | Not ports. |

## SKILL-358 landing check

| SKILL-358 criterion | State at baseline | Where handled |
| --- | --- | --- |
| AC 1: no top-level object or non-DTO class, enforced by a guard | Not met. The guard it added reads no files (F-001). `IdeStatusProblemDetails` predates SKILL-358 and survived it. `ReviewMetricsDatabasePolicy` (`ae4ea5db0`) and `ReadinessTreeIdentityPayloadCodec` (`27eb9fa8b`) landed on 2026-09-19, the day after the guard. | F-001, F-003; SKILL-370 F-003 |
| AC 2-5: codec, bundle write, lease algorithm, identity policy | Landed. No `FeatureTaskExecutionIdentityPolicy` or `(this as` remains in ports, and `clearRunnerInterruptedPause` has no default. | None |
| AC 6: throwing defaults abstract | Landed for `error(`/`throw`. The same pattern now returns `Failed(...)` or `Unsupported(...)` instead (F-004), including `FeatureTaskRuntimeWorkerSupervisor.inspect`, which SKILL-358 F-004 named. | F-004 |
| F-001 item "derivation in a companion" (`FeatureTaskRuntimeSharedEvidenceResolverPort.NONE`) | Still present. Only tests reference it. | F-012 |
| F-001 item "parsing in a DTO" (`RepoValidationIssue.fromRawIssue`) | Still present. `infra/skills` is its only caller. | F-007 |
| AC 8: exceptions in the taxonomy | Landed for declared exceptions. `GoalPlanningPreparationState.fromWireValue` throws a JDK `IllegalArgumentException`. | F-013 |
| AC 9-11: one `RuntimeContext` constructor, wire keys, raw-map wrappers | Landed. The raw-map scanner that should cover ports is itself vacuous (SKILL-371 F-001). | SKILL-371 |
| Retention: `WorkflowFamily` extensions beside the repository | Kept by SKILL-358 and decision 2026-09-06 (c). | Superseded by F-005 |
| Retention: `GoalRunnerManifestStore` role split, `Path`, fixtures, `ReviewFinishedTelemetryPayload`, `NONE` on real default strategies | Kept. | What stays |

## Principle assessment

| Principle | Assessment | Evidence |
| --- | --- | --- |
| Dependency rule / hexagonal | Holds at module level | No adapter or entry imports. Leaks are content: git protocol text (F-003), SQLite batch size and table dispatch (F-005). |
| Interface segregation | Undermined by getters and forwarders | `WorkflowGitOperations` extends 5 interfaces, exposes 8 more through getters, and forwards them with 23 extensions (F-002). |
| Liskov / substitutability | Violated by defaults | Defaults that report success or substitute another operation (`repositoryCheckpointFingerprint` falls back to `repositoryFingerprint`) change semantics for any implementer that omits an override (F-004). |
| YAGNI | Dead and speculative surface | 7 unconsumed ports, 5 dead adapters, a dead persistence family, 21 aliases, one unused dependency (F-005, F-007, F-010). |
| Closed outcome types (ARCHITECTURE.md "State Ownership") | Violated in the launcher contract | `AgentRunLaunchFacts` models exclusive terminations as three booleans plus `require`, and `reviewProcessOutcome` resolves them by precedence (F-009). |
| Observability policy | One swallowed degradation | Nullable `UnitOfWork` repositories make the engine return an empty list with no record (F-008). |
| Enforcement | Not enforced | `PortsDeclarationArchitectureTest` reads zero files (F-001). |

Large Kotlin and JVM codebases (Dagger, Hilt, Anvil, kotlin-inject shops) converge on the same norms. A port is an interface its consumer injects. Its members are abstract unless a default holds no policy. Its results are typed. The adapter owns storage and protocol details. Test doubles live in test fixtures, not in production default bodies. The findings mark where this module departs from those norms.

## Findings

Priority reflects enforcement loss, correctness risk, and change cost. None is a production incident.

### F-001. High. The ports declaration guard scans a missing directory

Evidence:

- `runtime-core/src/test/kotlin/skillbill/architecture/PortsDeclarationArchitectureTest.kt:10-16` computes `runtimeRoot` as the working directory's parent when the working directory name starts with `runtime-`. Under Gradle that is `../../../runtime-kotlin`. It then resolves `runtime-kotlin/runtime-ports/src/main/kotlin`, which yields `runtime-kotlin/runtime-kotlin/runtime-ports/...`. `scanPortsMainSource` returns an empty list when the directory is missing.
- `PortNullObjectAbsenceArchitectureTest.kt:59` has the same doubled prefix for `runtime-ports/src/testFixtures`. Its first test resolves module paths correctly.
- A run of both tests at baseline passed. `PortsDeclarationArchitectureTest` took 0.038 s. Ports main source currently contains two top-level `object`s (`ReviewMetricsDatabasePolicy`, `ReadinessTreeIdentityPayloadCodec`) and one non-DTO top-level class (`IdeStatusProblemDetails`), each of which the test forbids.
- The first case of `PortNullObjectAbsenceArchitectureTest` resolves `RuntimeModuleCatalog.declaredGradleModules` entries as directories. The seven `runtime-infra:<name>` entries contain a colon, `Files.isDirectory` drops them, and no infrastructure main source is scanned. An infra grep finds no hidden violation today. Its census regex matches only `object (Noop|Unavailable|Empty|Unconfigured)…`, so `class NoopExperimentNavigationSessionRunner` in engine main passes (F-012).
- SKILL-371 subtask 1 repairs guards that pass bare module paths to the repository-root walker. These two tests use their own walker and fail for a different reason, so SKILL-371's scope ("vacuous for the same reason") may not reach them. SKILL-373 keeps both tests and relocates them to `runtime-architecture`.

Fix: resolve both roots through the scan-root convention SKILL-371 establishes, fail on a missing root, and assert that at least one file was read. Resolve null-object module roots through the module-directory mapping, and extend the census regex to `class` declarations. SKILL-378 subtask 1 adds companion-`val` detection. Fix the violations the live guards report:
- `ReviewMetricsDatabasePolicy` leaves ports with SKILL-370 F-003.
- `ReadinessTreeIdentityPayloadCodec` goes away with F-003.
- `IdeStatusProblemDetails` becomes a `@JvmInline value class` with its private constructor. Turning it into a data class would expose a public raw map and trip the raw-map rule.

### F-002. High. `WorkflowGitOperations` is an aggregate plus a service locator

Evidence (`runtime-ports/.../ports/workflow/gitops/`):

- `WorkflowGitOperations.kt` extends 5 capability interfaces and declares 8 getters (`checkpointHistoryOperations`, `linkedWorktreeOperations`, `goalSubtaskReviewOperations`, `repositoryFingerprintOperations`, `readinessTreeIdentityOperations`, `repositoryOwnedPathsOperations`, `runtimePhaseFileManifestOperations`, `scopedStagingOperations`).
- 23 top-level extension functions on `WorkflowGitOperations` forward to those getters under a renamed method (`updateCheckpointRef` → `checkpointHistoryOperations.updateRef`, `stagePaths` → `scopedStagingOperations.stagePaths`, and so on). `resolveReadinessTreeIdentityPayload` has no caller outside its file.
- 38 main files in engine and application consume `WorkflowGitOperations`. Outside ports, the getters are read directly in three places (`linkedWorktreeOperations`), and the rest go through the forwarders, which are imported 66 times.
- ARCHITECTURE.md: "A port describes operations its consumer needs, not getters for another object's entire dependency graph."

Origin: the getters arrived in SKILL-150/162 (`scopedStagingOperations`) and SKILL-190/221 (`checkpointHistoryOperations`, during detekt-suppression elimination). They kept the aggregate under `TooManyFunctions.thresholdInInterfaces: 11` (`config/detekt/detekt.yml:19`). The 2026-09-04 recalibration kept that threshold as "the SKILL-231 guard against composite ports". The recorded precedent for a wide port is the 2026-09-06 audit-gap entry (a): `GoalRunnerManifestStore` extends segregated role interfaces declared in ports, with no getters and no suppression. Detekt counts declared functions, so an inheriting aggregate declares none. The git port is the one wide port that does not follow that precedent.

Fix: make `WorkflowGitOperations` inherit every capability interface that remains. That is 13 at baseline, and 12 after SKILL-378 deletes linked-worktree operations. Rename capability members to the names the forwarders use today, so call sites keep their names and change only imports. Delete the 8 getters and 23 forwarders. The workflow adapter implements the aggregate with Kotlin interface delegation (`by`) over its existing capability objects. Consumers may inject a single capability where that is all they use. The spec does not require that change.

### F-003. Medium. Git protocol details cross the port

Evidence:

- `WorkflowGitOperationResult` carries `value: String` and `error: String`. Structured payloads are NUL-joined: checkpoint ref listings, readiness tree identity, index-state snapshots, path content identities. Engine and application main source contain 13 `\u0000` decode sites.
- `ReadinessTreeIdentityPayloadCodec` (`readiness/ReadinessTreeIdentityGitOperations.kt`), a ports `object`, encodes the payload for the adapter and decodes it for the engine. `deleteCheckpointRefsUnderPrefix` in `WorkflowGitOperations.kt` parses the ref listing inside ports.
- `recordsNothingToCommit` (`model/WorkflowGitOperationResult.kt`) matches git's English stderr strings ("nothing to commit") and is called from `GoalRunnerFinalization` in the engine and from the adapter.
- `WorkflowGitOperationResult.fromWire(status: String)` and the `invoke(status: String)` operator accept free-text status. The type is not persisted.

Fix: operations whose payload the inner layers parse return typed results: ref lists as `List<String>`, readiness identity as `ReadinessTreeIdentity?` inside the result, index state as an opaque snapshot value, path identities as a typed map. `createCommit` reports "nothing to commit" as its own result variant, decided in the adapter. Operations that return one scalar (a sha, a branch name) keep `WorkflowGitOperationResult`. Delete the codec, the stderr matcher, and the string-status constructors. SKILL-376 subtask 1 declares git result semantics a non-goal, so this lands after it.

### F-004. High. Default bodies that stand in for missing implementations

Evidence: 37 interface members in ports main have default bodies. The following 20 are overridden by the only production adapter, so production never runs them:

| Default | Behavior when not overridden |
| --- | --- |
| `WorkflowGitCommitHistoryOperations.{resetSoftToCommit, resetHardToCommit, isCommitAncestor, resolveCommit, readHeadTrackedFile}` | Returns `Failed("… cannot …")`. Passes the "no error/throw default" rule by returning instead of throwing. |
| `GoalSubtaskReviewGitOperations.recoverBaseline` | Returns an ERROR result. |
| `WorkflowGitWorktreeOperations.stageAll` (`worktree/WorkflowGitWorktreeOperations.kt:11`) | Returns `Ok` without staging. |
| `RepositoryFingerprintGitOperations.repositoryCheckpointFingerprint` | Silently returns the whole-repository fingerprint instead. |
| `ExperimentPairOwnerPort.{saveReport, loadReport, listReports, acquireLease, releaseLease}` and the same five on `ExperimentPairRepository` | `acquireLease` returns `true` (`ExperimentPairOwnerPort.kt:23`). Every caller gets the lease, and reports are dropped. SKILL-378 deletes both ports. |
| `FeatureTaskRuntimeWorkerRepository.releaseFeatureTaskRuntimeWorkerIfExpired` | Read-then-release in two calls. The atomic fenced version lives in SQLite. |
| `GoalRunnerWorkflowOutcomeMutationStore.authoritativeOutcomes` | Returns an empty map. |
| `FeatureVerifyWorkflowStateRepository.getFeatureVerifyWorkflows`, `FeatureTaskRuntimeWorkflowStateRepository.getFeatureTaskRuntimeWorkflows` | N+1 loop. SQLite batches. |

Two more defaults matter:

- `FeatureTaskRuntimeWorkerSupervisor.inspect` returns `Unsupported(...)`. SKILL-358 F-004 named it, and `JdkFeatureTaskRuntimeWorkerSupervisor` overrides it.
- `GovernedReviewEvidenceEndpointHandle.unbindListener() = Unit` has no production override, so the one production call is a no-op. `ParallelCodeReviewInlineCoverageContinuation` calls `endpoint.unbindListener()` (line 91) and then replaces `endpoint` with a newly bound one. Its `finally` (line 103) closes only the last endpoint. Every extra coverage-continuation pass therefore leaves a `ServerSocketChannel` and its acceptor thread open until the process exits. This is a static reading; I did not observe the leak at runtime.

The testFixtures directory already provides `Noop*` and `*Defaults` doubles for these ports. Tests compose them with `by` delegation.

Fix: make these members abstract, including `inspect`. Delete `unbindListener`. The continuation closes the endpoint it replaces, and a test proves the replaced endpoint's channel is closed. Delete defaults with no production caller (for example `GoalSubtaskPlanRepository.firstMissingPlan`). Keep a default only when it is written purely in terms of the same interface's members and encodes no policy, such as `DatabaseSessionFactory.readIfPresent` or SAM-interface conveniences. Extend the existing interface-default check in `PortsDeclarationArchitectureTest` to reject default bodies that are a bare constant result (`true`, `false`, `null`, `Unit`, empty collections, `WorkflowGitOperationResult.Ok/Failed`), with an empty baseline.

### F-005. Medium. `WorkflowStateRepository` carries dead families, alias families, and storage dispatch

Evidence (`ports/workflow/WorkflowStateRepository.kt`, `model/WorkflowRecordMapping.kt`):

- The KDoc says the interface is "split into one capability interface per family so no single interface crosses the detekt `TooManyFunctions` threshold".
- `FeatureImplementWorkflowStateRepository` (6 members, prose-mode "compatibility alias") has no caller in application, engine, CLI, or MCP. Only the SQLite implementation and tests use it. Neither does `FeatureImplementSessionSummary` or either `toContract` mapper in `WorkflowRecordMapping.kt`, which nothing imports. SKILL-200 removed the prose skill.
- `FeatureTaskRuntimeWorkflowStateRepository` (5 members) duplicates `FeatureTaskWorkflowStateRepository` calls with `mode = RUNTIME`, under a KDoc that calls it a "compatibility alias".
- Top-level `WorkflowFamily.save/saveRecord/get/getAll/list/latest/sessionSummary` choose the storage family with `when` in ports. `getAll` batches by `WORKFLOW_SNAPSHOT_BATCH_SIZE = 900`, a SQLite bound-parameter limit. About 50 main files across application, engine, and SQLite import these helpers.

Recorded decision this supersedes: 2026-09-06 SKILL-233 subtask 2 (c) moved these seven members off the `WorkflowFamily` enum and made them extensions "beside the port they drive", and SKILL-358 retained that. The alternatives it weighed were keeping them on the enum and moving the enum next to the port. It did not weigh family-keyed repository members. New evidence against the extensions:
- They encode storage-table selection and a SQLite bound-parameter limit, which are adapter knowledge.
- The SQLite adapter itself calls them. The adapter routes back through port-side helpers into its own methods.
- Two whole sub-interfaces (feature-implement, runtime alias) exist only to be dispatched to.
- The decision's own heading reads "behaviour that is not a DTO extension leaves `runtime-ports`".

Family-keyed members move the behaviour into the adapter and shrink the interface.

Fix: replace the per-family verify and runtime methods and the dispatch helpers with family-keyed repository operations (`save(family, record)`, `get(family, workflowId)`, `getAll(family, ids)`, `list(family, limit)`, `latest(family)`, `sessionSummary(family, sessionId)`). The adapter owns table selection and batching. Call sites change from `family.get(repo, id)` to `repo.get(family, id)`. Delete the feature-implement family, its DTO, and both mappers. Keep `terminalizeLegacyProseFeatureTaskWorkflow` and mode-agnostic `getFeatureTaskWorkflow` for legacy rows. This follows SKILL-372 subtask 1, which rewrites `toSnapshot`/`toRecord` and the continue-session summary in the same files.

### F-006. Medium. Six review "ports" implemented by constants in application

Evidence:

- `ports/review/preparation/ReviewPreparationPorts.kt` declares `ReviewScopeResolverPort`, `ReviewStackRoutingPort`, `ReviewGuidancePort`, `ReviewLearningsPort`, `ReviewBuildTestFactsPort`, and `ReviewLaneSelectionPort`. `ports/review/model/ReviewPreparationPortModels.kt` bundles them as `ReviewFactPorts`.
- The only production implementation is `ParallelReviewPreparationCompiler.reviewFactPorts` (application), which builds six anonymous objects. Two return values computed a few lines earlier, one returns the passed-in selection, and three return `emptyList()`.
- The only consumer is `ReviewPreparationService`, which calls each port once in sequence.

These are values passed through interfaces, not ports. No adapter implements them, and the arguments they receive are ignored.

Fix: `ReviewPreparationService` takes one `ReviewPreparationFacts` value (scope, routing, lane selection, rules, learnings, build/test facts). The compiler builds it. Delete the six interfaces and `ReviewFactPorts`. `ReviewScopeFacts`, `ReviewStackRoutingFacts`, and `ReviewLaneSelection` are referenced only by these ports and by the two application classes, so they move with the facts value into runtime-application. Their fields use domain types only, so the move compiles. Test doubles become data. Preparation output stays byte-identical.

### F-007. Medium. Unconsumed ports, dead adapters, and dead declarations

Evidence:

- Bound in runtime-core and never injected by any main or test code: `ScaffoldGeneratedStagingPort`, `ScaffoldInstallLinkPort`, `ScaffoldManifestPersistencePort`, `ScaffoldSourceLoaderPort`, `ScaffoldRepoValidationPort` (`di/scaffold/RuntimeScaffoldProvides.kt`, `RuntimeScaffoldValidationProvides.kt`), `DeclaredReviewSpecialistsPort` (`di/review/RuntimeReviewAddonCatalogProvides.kt`), and `ReviewLaunchIsolationResolver` (`di/review/RuntimeReviewLaunchProvides.kt`).
- `InstalledWorkspaceBaselineStatusPort` is exposed as `RuntimeComponent.installedWorkspaceBaselineStatusPort`. Nothing reads it except a string in `PrincipleEnforcementInventory`.
- Their adapters: `FileSystemScaffoldGeneratedStaging` (44 lines), `FileSystemScaffoldInstallLink` (36), `FileSystemScaffoldManifestPersistence` (84), `FileSystemDeclaredReviewSpecialists` (29), `AgentRunReviewIsolationResolver` (28), and `FileSystemInstalledWorkspaceBaselineStatus` (38). Nothing references them outside their own file, their binding, and at most one test. `FileSystemScaffoldSourceLoader` and `FileSystemScaffoldRepoValidation` are used concretely by `FileSystemScaffoldOrchestrator`, so only their port and binding are dead.
- `ExperimentPairRepository` is exposed on `UnitOfWork.experimentPairs`, but only `SqliteExperimentPairOwnerStore` reads it. It is an adapter-internal DAO. See the fix for why it stays.
- `LegacyGoalPlanningPreparationRepository`: 5 of 7 members (`findByGoalAndSubtask`, `listPreparedByGoalOrdered`, `preparedCount`, `firstMissingOrIncompleteSubtask`, `preparedStatus`) have no application or engine caller. `LEGACY_FEATURE_TASK_PROSE_WORKFLOW_STATUSES` is read only by `SQLiteWorkListRepository`.
- Dead declarations: `featuretask/model/FeatureTaskRuntimeProcessInspection` (zero imports; the live copy is `taskruntime/model`), `GoalRunnerChildRepairApplyStateInit`, `FeatureTaskRuntimeSnapshot`, and `NativeAgentSourceProjection`. `NormalizedGoalPlanningPreparationRepository` declares no members and has no consumer. It only sits between two roles and their aggregate.
- `kotlinx.serialization.json` is declared in `runtime-ports/build.gradle.kts` and used by no ports source.

- `RepoValidationIssue.fromRawIssue` parses `"path: message"` inside a ports DTO companion. SKILL-358 F-001 listed it. Its only caller is `infra/skills` `RepoValidationRuntime`.

Fix: delete the unconsumed ports, their bindings, the component accessor, the dead adapters and their tests, and the dead declarations. Delete the five unused legacy planning members from the port. Each is referenced only by its own SQLite override, which delegates to an internal record, so there is nothing to move. Move the legacy status constant and `fromRawIssue` into their only adapters. Both import only domain and ports types. `ExperimentPairRepository` would have to stay on `UnitOfWork`, since `SqliteExperimentPairOwnerStore` reaches it only through that interface. SKILL-378 deletes it with the rest of experiment support. Delete `NormalizedGoalPlanningPreparationRepository`. The other zero-consumer role interfaces stay; see "What stays". Remove the unused dependency.

### F-008. Medium. Nullable repositories hide a degradation

Evidence: `UnitOfWork.kt:36-37` declares `rejectedOutputDiagnostics` and `rejectedOutputDiagnosticPermissions` nullable. The only production implementation (`SQLiteRepositories.kt:98-100`) is non-null. `GoalPlanningLogService.kt:63-64` returns `emptyList()` on null without a diagnostic record, contrary to `../../../docs/observability-policy.md`. Application throws on the same null.

Fix: make both non-null and delete the null branches. Tests that need absence use a fixture repository.

### F-009. Medium. `AgentRunLaunchFacts` encodes exclusive outcomes as flags

Evidence (`ports/agentrun/model/AgentRunLauncherModels.kt:211-250`):

- `exitStatus: Int?`, `timedOut`, `interrupted`, and `spawnFailed` are independent fields. Three `require` calls forbid the combinations. `reviewProcessOutcome()` resolves them by `when` precedence. ARCHITECTURE.md requires "alternatives in a closed type, not independently nullable reports with accidental precedence".
- The data class holds `stdoutBytes: ByteArray`, so `equals` and `hashCode` compare the array by reference.
- `stdoutSha256` is computed in a constructor default, so every construction and `copy()` hashes all of stdout.
- 2 main and 18 test files construct it. The flags are read in about 66 main-source expressions (the name census includes same-named fields of other types).

Fix: replace the four fields with one sealed `AgentRunTermination` (`Exited(code)`, `TimedOut`, `Interrupted`, `SpawnFailed`). The launcher adapter computes size and digest once and passes them in. Keep the bytes out of data-class equality. `ReviewProcessOutcome` mapping and all persisted and wire values stay the same.

### F-010. Low. Pass-through typealiases and untyped members

Evidence:

- 21 typealiases in ports main. Nineteen follow `X = XModel` across 8 experiment files, and SKILL-378 deletes those. The others are `LinkedWorktreeAddRequest`/`LinkedWorktreeRemoveRequest` (`worktree/WorkflowGitLinkedWorktreeOperations.kt`) and `DecompositionManifestWriteResult`. SKILL-372 removes aliases of domain types and explicitly leaves ports aliases out.
- `Any` in port contracts: `GoalRunnerTerminalOutcomeStore.recoverMissingResultPrefixOutput(output: Any)`, `HistoryArtifactAppend.entryMap: Any`, `GoalRunnerChildRepairApplyState…artifacts: Any`, and `GoalChildPlanningHydrationResult.stepUpdates: List<Any>, artifacts: Any`.

Fix: rename each `*Model` type to its alias name and delete the alias. Replace each `Any` with the concrete type its producer passes today. After SKILL-372, that is the typed artifacts and step types.

### F-011. Low. Documentation does not match the module

Evidence: ARCHITECTURE.md says `FileLocation` "carries repo paths through domain and port signatures without a `java.nio` dependency". In fact 85 ports files use `java.nio.file.Path`, and `FileLocation` appears only in the two bridge functions. The Gradle Modules entry for `runtime-ports` also lists items this work removes.

Fix: describe the landed state. `Path` is the port value type for filesystem locations, and `FileLocation` is a domain value with two bridges. Do not migrate signatures.

### F-012. Medium. `NONE` companions that are sentinels or test-only substitutes

Evidence:

- `FeatureTaskRuntimeSharedEvidenceLocatorReadPort.NONE` (`taskruntime/FeatureTaskRuntimeSharedEvidenceLocatorReadPort.kt:9-13`) throws `ReviewHunkEvidenceLocatorMissingError` when called. Production passes it as "no reader" (`ReviewPreparationService.kt:43`, `ParallelReviewPreparationCompiler.kt:50`) and tests for it by identity (`!== …NONE` in `ParallelCodeReviewRunnerPlanning.kt:140`, `ReviewHunkStoreIndexing.kt:31`, and infra `FileSystemReviewEvidenceBrokerReads.kt:204`). It is a nullable value spelled as an object.
- `FeatureTaskRuntimeSharedEvidenceResolverPort.NONE` builds an artifact from a deriver inside the port declaration. No main source references it, and 5 test files do. SKILL-358 F-001 named it.
- Six more companion `NONE`s have no main-source reference. SKILL-378's census found them; I confirmed each by grep:
  - `GoalPlanningContextDiscovery.NONE` (1 test file; its private `EMPTY*` helpers exist only for it)
  - `ReviewNativeAgentPreflightPort.NONE` (2 test files)
  - `ReviewLaunchAgentStagingPort.NONE` (2 test files)
  - `GoalPlanningBoundaryBodyResolver.NONE`, `InstalledPlatformPackCatalogPort.NONE`, and `DeclaredReviewSpecialistsPort.NONE` (no reference; the last goes with its port in F-007)
- `NoopExperimentNavigationSessionRunner` (engine main) is never constructed in production. The null-object census misses it because it is a `class` (F-001). SKILL-378 deletes it; SKILL-377 widens the census to `class` declarations.
- The seven `AgentRun*` `NONE` values are real default strategies (default parameters of `SkillRunRequest`). SKILL-358 retained them.

Fix: make the locator reader nullable at its call sites and delete its `NONE`. Move the four test-only `NONE`s into runtime-ports testFixtures and delete the unreferenced ones. Extend SKILL-378's companion-`val` census rule, which 378.1 scopes to runtime-engine main, to runtime-ports main so these cannot return.

### F-013. Low. A JDK exception at a wire-decode seam

Evidence: `goalrunner/model/GoalPlanningPreparationRecord.kt:111` `GoalPlanningPreparationState.fromWireValue` throws `IllegalArgumentException` for an unknown stored token. The CLAUDE.md contract and SKILL-358 AC 8 require typed errors at parse seams.

Fix: throw the existing `InvalidGoalPlanningPreparationSchemaError`, or return null and let the caller raise it. Well-formed rows behave the same.

## Checklist

| # | Item | Answer |
| --- | --- | --- |
| 1 | Dependency direction | Clean. Main edges are `api(contracts)` and `api(domain)`, both justified because port signatures expose their types. `implementation(kotlinx.serialization.json)` is unused (F-007). Tests import nothing above ports (0 imports of application, engine, infrastructure, cli, mcp, or di). |
| 2 | Inbound side | Not a ports concern. SKILL-370 F-002 owns the application-side getter. Ports declares no inbound use-case interface. |
| 3 | Outbound side | Git protocol text (13 NUL decode sites, stderr matching) and a SQLite batch limit leak inward (F-003, F-005). No HTTP, SQL, or header vocabulary appears in ports. The only vendor-named port family is `*GitOperations`, kept by the 2026-09-03 decision. |
| 4 | Domain richness | Lease-expiry and planning-status rules sit in interface defaults (F-004). `FeatureTaskRuntimeWorkerOwnership` holds lease parsing in a ports model. It is shared by SQLite and engine, SKILL-372 types domain timestamps, and nothing is duplicated, so it stays. |
| 5 | Composition | `WorkflowGitOperations` getters act as a service locator (F-002). `ReviewFactPorts` is a port bag (F-006). `RuntimeContext` override slots are owned by SKILL-373. |
| 6 | Entry-point leakage | Clean. No CLI flag or MCP tool name appears in ports main (grep of `--`-prefixed literals and `mcp` tokens). |
| 7 | Ambient effects | Clean. No `System.`, `Instant.now`, `Thread`, `currentTimeMillis`, `nanoTime`, or `getenv` in ports main. `MessageDigest` in `AgentRunLaunchFacts` is pure but misplaced (F-009). |
| 8 | State and transactions | Clean in ports. `DatabaseSessionFactory` owns `read`/`transaction`/`selfManagedWrite`, and ports holds no mutable state. One check-then-act default existed (`releaseFeatureTaskRuntimeWorkerIfExpired`, F-004). |
| 9 | Error model | 7 `throw`s, 6 of them typed; the exception is F-013. 1 narrow `catch (DateTimeParseException)`. 128 `require`/`check` calls in self-validating DTOs, which SKILL-358 retained. No broad catch, and no cancellation handling needed. |
| 10 | Cohesion and ownership | Single-consumer items: `fromRawIssue` and the legacy status constant (infra), review facts types (application), and `ExperimentPairRepository` (SQLite; stays). See F-006 and F-007. |
| 11 | YAGNI | F-004, F-007, F-010, F-012. |
| 12 | Naming and packages | Two orphan test packages: `test/…/ports/architecture` (1 file) and `testFixtures/…/ports/review/empty` (1 file). Both move to their main package. Two packages spell the feature-task-runtime area (`featuretask`, `taskruntime`); the dead duplicate in F-007 is the only collision. No stutter packages. The largest package is `review/model` (18 files), which is within the 20-file model limit. |
| 13 | Guard validity | `PortsDeclarationArchitectureTest` is vacuous. `PortNullObjectAbsenceArchitectureTest` is partly vacuous (F-001). `RuntimeRawMapArchitectureTest` filters on a repo-relative `runtime-ports/src/main/kotlin/` prefix (line 72) and matches nothing; SKILL-371 subtask 1 repairs it. `RuntimeContractModuleImportRulesTest` and `PlanningProjectionNoopValidatorGuardTest` resolve module paths correctly. |

## Over-engineering register

Paths relative to `../../../runtime-kotlin/runtime-ports/src/main/kotlin/skillbill/ports`.

- `workflow/gitops/WorkflowGitOperations.kt`: 8 getters and 7 forwarders; plus 16 forwarders in 6 sibling files. Delete.
- `workflow/gitops/readiness/ReadinessTreeIdentityGitOperations.kt`: `ReadinessTreeIdentityPayloadCodec`. Replaced by a typed result.
- `workflow/gitops/model/WorkflowGitOperationResult.kt`: `recordsNothingToCommit`, `fromWire`, `invoke(status)`. Delete.
- 20 fake-serving default bodies (F-004). Make abstract.
- `workflow/WorkflowStateRepository.kt`: feature-implement family, runtime alias family, family dispatch helpers, batch constant. Replace with family-keyed operations.
- `workflow/model/WorkflowRecordMapping.kt`: two `toContract` mappers with no importer. Delete.
- `GovernedReviewEvidenceEndpointHandle.unbindListener`: a no-op that hides an unclosed endpoint. Delete.
- 8 `NONE` companions: a sentinel, four test-only substitutes, and three unused (F-012). Delete, or move to testFixtures.
- `review/preparation/ReviewPreparationPorts.kt`, `ReviewFactPorts`: delete. Replace with one value.
- 7 unconsumed ports and 6 dead adapters (F-007). Delete.
- 21 typealiases. Delete.
- `featuretask/model/FeatureTaskRuntimeProcessInspection.kt`: duplicate. Delete.
- `kotlinx.serialization.json` dependency. Delete.

Estimate: roughly 700 to 1,000 production lines removed across ports, workflow and SQLite adapters, core bindings, and application, most of it from forwarders, dead adapters, dead families, and review ports. This is an estimate, not a measured diff.

## What stays unchanged

- The module and its position, `api` edges to contracts and domain, and no new module.
- `FeatureTaskRuntimeWorkerOwnership` lease parsing in its ports model. It is shared by the SQLite and engine sides, and the parse seam raises a typed error.
- 66 enum entries that own their wire tokens through `wireValue`, and 128 `require`/`check` calls in self-validating DTOs (SKILL-358 retention).
- The `featuretask` and `taskruntime` package pair. Merging them is a rename with no boundary gain, and the one real collision is deleted (F-007).
- Considered and rejected: consumers injecting narrow git capabilities instead of the aggregate (38 consumers of churn for no substitution gain; allowed, not required); a detekt suppression to flatten role splits (forbidden by the 2026-09-06 audit-gap decision); `explicitApi()` and visibility narrowing (ports is a public contract module, and 0 of its declarations are module-private by design).
- `UnitOfWork` exposing repositories plus `DatabaseSessionFactory.read/transaction`. That is the standard unit-of-work shape, and `GoalRunnerPersistenceSession` is a real narrower view with 6 consumers.
- Callback fun interfaces in `agentrun` and their `NONE` values. An adapter calls them back, and SAM defaults hold no policy.
- `SkillRunRequest`'s width. It is the launcher's one request type, and its `init` checks guard real misuse.
- Role splits of `GoalRunnerManifestStore`, `GoalRunnerWorkflowOutcomeStore`, and `DecompositionManifestStore`, including roles with no direct consumer (the five `GoalRunnerManifest*` roles, `GoalRunnerWorkflowOutcomeMutationStore`, `DecompositionManifestPersistencePort`). Consumers bind to the aggregate. Collapsing them would only trade role names for a detekt suppression, and SKILL-376 moves their implementations. Revisit after SKILL-376.
- `WorkflowGitOperationResult` for single-scalar git operations.
- `ReviewFinishedTelemetryPayload` projection in ports. ARCHITECTURE.md documents shared payload projection there, and application and SQLite both consume it.
- Experiment ports: SKILL-378 deletes experiment support, so SKILL-377 does not touch them.
- `java.nio.file.Path` in port signatures, package granularity (driven by the 12-sibling and 20-model limits), and `RuntimeContext` (SKILL-373 moves its override slots).

## Coordination

| Seam | Owner | This spec |
| --- | --- | --- |
| Architecture scan-root convention | SKILL-371 subtask 1 | Applies it to the two ports tests. |
| Architecture-suite location | SKILL-373 subtask 3 moves the suite to runtime-core `src/repoTest`; there is no new module | SKILL-377 edits the tests wherever they live. If 377 lands first, 373 subtask 3 moves those edits with the suite. |
| `RuntimeComponent.installedWorkspaceBaselineStatusPort` accessor | SKILL-377 owns the port, binding, and adapter. SKILL-373 subtask 1 (earlier in the order) deletes the accessor if it is still present | SKILL-377 subtask 3 skips the accessor if it is gone. |
| Linked-worktree git operations and their two typealiases | SKILL-378 subtask 1. The only consumers are the experiment pair coordinators | SKILL-377 subtask 1 flattens the capabilities that remain, which is 12 after 378. |
| `FeatureImplementSessionSummaryContract`, `FeatureVerifySessionSummaryContract` | SKILL-374 keeps them in contracts because a port exposes them. That exposure is the two dead `toContract` mappers, and no other file uses them | SKILL-377 subtask 2 deletes the mappers and both contracts with their same-file keys. |
| `ReviewMetricsDatabasePolicy` | SKILL-370 F-003 | No change. The guard confirms it has left. |
| `WorkflowStateRecord` mapping and snapshot shape | SKILL-372 subtask 1, which moves the strict step and artifact decoders into `WorkflowRecordMapping.kt` | Family-keyed API lands after it. Deleting the `toContract` mappers keeps those decoders. `WorkflowContinueSessionSummary` stays in contracts (SKILL-374). |
| Validator interfaces moving into runtime-ports | SKILL-372 subtasks 1 and 2 (`WorkflowSnapshotValidator` and six more). 372.2 types their `Any` members and removes the `= Unit` default first | No action. The live guards scan them once they land. |
| Git process code in `infra/workflow` | SKILL-376 subtask 1 | Typed results land after it. |
| Experiment support (`ports/experiment/**`, `ExperimentPairRepository`, experiment launcher fields, `NoopExperimentNavigationSessionRunner`, 19 of 21 ports typealiases) and companion-`val` detection in `PortNullObjectCensus` | SKILL-378 subtask 1, which deletes experiment support and runs before SKILL-370 | SKILL-377 starts after it and drops every experiment item. SKILL-377 keeps the scan-root fixes, `class` detection in the census, all seven non-experiment ports `NONE`s (F-012), and extending 378.1's companion-`val` rule, which 378 scopes to engine main, to runtime-ports main. SKILL-378 subtask 2 runs after 377.1, and 378.3 after 377.3 (alias recensus). No cycle. |
| Raw-map rule over ports main (24 `Map<String, Any?>` occurrences in 7 files) | SKILL-371 subtask 1 ("any further violation the restored guards report") | Not duplicated here. `IdeStatusProblemDetails` is chosen as a value class so this spec adds no raw map. |
| `ParallelCodeReviewInlineCoverageContinuation` package path | SKILL-370 F-009 renames `review/parallel/core/code/review/*` | The endpoint-close fix follows the file wherever SKILL-370 put it. |
| Recorded decision 2026-09-06 (c), retained by SKILL-358 | This bundle | F-005 supersedes it. Subtask 2 records the superseding entry in `../../../runtime-kotlin/agent/decisions.md`. |
| Goal-runner store implementations | SKILL-376 subtask 2 | No signature change here, apart from F-004 defaults and F-010 `Any`. |

## Limits

This is a static census, apart from the one test run for F-001. Name-based reference counts can merge unrelated symbols that share a name. Implementer detection parsed supertype lists and SAM constructors, and I verified each claim a finding makes with a direct grep. The implementer should confirm every deletion with the compiler.

`ExperimentParentDeliveryReconciler` is never constructed in production, so the SKILL-366 crash-after-push idempotency requirement has no production path. SKILL-378 resolves this by deleting experiment support.
