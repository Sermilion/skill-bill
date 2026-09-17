# SKILL-358 Subtask 2 - One owner for every wire token

Parent spec: [.feature-specs/SKILL-358-runtime-ports-boundaries-and-simplicity/spec.md](./spec.md)
Issue key: SKILL-358

## Scope

Resolve F-005 in [investigation.md](investigation.md).

Own `runtime-ports/src/main/kotlin/skillbill/ports/workflow/model/GoalChildWorkflowDeletionScope.kt`,
`ports/workflow/gitops/model/WorkflowGitOperationResult.kt`, `WorkflowGitOperationStatus.kt`,
`GoalSubtaskReviewGitModels.kt`, `GoalSubtaskReviewInputWireMap.kt`, `ports/review/model/ReviewAccountingRecord.kt`,
`ReviewAccountingBoundedPayload.kt`, `ports/review/ReviewRepository.kt`,
`ports/telemetry/model/ReviewFinishedTelemetryPayload.kt`, `ports/idestatus/IdeStatusWireMap.kt`,
`IdeStatusValidator.kt`, and their tests; in `runtime-contracts`: `skillbill.contracts.review` (new
`ReviewAccountingPayloadKeys`, `ReviewFinishedTelemetryPayloadKeys`, and the remaining
`GovernedReviewEvidencePayloadKeys` entries), `skillbill.contracts.workflow` for the goal-subtask review input
keys; in `runtime-domain`: `skillbill.idestatus.model` for a typed IDE status snapshot if none exists,
`skillbill.review.context.model.ReviewAccountingModels.kt`; in `runtime-application`:
`ReviewAccountingProjection.kt`, `idestatus/model/IdeStatusModels.kt`; in `runtime-infra-sqlite`:
`review/ReviewAccountingPersistence.kt`, `workflow/GoalChildWorkflowStore.kt`; in `runtime-infra-fs`:
`IdeStatusValidatorAdapter.kt`, the moved codec payload builders; in `runtime-engine`:
`FeatureTaskRuntimeGoalReviewPassRecorder.kt`, `FeatureTaskRuntimeGoalReviewInputBuilder.kt`; in `runtime-core`
tests: `RuntimeArchitectureTestSupport.isBoundaryCarrierRawMapDeclaration`, `RuntimeRawMapArchitectureTest`,
`WireVocabularyGovernedSeamInventory`; a new repo-contract parity test; `runtime-kotlin/ARCHITECTURE.md` (boundary
rule 11 paragraph and the closed status-family inventory); and `runtime-kotlin/agent/decisions.md`.

**Tokens.** `GoalChildWorkflowDeletionScope` carries `List<WorkflowStatus>`; `GoalChildWorkflowStore` binds
`wireValue` at the SQL seam. `WorkflowGitOperationResult.Ok` and `Failed` expose `status: WorkflowGitOperationStatus`
and derive `wireValue` from it; the `"ok"`/`"error"` literals appear only in `WorkflowGitOperationStatus`.

**Keys.** Every payload key written or checked in `runtime-ports` becomes a `runtime-contracts` constant:
`ReviewAccountingPayloadKeys` for the accounting summary, node, counters, evidence delivery, and segment keys;
`ReviewFinishedTelemetryPayloadKeys` for the review-finished branch (reusing `LifecycleTelemetryPayloadKeys`,
`ReviewFindingPayloadKeys`, and `ReviewVerificationSignalKeys` where a constant exists); the thirteen missing
`GovernedReviewEvidencePayloadKeys` entries; and a `GoalSubtaskReviewInputPayloadKeys` object for the four review
input keys. Add one repo-contract test that pins `ReviewAccountingPayloadKeys` to the `accounting_summary`,
`accounting_counters`, and `evidence_delivery` `required` and `properties` lists in
`orchestration/contracts/review-context-schema.yaml`, and `ReviewFinishedTelemetryPayloadKeys` to the
`skillbill_review_finished` branch of `telemetry-event-schema.yaml`.

**Typed carriers.** `ReviewAccountingRecord` carries `summary: ReviewAccountingSummary` from `runtime-domain`
instead of a bounded map; `ReviewAccountingProjection.toBoundedPayload` becomes the SQLite adapter's private
serializer and `ReviewAccountingPersistence` deserialises into the typed summary; the hand-rolled key validator in
`ReviewAccountingRecord.kt` L18-137 is deleted and the schema check stays at the adapter that already validates
`review-context-schema.yaml`. `GoalSubtaskReviewInput.toArtifactMap()` and `GoalSubtaskReviewInputWireMap` are
deleted; the two engine writers project the four fields with the contracts keys. `IdeStatusValidator.validate`
takes the typed IDE status model; `IdeStatusValidatorAdapter` projects it to the wire map privately before schema
validation; `IdeStatusWireMap` and `IdeStatusModels.toStatusWireMap` are deleted or made private to the adapter.

**Guard.** Narrow `isBoundaryCarrierRawMapDeclaration` so the name-suffix exemption does not apply to public
declarations under `runtime-ports/src/main`; register every `runtime-ports` file that still writes a payload
(`ReviewFinishedTelemetryPayload.kt`) in `WireVocabularyGovernedSeamInventory`.

## Acceptance Criteria

1. `GoalChildWorkflowDeletionScope.deletableStatuses` has type `List<WorkflowStatus>`;
   `grep -rnE '"(blocked|failed|abandoned|completed|pending|paused)"' runtime-ports/src/main` returns nothing;
   `GoalChildWorkflowStore` binds `wireValue` and its deletion test passes with unchanged assertions.
2. `grep -rn '"ok"\|"error"' runtime-ports/src/main` matches only `WorkflowGitOperationStatus.kt`;
   `WorkflowGitOperationResult.Ok.wireValue` and `Failed.wireValue` derive from `WorkflowGitOperationStatus`;
   `WorkflowGitOperationResultTest` passes with the two `fromWire` cases removed.
3. `grep -rnE '"[a-z_]+" to |\["[a-z_]+"\]|== "[a-z_]+"' runtime-ports/src/main` returns nothing;
   `ReviewAccountingPayloadKeys`, `ReviewFinishedTelemetryPayloadKeys`, and `GoalSubtaskReviewInputPayloadKeys`
   exist in `runtime-contracts`; `GovernedReviewEvidencePayloadKeys` declares every key the moved codec writes and
   the codec files in `runtime-infra-fs` contain no inline key; a repo-contract test pins the two key objects to
   their YAML branches and fails on a synthetic missing or extra key.
4. `ReviewAccountingRecord` has fields `reviewId`, `packetDigest`, `summary: ReviewAccountingSummary` and no map;
   `ReviewAccountingBoundedPayload.kt` does not exist; `requireBoundedAccountingPayload` does not exist;
   `ReviewAccountingPersistence` round-trips a summary through SQLite byte-identically to the current stored JSON,
   proven by a test that compares the persisted `payload_json` for a fixture summary against the pre-change value;
   `ReviewAccountingProjectionRedactionTest` passes with assertions changed only for the typed field.
5. `GoalSubtaskReviewInputWireMap.kt` does not exist; `GoalSubtaskReviewInput` has no `toArtifactMap`;
   `FeatureTaskRuntimeGoalReviewPassRecorder` and `FeatureTaskRuntimeGoalReviewInputBuilder` write the four keys
   from `GoalSubtaskReviewInputPayloadKeys`; the goal review input artifact JSON is byte-identical, proven by the
   existing engine tests over the artifact map.
6. `IdeStatusWireMap.kt` does not exist; `IdeStatusValidator.validate` takes a typed model declared in
   `runtime-domain` or `runtime-ports`; `IdeStatusValidatorAdapter` owns the map projection as a private function;
   `IdeStatusServiceTest` and `IdeStatusReadSnapshotConcurrencyTest` pass with unchanged assertions.
7. `grep -rn "Map<String, Any?> by" runtime-ports/src/main` returns nothing; `isBoundaryCarrierRawMapDeclaration`
   returns false for any public declaration under `runtime-ports/src/main` regardless of suffix, proven by a
   synthetic `class FooMap(private val delegate: Map<String, Any?>) : Map<String, Any?> by delegate` fixture under
   a ports path; `RuntimeRawMapArchitectureTest` passes on the real tree.
8. `./gradlew :runtime-ports:test :runtime-infra-sqlite:test :runtime-infra-fs:test :runtime-application:test :runtime-engine:test :runtime-core:test`
   passes with zero failures; `WireVocabularyArchitectureTest` passes with `ReviewFinishedTelemetryPayload.kt`
   registered; `ARCHITECTURE.md` boundary rule 11 no longer describes a suffix exemption for ports, and the closed
   status-family inventory names `GoalChildWorkflowDeletionScope` as owned by `WorkflowStatus`; `decisions.md`
   records the typed accounting record and the scanner change.

## Non-goals

No change to interface membership, defaults, the codec's location, exceptions, or `RuntimeContext`; subtask 1 owns
those. No change to the YAML schemas' content. No change to the `ReviewFinishedTelemetryPayload` projection's
location or output shape; only its keys become constants. No change to what the SQLite tables store.

## Dependency notes

Depends on: subtask 1. It edits files subtask 1 reshapes (`ReviewAccountingRecord.kt` beside the moved codec,
`WorkflowGitOperationResult.kt` after `fromWire` is deleted, the codec payload builders in their `runtime-infra-fs`
home). Rebase on the branch head and re-run the literal-key census before editing.

## Validation strategy

Name the regression before each test: a deletion scope token that drifts from `WorkflowStatus`, a git result
status spelled two ways, an accounting key that the YAML requires and the Kotlin omits, a review-finished event
rejected by the telemetry schema after a key rename, a stored accounting JSON that changes shape, a raw-map wrapper
that passes the guard by suffix. Compare persisted JSON and artifact maps byte for byte against pre-change fixtures.
Run the module suites in AC-008, `./gradlew check` on `runtime-kotlin`, the pack-declared quality gate, and
`bill-unit-test-value-check` for changed tests.

## Next path

Goal complete after this subtask settles; the runtime finalises history and decisions entries.

## Spec Path

.feature-specs/SKILL-358-runtime-ports-boundaries-and-simplicity/spec_subtask_2_one-owner-for-every-wire-token.md
