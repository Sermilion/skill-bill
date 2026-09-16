## [2026-09-16] Unified durable artifact map reader and lenient workflow-step integers (SKILL-351 subtask 1)

Context: Durable artifact decoding duplicated nine map-field accessor families and fourteen integer coercions with divergent semantics. Workflow snapshot step decoding intentionally keeps a lenient integer coercion for legacy rows.

Decision: (c) Retain `AttemptLedgerWorkflowDecoding.asLenientIntOrNull` as the sole lenient integer coercion (Int, Number→toInt, String→toIntOrNull). All other durable artifact seams use `DurableArtifactMapReader` with `BigDecimal.longValueExact` / exact integral narrowing via `asExactIntOrNull` and `asExactLongOrNull` in `FeatureTaskRuntimePersistenceMapFields.kt`. Review-state and goal-observability field helpers delegate to the exact coercion helpers rather than maintaining parallel parsers.

Evidence: `FeatureTaskRuntimePersistenceMapFieldsTest`, `ReviewRunLaneSegmentAccountingJsonTest`, `TypedParseBoundaryArchitectureTest`.

Revisit when: SKILL-352 replaces any remaining engine-local readers or workflow status moves to closed enums at the engine boundary.
