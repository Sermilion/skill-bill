# SKILL-372 subtask 2 — task-1 implementation baseline

Recorded at implement phase entry on branch `feat/SKILL-372-runtime-domain-hexagonal-boundaries` after subtask 1 (`status: complete` in decomposition manifest). This file is the family ownership and validation matrix tasks 2–6 consume; it does not claim subtask 2 acceptance criteria are met.

## Subtask 1 aggregate and mapping (recheck)

| Surface | Location | Baseline fact |
| --- | --- | --- |
| Typed snapshot | `runtime-domain/.../workflow/engine/model/WorkflowModels.kt` | `WorkflowStateSnapshot` carries `steps: List<WorkflowStepState>`, `artifacts: DurableWorkflowArtifacts`, `mode: FeatureTaskWorkflowMode?`, `Instant?` timestamps — no row JSON on the aggregate. |
| Artifact wrapper | `runtime-domain/.../DurableWorkflowArtifacts.kt` | Strict `fromAny` / `fromMap`; no lenient empty-map parse on corrupt text. |
| Port mapping | `runtime-ports/.../WorkflowRecordMapping.kt` | `toSnapshot()` strict-decodes `stepsJson` / `artifactsJson`; `mapToRecord()` uses `preserveArtifactTimestampText` and `encodeInstant` (subtask 1 timestamp encoders — do not re-normalize). |
| Step attempt count | `WorkflowRecordMapping.kt` `toExactIntOrNull` | Exact integral coercion at steps decode seam (distinct from artifact-map reader). |
| Engine updates | `runtime-domain/.../WorkflowEngine.kt` | `openRecord` / `updateRecord` call `validateWorkflowOpen` / `validateWorkflowUpdate` internally; no `WorkflowSnapshotValidator` parameter remains on `WorkflowEngine`. |
| Legacy mapping | Engine/SQLite | Some paths still use `stepsJson`/`artifactsJson` column text or parallel codecs — inventory below; task 3–4 migrate reads/writes to typed snapshot + domain accessors. |

## Artifact family ownership matrix (F-005)

Columns: **key(s)**, **domain decoder/encoder owner**, **planned `DurableWorkflowArtifacts` accessor package**, **write seams to validate before persist**, **external raw access today**.

| Family | Wire key(s) | Domain codec / model | Accessor home (target) | Primary write seams | Raw/literal access outside domain (main) |
| --- | --- | --- | --- | --- | --- |
| Phase records / ledger | `feature_task_runtime_phase_records`, `feature_task_runtime_phase_ledger` | `FeatureTaskRuntimePhaseRecord`, ledger entry types; wire in `taskruntime/artifact/FeatureTaskRuntimeWorkflowArtifactWire.kt` + duplicates in `phaseartifacts/` | `skillbill.workflow.taskruntime.artifact` | Engine `FeatureTaskRuntimeWorkflowPersistence`, SQLite `FeatureTaskRuntimeSqliteArtifactWire`, application phase decoders | Engine `FeatureTaskRuntimePhaseArtifactDecoders.kt`; application `decodeFeatureTaskRuntimePhaseRecords`; resolver private copy |
| Goal continuation | `goal_continuation` | `FeatureTaskRuntimeGoalContinuationArtifact` | Same package as model (`.../goal/FeatureTaskRuntimeGoalContinuationArtifact.kt`) | Engine goal-runner persist, SQLite control coordinator | Engine + SQLite `goalContinuation(artifacts)` lenient null; literals in `DecompositionManifestRuntimeStateDerivation` |
| Goal continuation outcome / field adoption | `goal_continuation_outcome`, field adoption key | Goal continuation / outcome decoders | taskruntime artifact + goalrunner | Engine/SQLite goal-runner | Literal reads in decomposition runtime derivation |
| Decomposition runtime | `decomposition_runtime` | `DecompositionManifestWireCodec`, runtime keys in `decomposition/runtime/` | `skillbill.workflow.decomposition.runtime` | Parent projection writers (engine + SQLite), manifest loaders | Snapshot extensions in application + SQLite (twins) |
| Projection failure | `decomposition_manifest_projection_failure` | Entry shape in domain (task 5) | `decomposition.runtime` | Application + SQLite projection failure persistence (twins) | — |
| Commit-push result | (decomposition / goal artifacts) | Commit projection tests | decomposition + taskruntime | Application commit projection | Literals in runtime state derivation |
| Phase output / handoff / planning | Multiple `FeatureTaskRuntimeWireArtifactKind` values | Handoff + phase output models | `taskruntime.artifact` + handoff packages | Phase completion in engine/application | Via validator-in-domain call sites |
| Operator block retry | `operator_block_retry` | Phase artifact decoders | taskruntime artifact | `FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint` (SKILL-378 F-010) | Engine phase decoders raw map |
| Readiness / validation / gate evidence | readiness, validation, gate progress keys | `FeatureTaskRuntimeReadinessEvidence`, validation evidence types | `taskruntime.model.validation` | Phase persistence | `asIntegerOrNull` truncating paths (task 2) |
| Goal observability / progress | four `goal_*_event` / history keys | `GoalObservabilityParsing`, progress models | `workflow.goal.model` | Progress recording (engine/SQLite/MCP) | MCP + SQLite raw reads; validator params in parsing |
| Goal review state / results | review state keys | `GoalSubtaskReviewStateDecoding` | `workflow.goal.model` | Review persistence engine/SQLite | Twin review decode in engine/SQLite codecs |
| Attempt ledger | `goal_attempt_ledger` | `AttemptLedgerDecoding` | `goalrunner` | Ledger writers | — |
| Worker subtask outcomes | `goal_worker_subtask_request_outcomes` | `GoalWorkerSubtaskRequestArtifactCodec` | `goalrunner` | Worker request persist | `asGoalRunnerIntOrNull` (truncating) |
| Run invariants / checkpoint / quarantine / diagnostics | various `feature_task_runtime_*` | Persistence model files | Respective `taskruntime.model.*` packages | Engine audit/quarantine paths | Mostly domain-internal reads today |
| Engine-only | `repository_evidence` | `WorkflowInputProjectionSelector` | Stays engine | Engine input projection | Engine-only by spec |
| Legacy goal control | `goal_review_policy`, `goal_out_of_band_acceptances` | SQLite migration decoders | **Not domain** — stay SQLite with ledger migration | SQLite control migration | Accepted duplicate with application until migration retires |

**Key visibility (AC-001 baseline):** all domain `*_ARTIFACT_KEY` constants in `FeatureTaskRuntimePersistenceArtifactKeys.kt`, goal keys, decomposition keys, etc. remain **public** `const val` — task 3 makes them `internal`.

## Validation matrix (F-007 → task 4)

Rule for migration: **validate wire JSON at adapter/application before domain decode; validate encoded payload before any durable write** (including direct engine/SQLite repository saves identified below).

### Validator interfaces still declared in runtime-domain

| Validator | Domain declaration | Production impl | Domain call sites (count) | Target seam |
| --- | --- | --- | --- | --- |
| `FeatureTaskRuntimeWireArtifactValidator` + kind enum | `taskruntime/model/core/` | `infra/contracts` class (name collision — rename in task 4) | 12 forwarding extensions in `FeatureTaskRuntimeWireArtifactValidatorExtensions.kt`; handoff projection inputs; phase wire mappings | Callers of extensions → direct `validate(kind, typedCarrier, label)` in ports; infra/contracts adapter |
| `FeatureTaskRuntimePhaseOutputValidator` | `taskruntime/phase/task/` | infra/contracts + engine | Phase output normalization callers (3) | Engine/application adapter before `normalizePhaseOutput` |
| `DecompositionManifestValidator` | `workflow/decomposition/` | infra/contracts | Snapshot `decompositionRuntime(validator)` twins; manifest load/save | Application decomposition services + SQLite manifest access before decode |
| `InstallPlanWireValidator` | `install/model/` | infra/contracts | `InstallPlanPolicy`, `InstallPlanWireMap` | Install application/infra gateway |
| `ReviewContextEnvelopeValidator` | `review/context/` | infra/contracts | Review context packet assembly | Review application + infra review stage |
| Goal validator **typealiases** (3) | `workflow/goal/*Validator.kt` | Same wire validator | `GoalObservabilityParsing`, `GoalObservabilityArtifacts` | Collapse to wire validator at observability adapter |

**Domain testFixtures to delete (task 4):** `NoopFeatureTaskRuntimeWireArtifactValidator`, `NoopGoalProgressEventValidator`, `NoopGoalObservabilityEventValidator`.

**Forwarding extensions to delete (task 4):** all 12 in `FeatureTaskRuntimeWireArtifactValidatorExtensions.kt` (kinds: quarantine, planning projection, implementation attempt, build receipt, handoff declaration/persistence/measurement/shared evidence, envelope, goal progress/observability events, goal planning preparation envelope).

### Persistence entry points needing write-boundary validation inventory

| Module | Entry | Validates today? | Notes |
| --- | --- | --- | --- |
| `runtime-engine` | `FeatureTaskRuntimeWorkflowPersistence`, goal-runner persist packages | Partial — some artifacts via domain validators inside decode | Task 4: validate encoded map before `updateRecord` / store |
| `runtime-infra/sqlite` | `WorkflowStateWrites`, goal-runner control/manifest stores | Snapshot validator on some paths; artifact writes vary | Task 4: malformed artifact must not persist (AC-006) |
| `runtime-application` | `WorkflowService` update, decomposition manifest file writes | `WorkflowService` uses engine validate path for one update route | Direct saves in decomposition need same gate |

## Schema rejection test inventory (relocate in task 4, preserve input + error type)

| Error family | Representative tests (module) | Current decode/validate locus | Destination seam |
| --- | --- | --- | --- |
| `InvalidWorkflowStateSchemaError` | `WorkflowServiceTest`, `WorkflowStateValidationPersistenceTest`, `FeatureTaskRuntimeStatusServiceTest`, `WorkflowStateStoreTest`, `GoalRunnerControlStoreTest`, domain `FeatureTaskRuntimePersistenceModelsTest`, `WorkflowEngineUpdateTest` | Port `toSnapshot`, engine `WorkflowService`, domain engine validation | Port mapping + engine/application service before aggregate mutation; keep domain pure rule tests |
| `InvalidDecompositionManifestSchemaError` | `WorkflowServiceTest`, `FeatureSpecPreparationWriterTest`, application decomposition tests | Domain `DecompositionManifestValidator` + caller param | Ports validator + application/SQLite before `decodeManifest` |
| `InvalidGoalObservabilityEventSchemaError` / progress | `GoalObservabilityModelsTest`, `WorkflowServiceTest` | Domain parsing with validator param | Observability adapter after wire validate |
| `InvalidGoalSubtaskReviewStateSchemaError` | `WorkflowGoalRunnerOutcomeStoreTaskRuntimeTest` | Domain decode | Engine/SQLite store adapter |
| `InvalidFeatureTaskRuntimePhaseOutputSchemaError` | `WorkflowServiceTest` | Phase output validator | Engine phase adapter |
| `InvalidGoalPlanningPreparationSchemaError` | SQLite `GoalPlanningPreparationStoreTest` | Domain + store | SQLite store adapter |
| `InvalidReviewContextSchemaError` | `ReviewStageStatePersistenceTest` | Review envelope validator | Infra review stage |
| `InvalidFeatureTaskRuntimeWorkerOwnershipSchemaError` | `WorkflowStateStoreTest` | Worker ownership decode | SQLite/engine adapter |

**Goal continuation gap (AC-003):** engine `GoalContinuationArtifactCodec.goalContinuation` and SQLite twin return `null` on malformed/missing fields instead of `FeatureTaskRuntimeGoalContinuationArtifact` typed errors — task 3 routes through domain accessor; task 3 tests add engine/SQLite recovery regressions.

## Fourteen diverged twins — main-source definitions (AC-004 baseline)

Target: one definition each; pure rules to domain; shells stay engine/application.

| Symbol | Definitions (module paths) | Disposition |
| --- | --- | --- |
| `clearDecompositionManifestProjectionFailure` | application `decomposition/DecompositionManifestProjectionFailurePersistence.kt`; sqlite `goalrunner/manifest/DecompositionManifestProjectionFailurePersistence.kt` | Collapse shell; domain entry shape |
| `persistDecompositionManifestProjectionFailure` | same pair | same |
| `decompositionRuntime` | application `DecompositionWorkflowRuntimeLookup.kt`; sqlite `workflow/decomposition/DecompositionWorkflowRuntimeLookup.kt` | Domain decode + single extension |
| `hasDecompositionPlan` | same pair | Domain predicate |
| `isGoalContinuationChildWorkflow` | same pair | Domain predicate |
| `findDecomposedParentOrCorruptFallback` | application + sqlite `DecompositionWorkflowRuntimeLookupParentDiscovery.kt` | Domain rule + one repository shell |
| `findMatchingDecompositionManifests` | application `DecompositionManifestRuntimeState.kt`; engine `DecompositionManifestEngineEncoding.kt`; sqlite `DecompositionManifestAccess.kt` | Reconcile three-way; keep Path in application owner |
| `loadDecompositionManifest` | application `DecompositionManifestFileWrites.kt`; sqlite `DecompositionManifestAccess.kt` | Application owner; sqlite routes via port |
| `resolveDecompositionManifest` | application `DecompositionManifestRuntimeState.kt`; sqlite `DecompositionManifestAccess.kt` | Collapse into application (spec) |
| `goalContinuation` | engine + sqlite `GoalContinuationArtifactCodec.kt` | Delete; use `FeatureTaskRuntimeGoalContinuationArtifact` accessor |
| `goalReviewArtifacts` / `validatedGoalReviewPasses` / `goalReviewEmissionEnvelope` | engine + sqlite same file | Domain decode; delete twins |
| `missingResultPrefixTerminalOutcomeArtifact` | engine `GoalTerminalOutcomeDerivationSweepAggregate.kt`; sqlite `GoalContinuationOutcomeSelection.kt` | Single domain rule |
| `goalRepositoryIdentity` | engine `GoalRepositoryIdentity.kt` only (Path + port — not domain) | Single engine impl (SKILL-371) |

**Shell files to collapse after rules move (task 5):** `GoalParentProjectionWriter` (engine + sqlite), `GoalContinuationArtifactCodec`, `DecompositionWorkflowRuntimeLookup`(+ParentDiscovery), `DecompositionManifestProjectionFailurePersistence`, engine `FeatureTaskRuntimePhaseArtifactDecoders.kt`.

## Integer coercion inventory (F-008 → task 2)

| Implementation | Location | Semantics |
| --- | --- | --- |
| **Exact (canonical)** | `DurableArtifactMapReader` + `asExactIntOrNull` / `asExactLongOrNull` | `BigDecimal.intValueExact`, integral strings |
| **Exact duplicate bodies** | `WorkflowEngineNumericCoercion.kt`, `ReviewRunLaneSegmentAccountingJson.kt` private copy | Delete; import domain reader helpers |
| **Exact duplicate at port** | `WorkflowRecordMapping.toExactIntOrNull` | Steps JSON only — keep at port (not artifact map) |
| **Truncating `Number.toInt` / string** | `asGoalRunnerIntOrNull` (`GoalWorkerSubtaskRequestArtifactCodec.kt` + engine `GoalContinuationArtifactCodecWireDecode.kt` extension) | Migrate to exact + family typed error |
| **Truncating private** | `asGoalObservabilityIntOrNull` in `GoalObservabilityArtifacts.kt` | Migrate |
| **Whole-number Double check** | `FeatureTaskRuntimePlanOutcomeDecoders.asIntOrNull` | Migrate |
| **Long-only exact (no BigDecimal)** | three `asIntegerOrNull` in readiness/validation/gate evidence files | Migrate to reader |
| **Documented lenient (decision 2026-09-16)** | `AttemptLedgerWorkflowDecoding.asLenientIntOrNull` | **Symbol missing post–subtask 1 tree** — task 2 must restore sole lenient helper per runtime-domain `../../../agent/decisions.md` (c); preserve historical lenient regression once reintroduced |

## Compatibility fixtures to preserve (bytes / ordering / timestamps)

Do not re-run in implement phase; validate phase executes.

| Fixture class | Where exercised | Preserve |
| --- | --- | --- |
| Phase record/ledger round-trips | `FeatureTaskRuntimePersistenceModelsTest`, phase projection tests | `toArtifactMap()` bytes, field omission, edge_iteration handling |
| Goal continuation + review state combos | `GoalSubtaskReviewStateTest` | Multi-artifact maps with continuation + review keys |
| Timestamp text on unchanged instant | `FeatureTaskRuntimePhaseTimestampTest` (engine), `WorkflowRecordMapping` encoders | Legacy `uuuu-MM-dd HH:mm:ss`, offset fractions, blank finished_at |
| Decomposition manifest wire | `DecompositionManifestCommitProjectionTest`, engine decomposition tests | `encodeManifestWireMap` bytes for parent rewrites |
| Goal-parent projection / legacy control migration | Engine/SQLite goal-runner tests (task 5 baseline matrix) | Byte-identical artifact maps after consolidation |
| Handoff / phase output rejection cases | Engine `WorkflowServiceTest` phase output tests | Same rejected payload + error type at new seam |

Subtask 1 encoders: **`encodeInstant` / `preserveArtifactTimestampText`** in `runtime-ports/.../WorkflowArtifactTimestampMapping.kt` and `WorkflowRecordMapping.kt` — treat as frozen for subtask 2.

## Task dependency note

Tasks 2–6 must read this baseline before removing validator parameters or duplicate coercions. Task 1 makes **no production Kotlin changes**; matrices above supersede investigation prose where the post–subtask 1 tree differs (typed aggregate landed; lenient artifact JSON decoders largely removed; `AttemptLedgerWorkflowDecoding.asLenientIntOrNull` not yet present).
