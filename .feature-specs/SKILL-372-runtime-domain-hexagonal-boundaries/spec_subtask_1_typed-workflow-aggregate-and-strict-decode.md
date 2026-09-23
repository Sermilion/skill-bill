# SKILL-372 Subtask 1 - Typed workflow aggregate and strict decode

Parent spec: [.feature-specs/SKILL-372-runtime-domain-hexagonal-boundaries/spec.md](spec.md)
Issue key: SKILL-372

## Scope

Resolve F-001, F-002, F-003, and F-009 in [investigation.md](investigation.md).

**Aggregate.**

- `WorkflowStateSnapshot` (`runtime-domain/…/workflow/engine/model/WorkflowModels.kt`) carries `steps: List<WorkflowStepState>`, `artifacts: DurableWorkflowArtifacts`, `mode: FeatureTaskWorkflowMode?`, and `startedAt`/`updatedAt`/`finishedAt: Instant?`.
- Move the strict decoders (`decodeSteps`, `decodeObject`, `parseDurableJson` from `WorkflowEngineSnapshotCodecDurable.kt`) and step encoding out of runtime-domain into `runtime-ports/…/ports/workflow/model/WorkflowRecordMapping.kt`, beside `toSnapshot()`. `toSnapshot()` decodes both columns strictly and `toRecord()` encodes them. These are the only places column text becomes the aggregate.
- Delete the lenient public `decodeWorkflowSteps` (`AttemptLedgerWorkflowDecoding.kt:25`) and migrate its SQLite callers to `snapshot.steps`.

**Engine.**

- `WorkflowEngine` operates on values.
- `openRecord`/`updateRecord` always run `validateWorkflowOpen`/`validateWorkflowUpdate` and raise `InvalidWorkflowStateSchemaError`. `validateUpdate` becomes non-public, and `WorkflowService.kt:151` keeps any operator-facing message by catching the typed error.
- Replace the `finishedAt = ""` sentinel with an explicit `Instant` that the caller supplies.
- Move snapshot schema validation to the write seam (`toRecord` or the workflow-state repository save), and move `WorkflowSnapshotValidator` to `runtime-ports`.
- Delete the never-passed `checkpoint` parameter. `WorkflowEngine` then has no constructor parameters.
- Update the 10 production and about 30 test construction sites, and DI in `runtime-core`.

**Lenient decoders.** Delete `DurableWorkflowArtifacts.fromJson`, SQLite `decodeArtifacts`, application `decodeWorkflowArtifacts`, and engine `artifactsFrom`/`artifactsFromJson`. Migrate their 108 call sites to `snapshot.artifacts` or `record.toSnapshot()`. Keep `sparseArtifactKeys` only if progress polling needs it for cost, and make it raise the typed error on malformed text.

**Time.** Type as `Instant` every domain timestamp that domain or a consumer compares, orders, or does arithmetic on: the goal-runner execution lease, the attempt ledger, phase records used for durations, and goal progress event time. Codecs parse and format the formats stored today, including the SQLite `yyyy-MM-dd HH:mm:ss` legacy form. Delete consumer re-parsing (`GoalPlanningLogService.kt:138`, `GoalRunnerTelemetryEmitter.kt:170`, `FeatureTaskRuntimeWorkflowPersistence.kt:233`, `SQLiteWorkListRepository.kt:145`, and siblings found at implementation time). A carried-only display timestamp may stay `String`, with a one-line reason in its model file.

**Tests.** Update the 43 test files that build or inspect row text so they build aggregates. Keep row-text assertions only in the mapping and SQLite repository tests.

## Acceptance Criteria

1. `WorkflowStateSnapshot` has `steps`, `artifacts`, a typed `mode`, and `Instant?` timestamps, with no `stepsJson`, `artifactsJson`, or `""` sentinel. No runtime-domain production file parses or serialises workflow steps or artifacts JSON.
2. `toSnapshot()` raises `InvalidWorkflowStateSchemaError` for malformed JSON, a non-array steps root, a non-object artifacts root, and a non-object step entry. `toRecord()` produces byte-identical columns and timestamp text to the baseline for every supported fixture.
3. The four lenient artifact decoders, their wrappers, and `decodeWorkflowSteps` do not exist, and no production code substitutes an empty map for unparseable workflow JSON.
4. A workflow row with corrupt `artifacts_json` fails typed or reaches the existing quarantine path on resume, status, and goal-runner progress reads, where before it read as empty.
5. An engine or SQLite update with an undeclared step id or a status the definition does not allow raises `InvalidWorkflowStateSchemaError` from `updateRecord`. The update validation entry point is not public.
6. `WorkflowSnapshotValidator` is declared in `runtime-ports`, not runtime-domain. `WorkflowEngine` has no constructor parameters, and every existing workflow-state schema rejection test still rejects.
7. The timestamps named in scope are `Instant`, the listed consumer re-parse sites are gone, and each stored timestamp format round-trips byte-identically.
8. Snapshot wire JSON, step JSON, and acknowledgement payloads match the SKILL-351 baselines under `.feature-specs/done/SKILL-351-runtime-domain-boundaries-and-simplicity/baselines/`.

## Non-goals

No artifact accessors, key visibility changes, rule moves, or removal of the other validator parameters; those are subtask 2. No package moves; that is subtask 3. No new transition rules or named transition methods. No schema or column change.

## Dependency notes

Depends on: none within this bundle. Recheck anchors on the current tree. The commit carries every consumer and test change the new aggregate needs, so it ships alone. It does not wait for another issue.

## Validation strategy

Name the regression before each test: a corrupt artifacts column that resume read as empty must now fail typed; an invalid update from a SQLite writer must now fail; a valid row must round-trip byte-for-byte.

- Capture row and wire fixtures across workflow families and every stored timestamp format first.
- Run the runtime-domain, runtime-ports, runtime-application, runtime-engine, infra-sqlite, infra-contracts, runtime-mcp, runtime-cli, and runtime-core architecture suites, then the governed quality gate.
- Apply `bill-unit-test-value-check` to changed tests.

## Next path

Continue to `spec_subtask_2_domain-owned-artifacts-and-shared-rules.md` after this subtask settles.

## Spec Path

.feature-specs/SKILL-372-runtime-domain-hexagonal-boundaries/spec_subtask_1_typed-workflow-aggregate-and-strict-decode.md
