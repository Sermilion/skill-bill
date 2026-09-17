## [2026-09-17] Remaining multi-file families after merge-count shrink (SKILL-351 subtask 3)

Context: Subtask 3 merges count-driven split files where ceilings allow and documents responsibility-based splits that remain.

Decision: `FeatureTaskRuntimeProjectionCanonicalizer` is one implementation file plus `FeatureTaskRuntimeProjectionCanonicalizationTypes.kt` for shared types and key sets. `GoalObservabilityParsing` is a single file. `FeatureTaskRuntimeHandoffProjection*` stays split by lifecycle: core projection model (`model/FeatureTaskRuntimeHandoffProjection*.kt`), envelope wire (`FeatureTaskRuntimeHandoffProjectionEnvelopeWire.kt`), validator and field resolution (`FeatureTaskRuntimeHandoffProjectionValidator.kt`, `FeatureTaskRuntimeHandoffProjectionFieldResolver.kt`, `FeatureTaskRuntimeHandoffProjectionValueBuilder.kt`, `FeatureTaskRuntimeHandoffProjectionFinalization.kt`, `FeatureTaskRuntimeHandoffProjectionDeclarationChecks.kt`, `FeatureTaskRuntimeHandoffProjectionSourceFields.kt`) because validator plus builder paths exceed a single file without spillover suffixes. `WorkflowEngine*` splits snapshot codec (`WorkflowEngineSnapshotCodec.kt`, `WorkflowEngineSnapshotCodecDurable.kt`), continuation assembly (`WorkflowEngineContinuationAssembly.kt`, `WorkflowEngineContinuationCompact.kt`, `WorkflowEngineContinuationPrompts.kt`), validation (`WorkflowEngineValidation.kt`), and numeric coercion (`WorkflowEngineNumericCoercion.kt`) around distinct persistence and continuation responsibilities. `FeatureTaskRuntimePhaseWorkflow*` keeps `FeatureTaskRuntimePhaseWorkflowDefinition.kt` as the graph owner with `FeatureTaskRuntimePhaseWorkflowGraph.kt`, `FeatureTaskRuntimePhaseWorkflowTransitions.kt`, `FeatureTaskRuntimePhaseWorkflowQueries.kt`, and `FeatureTaskRuntimePhaseWorkflowProjectionDeclarations.kt` as named graph, transition, query, and projection-declaration units.

Evidence: `ProductionFileLineCeilingArchitectureTest`, merged canonicalizer and goal observability units in this subtask.

Revisit when: Any family grows past ceilings without a clearer responsibility boundary.

## [2026-09-16] Validator port home, wire artifact collapse, version ownership, wrapper policy (SKILL-351 subtask 2)

Context: Subtask 2 restores ownership and typed boundaries across validator ports, learnings session wiring, workflow continuation typing, and wire-key governance.

Decision: (a) Feature-task runtime JSON-schema validator ports live in `runtime-domain` under `skillbill.workflow.taskruntime` and `skillbill.workflow.decomposition`; concrete Draft 2020-12 validators stay in `runtime-infra-fs`. (b) The identical-shape task-runtime and goal wire validators use `FeatureTaskRuntimeWireArtifactValidator` keyed by `FeatureTaskRuntimeWireArtifactKind`; infra dispatches through `FeatureTaskRuntimeWireArtifactValidatorAdapter`. (c) `SkillBillVersion` and `skillbill/version.properties` live in `runtime-core`; the packaged resource read remains the single ambient seam documented in `ARCHITECTURE.md`. (d) Typed boundary wrappers move out of the monolithic `WorkflowBoundaryCollections.kt` into owning area `model` packages (`workflow/decomposition`, `telemetry`, `review/context`, `workflow/engine`); delete the aggregate file rather than retaining a re-export hub. (e) `WorkflowDefinition.usesFeatureTaskRuntimeContinuation` replaces engine imports of `workflow.taskruntime` for continuation branching. (f) Governed goal-continuation artifact keys declare once in `FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys`; `WireVocabularyGovernedSeamInventory` scans the encode/decode pair.

Evidence: `../../../.feature-specs/done/SKILL-351-runtime-domain-boundaries-and-simplicity`, `FeatureTaskRuntimeWireArtifactValidatorAdapter`, `LearningSessionWire.kt`, `WorkflowEngineBoundaryMaps.kt`, `WireVocabularyArchitectureTest`.

Revisit when: Subtask 3 shrinks remaining surface and merge-count split units land.

## [2026-09-16] Decomposition manifest validator port (SKILL-52.3)

Context: Decomposition manifest schema validation previously lived only in infra; application reached it through ad hoc imports.

Decision: `DecompositionManifestValidator` in `runtime-domain` is the domain-owned port; `DecompositionManifestValidatorAdapter` in `runtime-infra-fs` runs JSON Schema plus coherence checks and throws `InvalidDecompositionManifestSchemaError` on violation.

Evidence: `DecompositionManifestValidatorAdapter`, decomposition manifest rejection tests in `runtime-infra-fs`.

Revisit when: Manifest schema or repair orchestration changes ownership again.

## [2026-09-16] Unified durable artifact map reader and lenient workflow-step integers (SKILL-351 subtask 1)

Context: Durable artifact decoding duplicated nine map-field accessor families and fourteen integer coercions with divergent semantics. Workflow snapshot step decoding intentionally keeps a lenient integer coercion for legacy rows.

Decision: (c) Retain `AttemptLedgerWorkflowDecoding.asLenientIntOrNull` as the sole lenient integer coercion (Int, Number→toInt, String→toIntOrNull). All other durable artifact seams use `DurableArtifactMapReader` with `BigDecimal.longValueExact` / exact integral narrowing via `asExactIntOrNull` and `asExactLongOrNull` in `FeatureTaskRuntimePersistenceMapFields.kt`. Review-state and goal-observability field helpers delegate to the exact coercion helpers rather than maintaining parallel parsers.

Evidence: `FeatureTaskRuntimePersistenceMapFieldsTest`, `ReviewRunLaneSegmentAccountingJsonTest`, `TypedParseBoundaryArchitectureTest`.

Revisit when: SKILL-352 replaces any remaining engine-local readers or workflow status moves to closed enums at the engine boundary.
