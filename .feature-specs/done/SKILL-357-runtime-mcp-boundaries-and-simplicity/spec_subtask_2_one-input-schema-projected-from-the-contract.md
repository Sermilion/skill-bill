# SKILL-357 Subtask 2 - One input schema projected from the contract

Parent spec: [.feature-specs/SKILL-357-runtime-mcp-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-357

## Scope

Resolve F-002 in [investigation.md](investigation.md).

Own `runtime-kotlin/runtime-mcp/src/main/kotlin/skillbill/mcp/core/McpInputSchemas.kt`, `McpInputSchemaPrimitives.kt`, the input-schema fields of the tool declarations subtask 1 created, `McpStdioServer.validateStrictArguments` and the `unknownProperties` walker, `TelemetryEventSchemaValidator` for exposing the loaded schema document, `../../../orchestration/contracts/telemetry-event-schema.yaml`, `TELEMETRY_EVENT_CONTRACT_VERSION`, `src/repoTest/kotlin/skillbill/mcp/TelemetryEventInputSchemaParityTest.kt`, `TelemetryEventSchemaContractVersionTest`, `src/test/kotlin/skillbill/mcp/FailureDispositionSchemaAdvertisementTest.kt`, `McpStdioServerTest`, `McpStdioServerTestSupport`, `McpStdioArgumentShapeUnifiedContractTest`, a new golden `tools/list` fixture, `runtime-kotlin/ARCHITECTURE.md` (the telemetry-event schema ownership bullet), and `runtime-kotlin/agent/decisions.md`.

Make the YAML branch the one schema. Each tool's advertised `inputSchema` is projected at startup from its `$defs` branch: the branch object without `event_name` and `contract_version` in `properties` and `required`, with local `$ref`s inlined from `$defs`. Move every enum the Kotlin schemas carried into the YAML branches: the `feature_verify_workflow_update` `workflow_status` and `current_step_id` and step-update `step_id` enums, the `feature_task_phase_*` `phase_id` enum, the `feature_task_phase_block` `failure_disposition` enum, the `feature_verify_finished` `audit_result`, `completion_status`, `history_relevance`, and `history_helpfulness` enums, the `quality_check_*` `scope_type` and `result` enums where the branch does not already `$ref` them, and the `telemetry_remote_stats` and `goal_stats` `workflow` and `group_by` enums. Add one repo-contract test that pins each YAML enum to its owner: `WorkflowStatus` entries allowed by `FeatureVerifyWorkflowDefinition`, `FeatureVerifyWorkflowDefinition.stepIds`, `FeatureTaskRuntimePhaseWorkflowDefinition` phase constants, `FeatureTaskRuntimeFailureDisposition.entries.map(wireValue)`, `WorkflowStepStatus.entries.map(wireValue)`, and the `runtime-application` telemetry validator lists. Delete the Kotlin schema declarations, the unknown-argument walker (networknt's `additionalProperties: false` rejection is the one unknown-argument path), the parity test, and the advertisement test. Follow the schema file's versioning header for the constraint additions and keep `TelemetryEventSchemaContractVersionTest` green.

## Acceptance Criteria

1. `McpInputSchemas.kt` and `McpInputSchemaPrimitives.kt` are deleted and no tool declaration in `src/main` contains a hand-written `inputSchema` map; each tool's `inputSchema` is projected from its `$defs` branch by one `core` function that removes `event_name` and `contract_version`, inlines local `$ref`s, and leaves every other constraint intact; a test proves a branch with a `$ref` to `freeObjectShape` projects to an inlined object schema.
2. A golden fixture `src/test/resources/golden/mcp-tools-list.json` pins the full `tools/list` result; for every tool, its `properties` keys, `required` list, `additionalProperties` flag, and every `enum` that existed in the Kotlin schemas are present in the golden with the same values, and `McpStdioServerTestSupport.assertStrictSchemaCoveragePublished` passes with unchanged assertions.
3. Every enum listed in Scope is a constraint in its YAML branch, and one repo-contract test pins each to its Kotlin owner by `wireValue` or definition list; `grep -rnE '"(preplan|implement|retryable|needs_user_action|abandoned_at_review|had_gaps|working_tree|unsupported_stack|irrelevant|extract_criteria|completeness_audit)"' runtime-mcp/src/main` returns nothing.
4. `McpStdioServer.validateStrictArguments` and `unknownProperties` are deleted; a `tools/call` with an unknown argument on a strict tool returns `isError: true` with the networknt path in the message from the one validation seam, and `McpToolDispatcher.call` and `McpStdioServer.handleLine` reject it identically; `McpStdioArgumentShapeUnifiedContractTest` and `McpStdioServerTest.strict tools reject unknown nested arguments at the stdio boundary` pass with assertions updated only for the message text.
5. `TelemetryEventInputSchemaParityTest.kt` and `FailureDispositionSchemaAdvertisementTest.kt` are deleted; the `runtimeInternalEmissionEvents` allow-list moves to the new enum-parity test, which still proves every `$defs` branch not in that list is a registered tool and every registered tool has a branch.
6. `TELEMETRY_EVENT_CONTRACT_VERSION`, the YAML top-level `contract_version`, and every branch `contract_version.const` agree per the schema header's versioning rule; `TelemetryEventSchemaContractVersionTest`, `TelemetryEventSchemaCleanupTest`, `TelemetryEventSchemaValidatesAllEventsTest`, and `TelemetryEventSchemaViolationsTest` pass; `ARCHITECTURE.md` L867-869 states that the YAML branch is both the validating and the advertised schema; `decisions.md` records the projection and the deletion of the Kotlin copy.
7. `./gradlew :runtime-mcp:test :runtime-mcp:repoTest` passes with zero failures; tool result payloads for every golden fixture are byte-identical to subtask 1's tree.

## Non-goals

No change to handlers, composition, framing, error types, or keys; subtask 1 owns those. No change to the validator's coherence rules or its module placement. No runtime-internal emission branch (`goal_*`, `skillbill_review_*`) changes.

## Dependency notes

Depends on: subtask 1. The projection plugs into the `McpTool` declaration it introduces, and the typed-error and one-seam validation shape it leaves behind. Rebase on the branch head and re-census the tool list before editing.

## Validation strategy

Name the regression before each test: an advertised enum a client obeys that validation does not enforce, a YAML enum that drifts from its Kotlin owner, a `$ref` leaked into `tools/list`, an unknown argument accepted through the dispatcher but rejected at the server, a schema bump that leaves a branch const behind. Assert `tools/list` against the golden; run the module suites, `runtime-core` guards, and `./gradlew check` on `runtime-kotlin`; run the pack-declared quality gate and `bill-unit-test-value-check` for changed tests.

## Next path

Goal complete after this subtask settles; the runtime finalises history and decisions entries.

## Spec Path

.feature-specs/SKILL-357-runtime-mcp-boundaries-and-simplicity/spec_subtask_2_one-input-schema-projected-from-the-contract.md
