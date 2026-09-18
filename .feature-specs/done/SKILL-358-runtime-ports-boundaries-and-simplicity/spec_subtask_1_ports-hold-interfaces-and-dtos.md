# SKILL-358 Subtask 1 - Ports hold interfaces and DTOs

Parent spec: [.feature-specs/SKILL-358-runtime-ports-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-358

## Scope

Resolve F-001, F-002, F-003, F-004, F-006, F-007, F-008, F-009, and the module-owned part of F-010 in
[investigation.md](investigation.md).

Own every file under `../../../runtime-kotlin/runtime-ports/src/main`, `src/test`, and `src/testFixtures`; in
`runtime-infra-fs`: `GovernedReviewEvidenceEndpoint.kt`, `GovernedReviewEvidenceEndpointResponses.kt`,
`FileSystemReviewEvidenceBroker.kt`, `FileSystemDecompositionManifestFileStore.kt`,
`GitStandardWorkflowGitRemoteOperations.kt`, `AgentRunProcessRequestFields.kt`, `AgentRunAdapters.kt`,
`AgentRunCommandBuilders*.kt`, `GovernedReviewMcpConfigWriter.kt`, and their tests; in `runtime-infra-sqlite`:
`GoalRunnerControlCoordinator.kt`, `GoalRunnerControlStore.kt`, the `FeatureTask*Store.kt` and
`GoalChildWorkflowStore.kt` files that gain overrides, `GoalPlanningPreparationStore.kt`, and their tests; in
`runtime-mcp`: `GovernedReviewEvidenceBridge.kt`, `GovernedReviewEvidenceConnection.kt`,
`GovernedReviewEvidenceInitialization.kt`, `McpInputSchemas.kt`; in `runtime-application`:
`TelemetrySettingsLoading.kt`, `ParallelCodeReviewRunnerLaneLaunch.kt`, `ParallelCodeReviewRunnerModels.kt`,
`ParallelCodeReviewInlineCoverageContinuation.kt`, `WorkflowServiceIdentity.kt`,
`WorkflowServiceFeatureTaskIdentityRepair.kt`, `FeatureSpecPreparationWriter.kt`; in `runtime-engine`:
`GoalRunnerPurgeCoordinator.kt`, `FeatureTaskContinuationLookup*.kt`, `GoalPreflightInputValidation.kt`,
`GoalRunnerSelectedSubtaskLoop.kt`, `GoalPlanningPhaseAttemptGateBurstCap.kt`; in `runtime-domain`: a new
`skillbill.workflow.model` home for the execution identity and its policy; in `runtime-contracts`:
`skillbill.contracts.review` for the evidence constants and `skillbill.error` for the moved exceptions; in
`runtime-core` tests: `RuntimeContractModuleImportRulesTest`, `PrincipleEnforcementInventory`,
`PortNullObjectClassification`, and every test that constructs `RuntimeContext` with the flat constructor;
`runtime-kotlin/ARCHITECTURE.md` (the `runtime-ports` module bullet, the `skillbill.ports.*` package bullet, the
`RuntimeContractModuleImportRulesTest` paragraph, and the decomposition bundle journal paragraph); and
`runtime-kotlin/agent/decisions.md`.

**Evacuate behaviour.** Move `GovernedReviewEvidenceCodec`, `GovernedReviewEvidenceCodecWireParsing`,
`GovernedReviewEvidenceCodecWirePayloads`, `GovernedReviewEvidenceCodecWireSchemas`, and
`GovernedReviewEvidenceRequestParsing` into `runtime-infra-fs` package
`skillbill.infrastructure.fs.launcher.review` as `internal`; delete `GovernedReviewEvidenceCodecWire`. Declare
`READ_EVIDENCE`, `REQUEST_EXPANSION`, `OPERATIONS`, `SERVER_NAME`, `SOCKET_ENV`, `TOKEN_ENV`, `LANE_ENV`,
`REQUEST_BYTES`, and `RESPONSE_FRAME_BYTES` once in `runtime-contracts` `skillbill.contracts.review`
(`GovernedReviewEvidenceContracts.kt` or a sibling object) and point `runtime-infra-fs` and `runtime-mcp` at them.
The `runtime-mcp` bridge obtains tool specs by forwarding `tools/list` to the endpoint instead of calling
`toolSpecList()` locally. Move the lease extension functions and their private helpers from
`GoalRunnerControlRepository.kt` L48-130 into `runtime-infra-sqlite` beside `GoalRunnerControlCoordinator`, and
make `clearRunnerInterruptedPause` an abstract member implemented by `GoalRunnerControlStore`. Make
`writeBundleAtomically` an abstract member of `DecompositionManifestPersistencePort` overridden by
`FileSystemDecompositionManifestFileStore`; delete the ports extension and `DecompositionManifestBundleSnapshot`;
make `readTextWithoutRecovery`, `isRegularFileWithoutRecovery`, and `findDecompositionManifestFilesWithoutRecovery`
abstract. Move `FeatureTaskExecutionIdentity`, `FeatureTaskRouteScope`, `FeatureTaskWorkflowMode`, and
`FeatureTaskExecutionIdentityPolicy` to `runtime-domain` `skillbill.workflow.model` as one declaration each;
`FeatureTaskWorkflowCandidate` stays in ports because it carries `WorkflowStateRecord`; delete the
`ports.continuation` package. Replace `FeatureTaskWorkflowRowRepository` with abstract members on
`FeatureTaskWorkflowStateRepository` implemented by the SQLite store, with no `(this as X)` cast. Move
`FeatureTaskRuntimeSharedEvidenceResolverPort.NONE`'s derivation into the one production site that needs it, or
make the resolver non-optional at that site. Move `RepoValidationIssue.fromRawIssue` to the adapter that produces
raw issue strings.

**Honest defaults.** Every interface default whose body is `error(...)` or `throw` becomes abstract:
`GoalPlanningPreparationRepository.kt`, `WorkflowStateRepository.kt`, `FeatureTaskRuntimeWorkerRepository.kt`,
`ReviewEvidenceBroker.kt`, `DiffResolverPort.reviewWorktreeFileIdentities`,
`RejectedOutputDiagnosticRepository.retainProducerOutput`. Test fakes extend new `WorkflowStateRepositoryDefaults`,
`GoalPlanningPreparationRepositoryDefaults`, and `ReviewEvidenceBrokerDefaults` classes in
`runtime-ports/src/testFixtures`, reproducing the former defaults exactly, the way `UnitOfWorkDefaults` and
`GoalRunnerManifestStoreDefaults` already do. `WorkflowGitRemoteOperations` declares four abstract members; the
`NoopWorkflowGitRemoteOperations` fixture keeps the former bodies. Delete `TelemetrySettingsProvider.loadOrNull`;
`TelemetrySettingsLoading.kt` calls `load` and emits one `RuntimeDiagnostics` record naming the failure when it
falls back. Keep silent defaults only where the interface documents them as the contract (`hasObservedLaneResult`,
`knownPackSkillNames`, `readIfPresent`, `getFeatureImplementWorkflows` and its two siblings, `preparedPlanCount`,
`firstMissingPlan`); every other `= null`, `= 0`, `= Unit`, `= emptyList()`, `= emptyMap()` default becomes
abstract or is deleted with its dead operation.

**Delete.** Remove the discovery operation end to end: `ReviewEvidenceBroker.discover`, `expansionById`,
`confirmDelivery`, `recordMalformedRequest`, `finishDeliverySession`; `ReviewEvidenceDiscoveryRequest`,
`ReviewEvidenceCatalogEntry`, `ReviewEvidenceDiscoveryPage`, `REVIEW_DISCOVERY_PAGE_SIZE`,
`REVIEW_DISCOVERY_MAX_BYTES`, `REVIEW_EVIDENCE_MAX_REQUESTS`; the codec's `discoveryRequest`,
`discoveryPagePayload`, `discoveryCursor`, `discoveryPageSize`; and the `operation`, `cursor`, `page_size`
properties and `oneOf` branch of the `read_evidence` tool schema. Delete `NativeReviewOperationProtocol`,
`BrokerBackedNativeReviewOperationProtocol`, and `ReviewEvidenceLaneAccounting` (fold its members into
`ReviewEvidenceBroker`); `GovernedReviewEvidenceEndpointBinder.bind` takes a `ReviewEvidenceBroker`;
`SkillRunRequest` drops `nativeReviewOperations`. Delete `ConversationIsolation`; `SkillRunRequest` and
`AgentRunProcessRequestFields` use `skillbill.review.context.model.ReviewConversationIsolation`. Delete
`WorkflowSessionSummaryWire.kt`, `GoalVerificationContext`, `FeatureTaskRuntimeCrashLiveness`,
`FeatureTaskRuntimeWorkerAcquisition`, `WorkflowGitOperationResult.fromWire` and `invoke`, the five self-referential
typealiases, `ReviewFinishedTelemetryPayloadCompatibility.kt` (importers use `skillbill.ports.telemetry.model`).
Move `SequentialBoundedWorkFanOutPort` to `src/testFixtures`. Delete `StubGovernedReviewEvidenceEndpointBinder`,
`CheckpointHistoryGitOperationsRefusalTest`, and each fixture object that no test instantiates
(`EmptyAgentActivityStampRepository`, `UnavailableUnaddressedFindingsRepository`,
`UnavailableReviewRunLaneCompletenessRepository`, `UnavailableReviewRunStageCompletenessRepository`,
`NoopWorkflowGitRemoteOperations` only if no fake extends it after the defaults move, `NoopWorkflowGitCommitHistoryOperations`,
`NoopWorkflowGitBranchOperations`, `NoopWorkflowGitWorktreeOperations`, `NoopRepositoryFingerprintGitOperations`,
`NoopGoalSubtaskReviewGitOperations`, `UnavailableCheckpointHistoryGitOperations`), removing their rows from
`PortNullObjectClassification`.

**Exceptions and constructor.** Move `RejectedOutputDiagnosticError` and its cases,
`GoalRunnerLaunchAuthorizationDeniedException`, and `FeatureTaskRuntimeSharedEvidenceFingerprintContradictionError`
to `skillbill.error` in `runtime-contracts`, extending `SkillBillRuntimeException` (the denial) or
`ShellContentContractException` (the other two). `GoalRunnerWedgeClass.fromWire` returns
`GoalRunnerWedgeClass?`; the one caller that needs a failure throws a typed error. `RuntimeContext` keeps the
grouped primary constructor and loses the secondary constructor and the companion aliases; callers construct
`EnvironmentContext`, `TransportContext`, `WorkflowOpsContext`, and `OptionalCallbacks` directly.

**Guard.** Add `PortsDeclarationArchitectureTest` in `runtime-core` (or extend
`RuntimeContractModuleImportRulesTest`) that scans `runtime-ports/src/main` and asserts four empty lists: top-level
`object` declarations (companion objects allowed), top-level `class` declarations that are not `data`, `enum`,
`sealed`, or `value`, `(this as` occurrences, and interface default bodies containing `error(` or `throw`. Prove
the scanner on a synthetic fixture for each list. Register it in `PrincipleEnforcementInventory.enforceableRules`
and describe it in `ARCHITECTURE.md` beside the import rule.

## Acceptance Criteria

1. `find runtime-ports/src/main -name '*.kt' | xargs grep -lE '^(internal )?object '` returns nothing;
   `grep -rnE '^(internal |abstract |open )*class ' runtime-ports/src/main` matches only `data class`, `enum class`,
   `sealed class`, and `value class` lines; `grep -rn "(this as" runtime-ports/src/main` returns nothing;
   `grep -rnE 'error\("|throw ' runtime-ports/src/main` matches only lines inside `data class` `init` blocks or
   companion factories, none inside an `interface` body; the new architecture test asserts all four and fails on
   a synthetic fixture for each, and `PrincipleEnforcementInventory.enforceableRules` lists it.
2. `runtime-ports/src/main` contains no file named `GovernedReviewEvidence*Codec*.kt` or
   `GovernedReviewEvidenceRequestParsing.kt`; the codec objects are `internal` in
   `runtime-infra-fs/src/main/kotlin/skillbill/infrastructure/fs/launcher/review/`; `GovernedReviewEvidenceCodecTest`
   runs in `runtime-infra-fs` with its three assertions unchanged except for the deleted discovery case; the nine
   constants are declared once in `runtime-contracts` `skillbill.contracts.review` and
   `grep -rn "GovernedReviewEvidenceCodec\." runtime-mcp/src/main` returns nothing; a `runtime-mcp` test proves
   `tools/list` in bridge mode is answered by forwarding to the socket.
3. `DecompositionManifestPersistencePort` declares `writeBundleAtomically`, `readTextWithoutRecovery`, and
   `isRegularFileWithoutRecovery` abstract and `DecompositionManifestDiscoveryPort` declares
   `findDecompositionManifestFilesWithoutRecovery` abstract; `grep -rn "fun <T> DecompositionManifestPersistencePort"
   runtime-ports` returns nothing; a `runtime-infra-fs` test holds the store as `DecompositionManifestStore`,
   makes `verify` throw, and asserts the bundle journal marker was written and recovered, which fails against the
   pre-change extension.
4. `GoalRunnerControlRepository.kt` contains only an `interface` with abstract members;
   `grep -rn "fun GoalRunnerControlRepository\." runtime-ports/src/main` returns nothing; `GoalRunnerControlStoreTest`
   and `GoalRunnerControlCoordinator` exercise the lease algorithm from `runtime-infra-sqlite`, and the sqlite test
   for expired-lease release and heartbeat accumulation passes with unchanged assertions.
5. `grep -rn "object FeatureTaskExecutionIdentityPolicy" runtime-kotlin` matches exactly one file under
   `runtime-domain/src/main/kotlin/skillbill/workflow/model/`; `runtime-ports/src/main/kotlin/skillbill/ports/continuation`
   does not exist; `FeatureTaskExecutionIdentityPolicyTest` passes with unchanged assertions after its import
   changes; `FeatureTaskWorkflowRowRepository` is gone and `FeatureTaskWorkflowStateRepository` declares
   `saveFeatureTaskWorkflow`, `getFeatureTaskWorkflow`, `getFeatureTaskWorkflowAsMode`, `listFeatureTaskWorkflows`,
   `latestFeatureTaskWorkflow`, and `terminalizeLegacyProseFeatureTaskWorkflow` abstract.
6. `grep -rn "not implemented by this" runtime-ports/src/main` returns nothing; `WorkflowGitRemoteOperations`,
   `SharedGoalPreplanRepository`, `GoalSubtaskPlanRepository`, `FeatureTaskExecutionLookupRepository`,
   `GoalChildWorkflowStateRepository`, `FeatureTaskRuntimeWorkerRepository`, and `ReviewEvidenceBroker` have no
   default body other than the six documented delegations; `WorkflowStateRepositoryDefaults`,
   `GoalPlanningPreparationRepositoryDefaults`, and `ReviewEvidenceBrokerDefaults` exist in
   `runtime-ports/src/testFixtures` and every fake that compiled against a removed default now extends one;
   `TelemetrySettingsProvider` has one member and a `runtime-application` test proves a failing `load` yields one
   `RuntimeDiagnostics` record and the documented fallback.
7. `grep -rnE "discover\(|ReviewEvidenceDiscovery|REVIEW_DISCOVERY_|NativeReviewOperationProtocol|ReviewEvidenceLaneAccounting|ConversationIsolation\b|GoalVerificationContext|FeatureTaskRuntimeCrashLiveness|FeatureTaskRuntimeWorkerAcquisition|specInputTypes = listOf" runtime-ports/src/main`
   returns nothing; the advertised `read_evidence` schema has `requests` as its only property; `SkillRunRequest`
   has `reviewEvidenceBroker` and `reviewEvidenceEndpoint` and no `nativeReviewOperations`;
   `GovernedReviewEvidenceEndpointBinder.bind` takes `ReviewEvidenceBroker`; `RejectedOutputDiagnosticRepository.kt`
   has no `typealias`; `ReviewFinishedTelemetryPayloadCompatibility.kt` is gone and
   `grep -rn "import skillbill.ports.review.toReviewFinishedTelemetryPayload" runtime-kotlin` returns nothing;
   `SequentialBoundedWorkFanOutPort` is under `src/testFixtures`; `GovernedReviewEvidenceEndpointTest`,
   `AgentRunCommandBuildersTest`, and `JvmAgentRunProcessRunnerTest` pass with assertions changed only for the
   removed protocol parameter.
8. `grep -rnE "class .*: (RuntimeException|IllegalStateException|IllegalArgumentException|Exception)\(" runtime-ports/src/main`
   returns nothing; `RejectedOutputDiagnosticError`, `GoalRunnerLaunchAuthorizationDeniedException`, and
   `FeatureTaskRuntimeSharedEvidenceFingerprintContradictionError` are declared under
   `runtime-contracts/src/main/kotlin/skillbill/error/` extending `SkillBillRuntimeException` or a subtype;
   `GoalRunnerWedgeClass.fromWire` returns a nullable; `GoalRunnerSelectedSubtaskLoop`,
   `GoalPlanningPhaseAttemptGateBurstCap`, `RejectedOutputDiagnosticService`, and `SqliteRejectedOutputDiagnosticRepository`
   tests pass with unchanged assertions.
9. `RuntimeContext` has exactly one constructor and no `companion object`;
   `grep -rn "RuntimeContext(" runtime-kotlin --include='*.kt' | grep -v "EnvironmentContext\|: RuntimeContext\|fun "`
   matches only calls whose first argument is an `EnvironmentContext`; `ARCHITECTURE.md` L137-139 lists the four
   group types as the constructor shape.
10. `StubGovernedReviewEvidenceEndpointBinder.kt` and `CheckpointHistoryGitOperationsRefusalTest.kt` do not exist;
    every object under `runtime-ports/src/testFixtures` is referenced by at least one test or fixture outside
    `PortNullObjectClassification.kt`; `PortNullObjectClassification.classifiedObjects` has no row for a deleted
    fixture; `PortNullObjectAbsenceArchitectureTest` passes.
11. `./gradlew :runtime-ports:test :runtime-infra-fs:test :runtime-infra-sqlite:test :runtime-application:test :runtime-engine:test :runtime-mcp:test :runtime-core:test`
    passes with zero failures; the four `runtime-ports-*` baselines are unchanged and empty;
    `RuntimeContractModuleImportRulesTest`, `RuntimeRawMapArchitectureTest`, and `RuntimeLayerBoundaryArchitectureTest`
    pass; `ARCHITECTURE.md` describes the codec's new home, the abstract bundle write, and the declaration guard;
    `decisions.md` records the evacuation, the discovery deletion, and the constructor choice.

## Non-goals

No change to payload key literals, `GoalChildWorkflowDeletionScope`, `WorkflowGitOperationResult`'s token
strings, `ReviewAccountingRecord`'s validator, `ReviewFinishedTelemetryPayload`'s keys, the three raw-map
wrappers, or the raw-map scanner; subtask 2 owns those. No change to `runtime-mcp` framing beyond the import
change and the `tools/list` forwarding; SKILL-357 owns the bridge. No `FileLocation` migration. No change to the
`GoalRunnerManifest*` seams or the `WorkflowFamily` extensions.

## Dependency notes

Depends on: none within this goal. Coordinate with SKILL-357 subtask 1, which rewrites
`GovernedReviewEvidenceBridge.kt` and deletes `GovernedReviewEvidenceInitialization.kt`: if SKILL-357 has landed,
change only the constant imports and the `tools/list` forwarding in the new framer; if it has not, make the same
two changes in the current bridge files. SKILL-357 subtask 2 deletes `McpInputSchemas.kt`; until then its import
of the policy moves to the domain package. Rebase on `main` and re-run the reference census before editing.

## Validation strategy

Name the regression before each test: a bundle write through the interface that skips the journal, a lease
released by a non-owner, a fake that compiles against a default the adapter never had, a remote-branch check that
reports pushed without a git call, a telemetry settings failure with no record, a `discover` call that the schema
invites and the broker throws on, a denied launch classified as a programming error, a duplicate policy that drifts
from its twin, a new `object` landing in ports without a failing guard. Drive the guards with synthetic fixtures.
Run the module suites listed in AC-011, `./gradlew check` on `runtime-kotlin`, the pack-declared quality gate, and
`bill-unit-test-value-check` for changed tests.

## Next path

Continue to `spec_subtask_2_one-owner-for-every-wire-token.md` through the goal runtime after this subtask
settles.

## Spec Path

.feature-specs/SKILL-358-runtime-ports-boundaries-and-simplicity/spec_subtask_1_ports-hold-interfaces-and-dtos.md
