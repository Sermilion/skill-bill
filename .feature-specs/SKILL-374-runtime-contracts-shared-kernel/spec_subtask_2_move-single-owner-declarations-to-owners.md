# SKILL-374 subtask 2 - Move single-owner declarations to their owners

Parent: `spec.md`. Evidence: `investigation.md` F-002, F-003, F-007, F-008, F-009. Depends on
subtask 1.

## Scope

Placement rule: a declaration stays in `runtime-contracts` when two or more production
modules read or write it, or a `runtime-ports` signature exposes it. Otherwise it moves to
its one owner. Schema locators belong to `runtime-infra/contracts`, which stages the
resources. Moves keep class names, member names, wire values, and `toPayload()` output
byte-identical; only packages and modules change. Use each owner's existing area package
(for example `skillbill.application.learning`, `skillbill.infrastructure.http`) and respect
the package sibling limits. Before moving, re-run the investigation's consumer census, and
move only items that still have one owner.

1. **DTOs and their same-file keys (F-002).**
   - To `runtime-application`: `LearningEntryDto`, `LearningSummaryWire`,
     `LearningAppliedSessionWire`, `LearningListContract`, `LearningRecordContract`,
     `LearningResolveContract`, `LearningDeleteContract`, `LearningPayloadKeys`;
     `ReviewPreviewContract`, `ImportedReviewContract`, `ReviewFeedbackContract`,
     `NumberedFindingContract`, `TriageDecisionContract`, `TriageListContract`,
     `TriageRecordedContract`; the five `Lifecycle*Contract` classes; `DoctorContract`;
     `DecompositionManifestProjectionOperations`.
   - To `runtime-mcp`: `McpReviewImportSkippedContract`, `McpTriageSkippedContract`,
     `McpLearningsSkippedContract`, `McpOrchestratedPayloadContract`.
   - To `runtime-infra/http`: `TelemetryProxyBatchEvent`, `TelemetryProxyBatchPayload`,
     `RemoteStatsQueryPayload`.
   - To `runtime-infra/launcher`: `GovernedReviewWirePayload`, `GovernedReviewToolSpecList`,
     `GovernedReviewEvidencePayloadKeys`. `GovernedReviewEvidenceContracts` (the constants
     object read by runtime-mcp and an infra:skills repoTest) stays in its own file in
     runtime-contracts.
   - To `runtime-domain`: `InstallPlanContract`, `InstallPlanPayloadKeys`,
     `ReadinessEvidencePayloadKeys`.
   - Stay: `UpdateCheckContract` and `UpdateCheckPayloadKeys` (SKILL-371 subtask 2 makes
     CLI a second consumer), `VersionContract`, `RuntimeProvenanceContract`, `toRuntimeProvenance`,
     `DecompositionPlanningResult` and its wire types, `ScaffoldPayloadParsing`,
     `TelemetryProxyPayloadKeys`, and `RepoValidationReportContract` and
     `ReleaseRefMetadataContract` with their keys. Do not move or edit
     `FeatureImplementSessionSummaryContract`, `FeatureVerifySessionSummaryContract`, or
     `WorkflowSessionSummaryPayloadKeys`: SKILL-377 subtask 2 deletes them.
2. **Keys objects (F-002).** To `runtime-engine`: `GoalPlanningSharedContextPacketPayloadKeys`,
   `GoalSubtaskReviewInputPayloadKeys`, `ImplementationReturnContractPayloadKeys`. To
   `runtime-cli`: `GoalRunnerPurgePayloadKeys`, `GoalRunnerResetPayloadKeys`. To `runtime-infra/sqlite`: `GoalTelemetryPayloadKeys`,
   `SqliteLifecycleTelemetryMaterializationPayloadKeys`, `SqliteReviewTelemetryPayloadKeys`.
   To `runtime-infra/workflow`: `DecompositionManifestBundleJournalPayloadKeys`. Leave
   every experiment declaration (`contracts/experiment/**`, experiment `*SchemaPaths`, and
   `Experiment*` errors) untouched: SKILL-378 subtask 1 deletes them. Update `WireVocabularyGovernedSeamInventory` imports; its reflection is unchanged.
3. **SQLite shadow keys (F-003).** The two SQLite objects keep only values that
   `SharedPayloadKeys`, `LifecycleTelemetryPayloadKeys`, `GoalTelemetryPayloadKeys`,
   `ReviewFindingPayloadKeys`, `ReviewFinishedTelemetryPayloadKeys`, and
   `ReviewVerificationSignalKeys` do not declare. Adapter code, including the `MatKeys`
   alias in `LifecycleTelemetryPayloads.kt`, references the shared owner for the removed
   members.
4. **Schema locators and schema-load logging (F-002).** Move every non-experiment
   `*SchemaPaths` object to a new `skillbill.infrastructure.contracts.schema` package in
   `runtime-infra/contracts`, grouped by contract family into at most 12 files. Do not add
   them to the validator packages, which SKILL-376 subtask 3 collapses under a 12-file
   ceiling. The `*_CONTRACT_VERSION` constants stay.
   `GoalPlanningPreparationSchemaPaths.EXPECTED_SCHEMA_ID` and
   `FeatureTaskRuntimePhaseOutputSchemaPaths.EXPECTED_SCHEMA_ID` become top-level
   `const val` schema IDs in `runtime-contracts` beside their versions; ports, engine, and
   sqlite read those. Move `logSchemaLoadFailure` to `runtime-infra/contracts`. Move the
   two runtime-contracts repoTests (`ProducerOutputEvidenceContractVersionTest`,
   `RejectedOutputDiagnosticContractVersionTest`) to `runtime-infra/contracts/src/repoTest`,
   in a package that exists in runtime-infra/contracts main (SKILL-376 requires every infra
   test package to have a production counterpart).
   Drop `id("skillbill.repo-test")` from `runtime-contracts/build.gradle.kts` once its
   `src/repoTest` is empty.
5. **Marker interface (F-008).** Delete `JsonPayloadContract` and the `override` modifier
   on its 20 implementations; each `toPayload()` stays.
6. **Naming (F-009).** Move `skillbill.contracts.workflow.workflow` contents to
   `skillbill.contracts.workflow` (18 importing files). Merge `JsonSupportTest` into
   `JsonCodecTest`.
7. **Guards that pin moved files.** Update
   `RuntimeArchitectureTest.assertContractsSchemaPathFilesPresent`: the four locator files
   are now expected under runtime-infra/contracts, and the path list under
   runtime-contracts moves to the absent list. Update the telemetry test's
   `TelemetryProxyContracts.kt` path to infra:http. If "cli and mcp learning payloads use
   contract DTO mappers" still exists, delete it rather than repointing it. It is a
   source-text pin that SKILL-373 subtask 2 and SKILL-375 both delete. Keep each remaining
   assertion's intent.
8. **Documentation (F-007).** In `AGENTS.md` "Wire and payload keys", replace "in an owning
   `*Keys` or `*PayloadKeys` object in `runtime-contracts`" with the placement rule, keeping
   the named shared owners. In `runtime-kotlin/ARCHITECTURE.md`, update the Gradle Modules
   `runtime-contracts` bullet, Package Ownership `skillbill.contracts.*` (drop the
   split-package claim), boundary rule 5, "Runtime Contract And Schema Seams", and "Wire
   vocabulary". Append the placement-rule decision to `runtime-kotlin/agent/decisions.md`.
   It supersedes the 2026-05-28 clause keeping `*SchemaPaths` in runtime-contracts for
   locators, and keeps it for versions and record-identity IDs.

## Acceptance Criteria

1. Every item that scope items 1, 2, and 4 name as moving is declared under its target
   module's `src/main` (or the named test source set), and none is declared under
   `runtime-contracts/src/main`.
2. No `object` whose name ends in `SchemaPaths` is declared under `runtime-contracts/src`,
   and the two record-identity schema IDs are top-level `const val` declarations there.
3. The two SQLite objects declare no string value that any shared owner named in scope
   item 3 declares, and the governed-key set that `WireVocabularyGovernedSeamInventory`
   builds for each SQLite seam is unchanged.
4. `JsonPayloadContract` is not declared anywhere, and each of its 20 former
   implementations still declares `toPayload(): Map<String, Any?>`.
5. No package `skillbill.contracts.workflow.workflow` exists, and no test file is named
   `JsonSupportTest.kt`.
6. For every declaration remaining under `runtime-contracts/src/main` outside
   `skillbill.error` and the `*_CONTRACT_VERSION` constants, at least one of these holds:
   two or more other production modules reference it; a `runtime-ports` main signature
   exposes it; or another remaining declaration that meets this rule uses it.
7. `RuntimeArchitectureTest` asserts the new locations of the schema locator files and the
   telemetry proxy DTOs, and contains no "cli and mcp learning payloads use contract DTO
   mappers" test.
8. `AGENTS.md` and `runtime-kotlin/ARCHITECTURE.md` state the placement rule. Neither says
   that every wire key or every `*SchemaPaths` constant lives in `runtime-contracts`, or
   that `skillbill.contracts` spans two modules.
9. `runtime-kotlin/agent/decisions.md` has an entry for the placement rule that names the
   superseded 2026-05-28 clause.

## Non-goals

- Moving error classes.
- Moving `DecompositionPlanningResult` or consolidating map-reader helpers.
- Changing wire values, `toPayload()` output, schema files, or contract versions.
- `@Serializable` DTOs.
- Adding a consumer-count architecture guard.
- Repairing vacuous guards (SKILL-371 subtask 1).

## Dependency notes

- Requires subtask 1, which deletes the loaders, their errors, and the dead key members.
- Run on the current tree. Delete experiment declarations that are still in runtime-contracts. Edit the architecture suite where it lives. Use the infra contract paths that exist now.
- If "cli and mcp learning payloads use contract DTO mappers" is already gone, skip that step. If it still pins a file this subtask deletes, delete the pin here.
- `RuntimeModuleCatalog` edges must not change. If a move needs a new module edge, the item stays, and the subtask report names it and the reason.
- Move MCP-only keys that are in runtime-contracts owners to runtime-mcp.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check` passes, including `WireVocabularyArchitectureTest`,
  `RuntimeArchitectureTest`, `RuntimeGradleModuleLayeringTest`, and every `repoTest`.
- CLI and MCP output tests covering learning, review import, triage, doctor, version,
  update check, and lifecycle telemetry pass unchanged.
- Re-running the investigation's census on the result reports no single-owner item in
  runtime-contracts and zero overlap between the SQLite objects and the shared owners.

## Next path

This is the final subtask. After it commits, the goal runner finishes SKILL-374.
