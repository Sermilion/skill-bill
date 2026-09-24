# SKILL-375 - runtime-mcp-adapter-cleanup

## Mode

single_spec

## Intended Outcome

runtime-mcp keeps its module edges, framer, and YAML tool-schema source. It loses the production reflection shim that exists for tests, the three parallel tool tables, the forwarding objects, and the code left over from the removed feature-task MCP surface. Each tool is declared once, and each call takes one path from `handleLine` through its handler to its service. Every tool's wire output stays byte-identical, and no new layer is added.

## Overview

[investigation.md](investigation.md) holds the census, the evidence, and the list of what stays. Summary:

| Finding | Current state |
| --- | --- |
| F-001 | `componentForLegacyContext(Any)` uses reflection to reach a test class. 27 `context: Any` overloads route through it. Duplicate `handleLine` and `call` bodies exist only for tests. |
| F-002 | Each tool is spelled in `orderedToolNames`, `toolDescriptions`, and `TOOL_HANDLERS`. `McpTool.handler` is never read. Quality-check rules sit in four places. |
| F-003 | `McpRuntime`, `McpRuntimeLifecycle`, and `McpWorkflowRuntime` forward between handlers and services. |
| F-004 | Feature-task open and continue branches, seven decomposition mappers, 14 `TASK_RUNTIME` test sites, and one golden fixture are unreachable through any tool. |
| F-005 | `McpComponent` exposes its parent graph (scaffold reads `runtimeComponent.scaffoldGateway`) and a test-only `databaseSessionFactory`. |
| F-006 | Argument errors name tool `<unknown>`. |
| F-007 | Output keys such as `db_path` and `error` are literals. A stale wire-inventory marker points at a missing file. |
| F-008 | A hand-rolled `copyTelemetryEventSchema` task duplicates the `skillbill.governed-resources` convention. |
| F-009 | Validator load and compile paths repeat catch blocks. A test-only overload and a stale YAML header comment remain. |
| F-010 | 27 test files sit in the root `skillbill.mcp` package, which main doesn't have. |

Guard validity: the wire-vocabulary, layer-boundary, and four baseline guards read runtime-mcp. `RuntimeEngineInboundApiTest` resolves its roots against the repository root and scans nothing; SKILL-371 subtask 1 restores it. `RuntimeArchitectureTest` "cli and mcp learning payloads use contract DTO mappers" is a source-text pin on `McpRuntime.kt`, which this spec deletes. It is deleted rather than retargeted, as agreed with SKILL-373, whose subtask 2 deletes it by name if SKILL-375 hasn't. The investigation has the per-guard table and a coordination table for SKILL-368 and SKILL-370 to SKILL-377.

Why one subtask: every finding touches the same handler, registry, and test files. Deleting the shim forces tests onto the real seam, and that seam is the one the tool table and family handlers define. Splitting would leave a commit that either keeps the shim alive or rewrites the same tests twice. The change preserves behavior throughout, so one reviewer reads it against the goldens.

This bundle runs on the current tree. It does not wait for a subtask of another issue. Recheck the census against the tree at start. If a finding is already gone, drop that finding. Do the remaining work here, including the scaffold handler and the engine-inbound guard if they still fail. Edit the architecture suite where it lives. If "cli and mcp learning payloads use contract DTO mappers" still exists, delete it here.

Baseline: commit `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b`, 30 production files and 2,994 lines. Local spec mode was resolved through the runtime.

Next command: `skill-bill goal SKILL-375`.

## Acceptance Criteria

1. runtime-mcp main source contains no reflection (`declaredMethods`, `isAccessible`, `Method.invoke`), no parameter typed `Any` that stands for a component or context, and no `componentForLegacyContext`.
2. `McpStdioServer` has one `handleLine(line, component)` implementation. `McpToolDispatcher` has one dispatch entry that validates and then invokes. `McpRuntime.version` no longer exists.
3. Each MCP tool is declared once, in one ordered list, as a value that carries its name, description, handler, and any tool-specific normalizer, runtime-owned argument keys, and advertised enum subset. `tools/list`, schema projection, validation, and dispatch all read that list, and no tool name is compared or spelled as a literal outside its declaration.
4. `McpRuntime`, `McpRuntimeLifecycle`, `McpWorkflowRuntime`, `McpWorkflowOpenArgs`, `McpToolSpec`, `TOOL_HANDLERS`, and `RUNTIME_OWNED_QUALITY_CHECK_KEYS` no longer exist. Each handler parses its arguments, calls one application or engine service, and maps the result.
5. runtime-mcp main and test source contain no `WorkflowFamilyKind.TASK_RUNTIME`, no `openFeatureTask` call, no `issue_key`/`subtask_id` handling for workflow continue, and no `WorkflowContinueMcpBranchMapsDecomposition.kt`. `mcp-feature-task-runtime-workflow.json` is deleted, and the `Decomposition*` continue variants share one arm that throws an exception the dispatcher captures (`UnsupportedOperationException` naming the variant), not a client-error type.
6. `GoalPlanningStatusSnapshot.toMcpMap` and the five unreferenced `McpProtocolFramer` schema constants are deleted.
7. No handwritten code reads `McpComponent.runtimeComponent`. `McpComponent` declares no member that main source doesn't read.
8. Every `InvalidMcpToolArgumentError` raised while a handler reads its arguments names that handler's tool.
9. MCP workflow, telemetry, and feature-task output maps write keys through owning `*Keys` constants, placed according to the key-placement rule in force when this lands (see investigation F-007 and SKILL-374). `mapRemoteStatsWorkflow` restates no token that `remoteStatsWorkflows` owns, and `WireVocabularyGovernedSeamInventory` contains no marker for a path that doesn't exist.
10. `runtime-mcp/build.gradle.kts` declares the telemetry-event schema through the `skillbill.governed-resources` `copy` DSL. It has no `Copy` task, `java.io.File` import, or manual `sourceSets` resource directory.
11. `TelemetryEventSchemaValidator` has one string-free `assertIdentity`, and each load or compile path has a single catch per exception type. The YAML header names the real locations of `TelemetryEventSchemaPaths` and the coherence rules.
12. Tests of tool behavior drive tools through `McpStdioServer.handleLine` or the dispatcher's entry point with a fixture-built `McpComponent`, and no test calls a handler or service-forwarding function directly. Unit tests of transport and contract components (the framer, schema validator, schema projection, governed-evidence bridge, and connection) may keep testing them directly. The "workflow open retains context as its fourth positional argument" test is deleted. Every test file sits in a package that exists in main.
13. The runtime-core source-text pin `RuntimeArchitectureTest` "cli and mcp learning payloads use contract DTO mappers" no longer exists (SKILL-375 deletes it if SKILL-373 subtask 2 hasn't already).
14. Every golden fixture except the deleted feature-task golden passes unchanged, and `tools/list` matches `mcp-tools-list.json` byte for byte.
15. `runtime-mcp/agent/history.md`, and `../../../runtime-kotlin/ARCHITECTURE.md` where it describes runtime-mcp, describe the landed shape.

## Constraints

- Follow `../../../runtime-kotlin/ARCHITECTURE.md` Design Principles and `docs/code-principles.md`: no `//` comments in Kotlin, KDoc only on interfaces, wire keys through `*Keys` owners, and the package sibling limits.
- Keep the module graph, the hand-written framer, the sequential stdio loop, the validator in this module, and the YAML file name and content. The only YAML change is the header comment.
- Tool result payloads and `tools/list` stay byte-identical for well-formed input, as the existing goldens and stdio tests prove.
- Add no module, interface, SDK, DSL, annotation processor, or architecture-test class. Don't expand any baseline.
- Name the regression a test catches before adding it. Prefer moving existing assertions onto the tool boundary over writing new tests.

## Non-Goals

- The scaffold parser, scaffold session id, and `repo_root` semantics (SKILL-371 subtask 3), and update-check field parity (SKILL-371 subtask 2).
- Goal-observability decoding and artifact reads in `WorkflowGoalObservabilityMcpMapping` (SKILL-370, SKILL-372).
- Removing the unreachable decomposition continuation from runtime-application and runtime-cli. The investigation records it for a follow-up after SKILL-370.
- Changing the dispatcher's client-error classification, or adding typed errors to application services.
- Renaming or splitting `telemetry-event-schema.yaml`, or moving `TelemetryEventSchemaValidator`.
- Moving runtime-mcp tests off SQLite internals (SKILL-356).

## SKILL-380 coordination

No part of this bundle is a SKILL-380 prerequisite, and this bundle may land before or after SKILL-380. SKILL-380 changes no MCP tool, including the settlement tools `feature_task_phase_complete` and `feature_task_phase_block`. The two bundles overlap only in imports.

## Validation Strategy

Implementation runs `:runtime-mcp:test` and `:runtime-mcp:repoTest`, plus the runtime-core architecture tests for wire vocabulary, layer boundaries, inject-constructor rules, ambient environment, and documentation. The golden fixtures and stdio tests are the behavior baseline. After building the distribution, run it once over stdio with `initialize`, `ping`, `tools/list`, one call with an unknown argument, and one well-formed `feature_verify_workflow_open`. The validate phase runs the routed pack quality gate. Preparation ran no tests. The bundle was checked with `skill-bill goal preflight SKILL-375`.
