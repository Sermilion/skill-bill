# SKILL-375 Subtask 1 - One tool declaration, one call path

Parent spec: [.feature-specs/SKILL-375-runtime-mcp-adapter-cleanup/spec.md](spec.md)
Issue key: SKILL-375

## Scope

Resolves F-001 through F-010 from [investigation.md](investigation.md). Rebase onto SKILL-368, SKILL-370, SKILL-371, and SKILL-372 first, and keep their changes.

First step, before any edit: refresh this spec against current main as described in the parent spec's "Refresh before implementing" paragraph. File names, counts, and line references below come from `dbf9f4830`. Where a sibling bundle has already changed or removed something, follow the current code and skip the instruction. Record in the area history what the refresh changed.

Shim removal (F-001). Delete `shared/McpComponentAccess.kt` and every `context: Any` overload in `McpRuntime`, `McpRuntimeLifecycle`, `McpWorkflowRuntime`, `McpStdioServer`, `McpToolDispatcher`, and `McpWorkflowToolHandlers`. Delete the component-less `McpStdioServer.handleLine(line)`, `McpToolDispatcher.call`, and `McpRuntime.version`. Keep `src/test/.../shared/McpRuntimeContext.kt` as a test fixture that returns an `McpComponent`, and delete `McpRuntimeServices` and `services(...)` if nothing needs them after the rewrite. Move each tool-behavior test that called an internal runtime object onto `McpStdioServer.handleLine(line, component)` or the dispatcher's validated entry, keeping its assertions. Leave direct unit tests of the framer, validator, projection, bridge, and connection as they are. Delete "workflow open retains context as its fourth positional argument". Tests that only check validation or unknown-tool errors build the same fixture component.

Tool table (F-002). Make `McpTool` the single declaration: name, description, handler, optional normalizer, runtime-owned argument keys (default empty), and advertised enum subset per property (default empty). Declare the 25 tools in one ordered list in `McpToolRegistry`, in today's order. `quality_check_finished` declares its normalizer and its three runtime-owned keys. `feature_verify_finished` and `quality_check_finished` declare their enum subsets from `featureVerifyCompletionStatuses` and `qualityCheckResults`. `McpInputSchemaProjection` removes runtime-owned keys and applies enum subsets from the declaration rather than from tool-name branches. The dispatcher rejects runtime-owned keys from the declaration and looks tools up in a map built once from the list. Delete `TOOL_HANDLERS`, `orderedToolNames`, `toolDescriptions`, `McpToolSpec`, `handlerFor`, and `RUNTIME_OWNED_QUALITY_CHECK_KEYS`. Advertised schemas stay byte-identical to `mcp-tools-list.json`.

Family handlers (F-003). Replace `McpRuntime`, `McpRuntimeLifecycle`, `McpWorkflowRuntime`, and `McpWorkflowOpenArgs` with one handler file per family: review and learning, lifecycle telemetry, remote telemetry and system (doctor, update check, proxy capabilities, remote stats), workflow, feature-task settlement, and scaffold. Each handler parses its arguments, calls the service, applies `autoSync` where it does today, and maps the result. Move `shared/McpResultMappers.kt` into the review family. Keep `recordCaptureFailure` and the capture path in the dispatcher's failure handling.

Dead surface (F-004). Delete the `openFeatureTask` branch of workflow open with its two `required*` helpers, the `issue_key`/`subtask_id` fallbacks in workflow continue, `WorkflowContinueMcpBranchMapsDecomposition.kt`, the `workflow` branch of `readOnlyFullStateCommand` (the command is always `verify-workflow`), `GoalPlanningStatusSnapshot.toMcpMap`, and the five unreferenced `McpProtocolFramer` schema constants. `WorkflowContinueResult.toMcpMap` maps every `Decomposition*` variant in one arm that throws `UnsupportedOperationException("<variant> is not produced for verify workflows")`. Don't use `IllegalStateException`: the dispatcher returns it to the client without capture, while this arm signals a broken invariant that must reach the capture path. Delete the `TASK_RUNTIME` tests in `McpRuntimeTest` and `McpWorkflowContinuationRuntimeTest` (delete the file if nothing remains) and the `mcp-feature-task-runtime-workflow.json` golden.

Component surface (F-005). `McpComponent` declares an accessor for each type a handler reads, including the scaffold dependency SKILL-371 introduces. Remove `databaseSessionFactory`, and remove `clock` if no handler reads it after SKILL-371. No handwritten code reads `runtimeComponent`.

Argument errors (F-006). The argument accessors in `shared/McpToolArguments.kt` take the tool name, either as a receiver wrapper the dispatcher builds from the declaration or as a parameter, so each `InvalidMcpToolArgumentError` names the tool.

Wire keys (F-007). In the workflow, telemetry, and feature-task output maps, reference the existing owners (`WorkflowWirePayloadKeys`, `LifecycleTelemetryPayloadKeys.ERROR`, but not `LearningPayloadKeys`, which SKILL-374 moves into runtime-application, `TelemetryProxyPayloadKeys`, `SharedPayloadKeys`, `DecompositionManifestPayloadKeys`) for keys CLI and MCP both write. For MCP-only keys, follow the placement rule in force: a `runtime-contracts` owner under today's AGENTS.md, or an MCP-owned `*Keys` object if SKILL-374 has landed. `mapRemoteStatsWorkflow` accepts the values in `remoteStatsWorkflows` (runtime-domain `TelemetryConstants`) and keeps only the `verify` alias mapping. Remove the `cli/workflow/WorkflowContinueMcpBranchMapsDecomposition` marker from `WireVocabularyGovernedSeamInventory`, and drop the MCP decomposition marker together with the deleted file.

Build (F-008). Apply `skillbill.governed-resources` in `runtime-mcp/build.gradle.kts` and declare `copy("copyTelemetryEventSchema", "telemetry-event-schema.yaml", "SKILL-48: canonical telemetry-event schema", "skillbill/mcp/contracts")` with source root `../orchestration/contracts`. Remove the hand-written task, the `java.io.File` import, the `sourceSets` resource directory, and the `processResources`/`processTestResources` wiring. The plugin adds the generated root to the main resources, and tests read it from the main resources on the test runtime classpath. The classpath resource path `skillbill/mcp/contracts/telemetry-event-schema.yaml` stays the same.

Validator (F-009). Rewrite `loadSchemaDocument` and `compileSchema` as one `try` each, mapping `IOException` and `JsonProcessingException` to `InvalidTelemetryEventSchemaError` with today's messages. Remove the `assertIdentity(yamlText: String)` overload, and have `TelemetryEventSchemaCleanupTest` parse its YAML. Resolve `$defs` once per projection. Correct the YAML header comment's references to `TelemetryEventSchemaPaths` and the coherence-rule location, and keep `contract_version` unchanged.

Test layout (F-010) and guards. Put each rewritten test file in the family package of the handler it exercises, and put protocol and stdio suites in `core`. Split `McpRuntimeTest` only along family lines. Delete `RuntimeArchitectureTest` "cli and mcp learning payloads use contract DTO mappers" if it still exists. It is a source-text pin (file existence, import substrings, a retired name) on `McpRuntime.kt`. SKILL-373 subtask 2 deletes it by name and skips it if it's already gone. The architecture suite may have moved to `runtime-core/src/repoTest` (SKILL-373 subtask 3); edit it wherever it lives. If SKILL-374 has moved the four MCP-only DTOs in `McpAdapterContracts.kt` (`Mcp*SkippedContract`, `McpOrchestratedPayloadContract`) into runtime-mcp; `UpdateCheckContract` stays in runtime-contracts, place them in the family package of the handler that writes them. Don't rely on `RuntimeEngineInboundApiTest` as evidence: it scans nothing until SKILL-371 subtask 1 lands.

Docs. Update `runtime-mcp/agent/history.md` and the runtime-mcp paragraphs in `runtime-kotlin/ARCHITECTURE.md`.

## Acceptance Criteria

1. runtime-mcp main source contains no reflection API use, no `componentForLegacyContext`, and no parameter typed `Any` standing for a component or context.
2. `McpStdioServer` has exactly one `handleLine`, taking a line and an `McpComponent`. `McpToolDispatcher` exposes one dispatch entry, and `McpRuntime.version` is gone.
3. `McpToolRegistry` holds one ordered list of 25 `McpTool` declarations, and `tools/list`, projection, validation, and dispatch read it. A grep for each tool name in main source finds it only in its declaration.
4. `McpRuntime.kt`, `McpRuntimeLifecycle.kt`, `McpWorkflowRuntime.kt`, `McpWorkflowOpenArgs`, `McpToolSpec`, `TOOL_HANDLERS`, and `RUNTIME_OWNED_QUALITY_CHECK_KEYS` are deleted, and each family handler file calls services directly.
5. No runtime-mcp source references `WorkflowFamilyKind.TASK_RUNTIME` or `openFeatureTask`. `WorkflowContinueMcpBranchMapsDecomposition.kt` and `mcp-feature-task-runtime-workflow.json` are deleted, and the `Decomposition*` variants map to one arm that throws `UnsupportedOperationException`, which the dispatcher captures through its exception-capture path instead of returning it as a client error.
6. `GoalPlanningStatusSnapshot.toMcpMap` and the five unused `McpProtocolFramer` constants are deleted.
7. No handwritten runtime-mcp code reads `McpComponent.runtimeComponent`, and `McpComponent` declares only members that main source reads.
8. A tool call with a mistyped argument on an open-object tool returns an error that names that tool, not `<unknown>`.
9. The workflow, telemetry, and feature-task mappers contain no literal output key, `mapRemoteStatsWorkflow` restates no `remoteStatsWorkflows` token, and `WireVocabularyArchitectureTest` passes with no marker for a missing path.
10. `runtime-mcp/build.gradle.kts` declares the schema through the governed-resources `copy` DSL, and the built jar still contains `skillbill/mcp/contracts/telemetry-event-schema.yaml`.
11. `TelemetryEventSchemaValidator` has one `assertIdentity(JsonNode)` and no repeated catch blocks, and its error messages match today's.
12. Tests of tool behavior reach tools only through `McpStdioServer.handleLine` or the dispatcher entry with a fixture-built `McpComponent`, and no test calls a handler or forwarding function directly. Direct unit tests of the framer, schema validator, schema projection, governed-evidence bridge, and connection stay allowed. Every runtime-mcp test file declares a package that exists in main.
13. `RuntimeArchitectureTest` "cli and mcp learning payloads use contract DTO mappers" no longer exists.
14. Every remaining golden fixture and stdio test passes unchanged, and `tools/list` equals `mcp-tools-list.json`.
15. `runtime-mcp/agent/history.md` and `runtime-kotlin/ARCHITECTURE.md` describe the single tool list, the family handlers, and the removed feature-task surface.

## Non-Goals

- Scaffold parsing, session id, and `repo_root` (SKILL-371), goal-observability mapping (SKILL-370, SKILL-372), and `RuntimeComponent` accessor pruning (SKILL-373).
- Removing decomposition continuation from runtime-application or runtime-cli.
- Changing which exception types the dispatcher returns as client errors.
- Any change to tool names, arguments, result payloads, schema content, or `contract_version`.
- A tool-registration DSL, annotation processor, MCP SDK, or new interface.

## Dependency Notes

Depends on: none within this bundle.
External sequencing: land after SKILL-368 (governed-resources `copy` DSL), SKILL-370, SKILL-371, and SKILL-372, and rebase onto them before starting.

## Validation Strategy

Run `:runtime-mcp:test`, `:runtime-mcp:repoTest`, and the runtime-core architecture tests that read runtime-mcp: `WireVocabularyArchitectureTest`, the `RuntimeLayerBoundaryArchitectureTest` MCP checks, `RuntimeArchitectureTest`, and the ambient-environment, ambient-clock, inject-defaults, and package-cycle baselines. The goldens, `McpStdioServerTest`, `McpStdioServerDispatchTest`, and `McpStdioArgumentShapeUnifiedContractTest` are the behavior baseline. Add at most one test: a mistyped argument on an open-object tool returns an error naming the tool. It catches a regression where the tool name drops back to `<unknown>`. Build the distribution and exercise it once over stdio with `initialize`, `ping`, `tools/list`, an unknown-argument call, and a well-formed `feature_verify_workflow_open`. The validate phase runs the routed pack gate.

## Next Path

This is the only subtask. After it commits, the goal finishes. Continue with `skill-bill goal SKILL-375`.

## Spec Path

.feature-specs/SKILL-375-runtime-mcp-adapter-cleanup/spec_subtask_1_one-tool-declaration-one-call-path.md
