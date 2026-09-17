# SKILL-351 Subtask 1 - Unify durable decoding and failure reporting

Parent spec: [.feature-specs/SKILL-351-runtime-domain-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-351

## Scope

Resolve F-001 through F-004 in [investigation.md](investigation.md).

Own every durable decode seam in runtime-domain: the `fromArtifactMap` companions under `workflow/taskruntime/model`, `workflow/goal/model`, and `goalrunner`, the enum `fromWire` companions reached from SQLite rows in `review/model/ReviewStageState.kt` and `workflow/goal/model/GoalObservabilityModels.kt`, `ReviewFindingCitation.decodeList`, `DecompositionManifestWireCodec`, the workflow snapshot codec, and their SQLite and application callers where the failure identity changes.

Introduce one internal string-keyed artifact reader whose only variation is the family's typed-error factory, plus one exact integer coercion and the one documented lenient coercion `AttemptLedgerWorkflowDecoding` needs. Migrate the nine accessor families and fourteen coercions onto it and delete them. Where a decoded value's `init` invariant can fail on durable input, translate that failure at the decode seam into the family's typed error. Replace `ReviewRunLaneSegmentAccountingJson` with `JsonCodec`. Repair the four silent fallbacks in F-004 and delete the unreferenced liveness reader.

Give the nine wrappers in `WorkflowBoundaryCollections.kt` value semantics if they survive this subtask; their relocation or removal is subtask 2.

## Acceptance Criteria

1. Unknown `stage`, `claim_verdict`, `scope_disposition`, `reached`, and severity-direction tokens read from SQLite raise the review family's typed error, not `IllegalStateException`. A non-numeric citation line raises the same family's error, not `NumberFormatException`.
2. `FeatureTaskRuntimeResolvedBranch`, `FeatureTaskRuntimePhaseLedgerEntry`, `FeatureTaskRuntimeGoalContinuationArtifact`, and the goal observability and ledger enums raise `InvalidWorkflowStateSchemaError` or their family's typed error for every malformed field, including list-element types, `attempt_count: 0`, and unknown enum tokens. The three `catch (IllegalArgumentException)` rewraps in the goal-continuation decoder and the dual catches in `GoalSubtaskReviewFindingArtifacts` are unnecessary and removed.
3. One internal reader supplies required and optional string, integer, long, boolean, list, and object access with exact numeric coercion; one lenient coercion remains and is documented. The accessor families named in F-002 no longer exist. Supported values decode identically before and after, proven by round-trip fixtures per artifact family.
4. Lane segment accounting uses `JsonCodec`. `null`, `[]`, and existing encoded rows decode unchanged; a `segment_id` containing `\n`, `\t`, `"`, or a `\u` escape round-trips; malformed text raises a typed failure.
5. The resume gate-selection fallback either fails typed or records seam, value used, and expected value; the version fallback is recorded or fails; `parsePayloadMap` and `parseInstantOrNull` narrow their catch and record substitution. `goalObservabilityLatestEventForLiveness` is deleted.
6. Quarantine and regeneration seams that catch the typed error now receive every malformed case from these decoders. Tests that pinned `IllegalArgumentException` or `IllegalStateException` at durable seams assert the typed error instead. Operator-input parsers keep their argument-error behaviour and tests.
7. `TypedParseBoundaryArchitectureTest`'s inventory names the decoders changed here so regression is mechanical.

## Non-goals

No new serialization library, DTO generation, schema engine, or result framework. No change to CLI and MCP argument validation. No relocation of wrappers, ports, or DTO mapping; that is subtask 2. No deletion beyond the one dead liveness reader; that is subtask 3.

## Dependency notes

Depends on: none. This commit includes every consumer change required by the new failure identities so it ships alone. Subtasks 2 and 3 rebase on it if it lands first.

## Validation strategy

Name the regression before each test: a durable token that today crashes a read must now reach quarantine; a valid integer that today decodes must still decode. Build malformed fixtures per artifact family and assert error type and payload-free reason. Round-trip every artifact family and the workflow snapshot and diff the encoded bytes. Exercise SQLite row mappers with corrupt columns. Run runtime-domain, runtime-infra-sqlite, runtime-application, and runtime-engine tests, the typed-parse-boundary and failure-code guards, then the governed quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Continue to `spec_subtask_2_restore-ownership-and-typed-boundaries.md` through the goal runtime after this subtask settles.

## Spec Path

.feature-specs/SKILL-351-runtime-domain-boundaries-and-simplicity/spec_subtask_1_unify-durable-decoding-and-failure-reporting.md
