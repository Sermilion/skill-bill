# runtime-mcp architecture investigation (SKILL-375)

## Judgment

`runtime-mcp` points the right way. Main code imports application services, one engine inbound service (`FeatureTaskPhaseSettlementService`), domain and contract types, and the `runtime-core` composition root. It has no infrastructure imports, its four architecture baselines are empty, and `Main.kt` is the only place it reads the environment. I would keep the module, its dependency edges, the hand-written JSON-RPC framer, and the YAML contract as the single tool-schema source.

The problems are in its internal shape. SKILL-357 (merged in `e3fc39f07`, 2026-09-18) aimed to compose once, declare each tool once, and remove the forwarding layers. Its area history records "9/9 implemented". The code shows a different picture:

1. **Production code keeps a reflection shim so old tests still compile.** SKILL-357 removed `McpRuntimeContext` from main. To avoid rewriting the tests, the same commit added `componentForLegacyContext(value: Any)`, which uses reflection to call a `mcpComponent()` method on a test-only class, plus 27 `context: Any` overloads that route through it. A test named "workflow open retains context as its fourth positional argument" pins the shim in place.
2. **Each tool is still declared in three tables.** The tool name appears in `orderedToolNames`, `toolDescriptions`, and `TOOL_HANDLERS`. `McpTool.handler` is populated but never read. Special handling for `quality_check_finished` sits in four places, in three files.
3. **Forwarding objects remain.** `McpRuntime`, `McpRuntimeLifecycle`, and `McpWorkflowRuntime` sit between the handler functions and the services and add nothing.
4. **The removed feature-task MCP surface left dead code behind.** Every workflow tool binds `WorkflowFamilyKind.VERIFY`. The feature-task open branch, the `issue_key`/`subtask_id` continue fallbacks (which the schema rejects), seven decomposition continue mappers, and 14 test sites that use `TASK_RUNTIME` exercise paths that no tool can reach.

None of this is a production incident. Each item makes the next tool change more expensive, and the shim hides the real call path. The fix deletes layers. It adds no module, framework, SDK, or interface.

## Scope and method

- Baseline: commit `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b` on `feat/SKILL-368-build-logic-architecture-cleanup`, 2026-09-22.
- Production: 30 Kotlin files, 2,994 lines. Tests: 25 files, 4,618 lines. Repo-contract tests: 6 files, 1,283 lines.
- Method: I read all 30 production files in full. I ran grep and Python censuses for call sites, overloads, component member reads, dead declarations, literal wire keys, and workflow-family reachability. I diffed the CLI/MCP mapper pairs, traced `componentForLegacyContext` and the tool tables to their introducing commit with `git log -S`, and read the YAML branches for the verify workflow tools. I opened every architecture guard this bundle cites and traced its scan root (see Guard validity). I read the generated `InjectMcpComponent` and the governed-resources plugin to check feasibility. I did not delegate a line-level review.
- Prior work: SKILL-357 (this module). This investigation keeps SKILL-357's rejected-change list and records only what its landing left undone or added.

## Census

Baseline HEAD `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b`. Other sessions commit concurrently.

| Source set | Files | Lines |
| --- | ---: | ---: |
| main | 30 | 2,994 |
| test | 25 | 4,618 |
| repoTest | 6 | 1,283 |
| testFixtures | 0 | 0 |

Main packages: `core` 6, `shared` 6, `workflow` 7, `scaffold` 5, `review` 2, `telemetry` 2, `featuretask` 1, `lifecycle` 1. Test packages: `skillbill.mcp` 27, `shared` 2, `scaffold` 1, `telemetry` 1. Main has no file in the root `skillbill.mcp` package (F-010).

`build.gradle.kts` edges:

| Configuration | Edges |
| --- | --- |
| `api` | none |
| `implementation` | runtime-application, runtime-contracts, runtime-core, runtime-domain, runtime-engine, runtime-ports; kotlin-inject runtime, kotlinx-serialization-json, json-schema-validator, jackson-databind, jackson-dataformat-yaml |
| `ksp` | kotlin-inject compiler |
| `testImplementation` | runtime-infra:host, runtime-infra:workflow, runtime-infra:contracts, runtime-cli, runtime-infra:sqlite, testFixtures(runtime-infra:sqlite), testFixtures(runtime-core), testFixtures(runtime-ports), junit, kotlin-test |

The main edges match `RuntimeModuleCatalog` (`runtime-mcp` has `api = emptySet()`). No module imports `skillbill.mcp`. `RuntimeArchitectureTest` forbids inner-layer tests from importing it.

Declarations and types:

| Measure | Count | Note |
| --- | ---: | --- |
| Public top-level declarations | 1 | `main`. Everything else is `internal` or `private`, which SKILL-357 fixed. |
| Interfaces | 0 | Nothing to judge for substitutes. |
| `@Inject` classes | 0 | The only DI type is the `McpComponent` child component. |
| Typealiases | 5 | `McpToolSpec` (F-002), `McpToolHandler` (keep: a function-type name), and four private aliases in the decomposition mappers (deleted with F-004). |
| `Map<String, Any?>` in signatures | all handlers | Expected in an entry adapter. The raw-map rule covers only application, domain, and ports. |
| JSON/YAML parse sites | 8 | Framer (2), bridge (2), validator (3), projection (1). All sit at the adapter boundary. |
| Enum-token literals | 3 sites | `mapRemoteStatsWorkflow` restates `remoteStatsWorkflows` from `runtime-domain` `TelemetryConstants`. `readOnlyFullStateCommand` spells `"bill-feature-verify"` (deleted with F-004). The `"unrouted"`/`"unknown"` normalizer defaults are adapter policy, which the YAML owns. |

## Fixed checklist

1. **Dependency direction.** Clean. All main edges point inward, are `implementation` rather than `api`, and match `RuntimeModuleCatalog` L142. No infrastructure appears in main.
2. **Inbound side.** Handlers call services, but through a forwarding object (F-003). The adapter reads `WorkflowService.goalObservabilityEventValidator` and re-decodes artifacts (`McpWorkflowRuntime.kt:93,115`, `WorkflowGoalObservabilityMcpMapping.kt:39-45`). SKILL-370 F-002 owns that fix.
3. **Outbound side.** Not applicable: an entry adapter owns no ports. The governed-evidence socket client is transport code in the adapter, which is where it belongs.
4. **Domain richness.** The adapter has no domain rules. The only duplicated rule, the scaffold payload decode, belongs to SKILL-371 subtask 3. The CLI/MCP decomposition continue mappers are byte-identical apart from names; in MCP they are dead code (F-004).
5. **Composition.** One graph per process (`Main.kt:19-28`). `McpComponent` exposes its parent graph for scaffold, a service-locator reach (F-005). Production reflection resolves the component from `Any` (F-001).
6. **Entry-point leakage.** Not applicable. This module is the entry point.
7. **Ambient effects.** `Main.kt` reads `System.getenv` under a recorded exemption. `readlnOrNull` and `println` are the stdio transport. `UUID.randomUUID()` in `McpScaffoldRuntime.kt:55` is owned by SKILL-371 subtask 3, which moves session-id generation. No clock or nanoTime reads. The baseline guards resolve correctly (see Guard validity).
8. **State and transactions.** The only long-lived state is lazy, immutable schema caches in `TelemetryEventSchemaValidator` and `McpToolRegistry`. The adapter opens no transactions; services own them.
9. **Error model.** Errors are typed (`InvalidMcpToolArgumentError`, `InvalidTelemetryEventSchemaError`, `GovernedReviewEvidenceTransportError`). The scaffold `runCatching` rethrows `CancellationException`, and dispatch rethrows non-`Exception` throwables. Argument errors lose the tool name (F-006). The failure classification stays (see What stays).
10. **Cohesion and ownership.** No other module consumes MCP code. Handler files are spread across `core`, `shared`, and family packages (F-003).
11. **YAGNI.** Reflection shim and test-only members (F-001), `McpTool.handler` never read (F-002), forwarding objects (F-003), unreachable feature-task code (F-004), a test-only component member (F-005), five unused constants and one dead mapper (F-004).
12. **Naming and packages.** No stutter. 27 test files sit in the root package that main lacks (F-010). No package reaches the 12-sibling limit.
13. **Guard validity.** See the next section. `RuntimeEngineInboundApiTest` scans nothing.

## Guard validity

`ArchitectureScanSupport.runtimeRoot` resolves to the repository root (the first ancestor that contains `runtime-kotlin/`).

| Guard | Scan root as resolved | Reads runtime-mcp? |
| --- | --- | --- |
| `WireVocabularyArchitectureTest` | `mainSourceRoots` → `runtime-kotlin/runtime-mcp/src/main/kotlin` | Yes |
| Ambient environment, ambient clock, inject defaults, package-cycle baselines | `PrincipleEnforcementInventory` `mainScanRoot = "runtime-kotlin/$dir/src/main/kotlin"` | Yes |
| `RuntimeLayerBoundaryArchitectureTest` MCP checks (L395, L409-432) | `sourceFiles()` filtered to `runtime-kotlin/runtime-mcp/src/main/kotlin/` | Yes |
| `RuntimeArchitectureTest` "cli and mcp learning payloads use contract DTO mappers" (L388-401) | `sourcePath("skillbill/mcp/core/McpRuntime.kt")` | Yes. It is a source-text pin on a file F-003 deletes, so SKILL-375 deletes it (agreed with SKILL-373) |
| `RuntimeEngineInboundApiTest` | `runtimeArchitectureRoot.resolve("runtime-mcp/src/main/kotlin")`, which is the repository root plus `runtime-mcp`, a directory that doesn't exist. The walk hits `if (!Files.isDirectory(root)) return@forEach`. | **No, it scans nothing.** SKILL-371 subtask 1 restores it. |

This bundle relies only on guards that read the module. The learning-payload source-text pin is deleted, not retargeted. SKILL-373 subtask 2 deletes it by name if SKILL-375 hasn't.

## Module position

```text
Main.kt ──► RuntimeComponent (runtime-core) ──► McpComponent (kotlin-inject child)
   │                                               │
   └─ socket env set ──► GovernedReviewEvidenceBridge ──► Unix socket
                                                   ▼
McpStdioServer ─► McpToolDispatcher ─► handler fn ─► Mcp*Runtime object ─► application / engine service
                        │
                        └─► TelemetryEventSchemaValidator ◄── orchestration/contracts/telemetry-event-schema.yaml
```

| Edge | Symbols used | Verdict |
| --- | --- | --- |
| runtime-application | 53 imports: review, learning, telemetry, workflow, system, update-check services and result models | Correct inbound adapter edge |
| runtime-engine | `FeatureTaskPhaseSettlementService` and its three request/ack models | Correct: settlement is an engine inbound API (`RuntimeEngineInboundApiTest`) |
| runtime-core | `RuntimeComponent`, `create`, `SkillBillVersion` | Correct for a process entry. One leak: scaffold reads `runtimeComponent.scaffoldGateway` directly (F-005) |
| runtime-domain / contracts / ports | Wire keys, typed errors, domain view models, `RuntimeDiagnostics` | Correct |
| infrastructure | none in main | Correct |

## Principle assessment

| Principle | Assessment | Evidence |
| --- | --- | --- |
| Dependency rule / hexagonal | Holds | No infrastructure imports in main. Adapter maps typed results to wire maps. |
| Composition / DI | Holds at process level, leaks inside | One graph per process (SKILL-357 fixed this). Production reflection resolves the component from `Any` (F-001). `McpComponent` exposes its parent graph and a test-only member (F-005). |
| Single source of truth | Violated | A tool is spelled in three Kotlin tables. Quality-check rules appear in four places (F-002). |
| SRP / simplicity | Violated | Handler, then object, then service: two hops where one is enough (F-003). Duplicate `handleLine` and `call` bodies exist for tests (F-001). |
| YAGNI | Violated | Unreachable feature-task branches, mappers, tests, and a golden fixture. `McpRuntime.version` backs no tool. Five framer constants and one mapper are unused (F-004). |
| Error contracts | Minor gap | Argument errors report tool `<unknown>` (F-006). |
| Wire vocabulary rule | Partly violated | 19 literal `"db_path"` and 29 literal `"error"` writes. A stale inventory marker points at a file that doesn't exist (F-007). |
| Build conventions | Minor gap | A hand-rolled Copy task duplicates the `skillbill.governed-resources` convention (F-008). |
| Test package alignment | Violated | 27 test files in a root package that main lacks (F-010). |
| Test value | Coupled to internals | Tests call internal objects through the shim rather than through the tool boundary (F-001). |

Common practice in large Kotlin and JVM codebases points the same way. The official MCP SDKs register each tool once, with its name, description, schema, and handler, and derive both listing and dispatch from that registration. Production code carries no reflection or overloads that exist only for tests. Tests drive the public seam (here, `handleLine` or the dispatcher) with a real component built by a test fixture. Code that no entry point can reach is deleted, not kept "in case". The findings below mark where this module departs from those norms.

## Findings

### F-001. High. A production reflection shim and duplicate entry paths exist for tests

Evidence:

- `shared/McpComponentAccess.kt`: `componentForLegacyContext(value: Any)` looks up a declared method named `mcpComponent` (or `mcpComponent$…`), calls `setAccessible(true)`, and casts the result to `McpComponent`. The only class with that method is the test-only `src/test/.../shared/McpRuntimeContext.kt`.
- 27 `context: Any` overloads in 6 main files route through it (`McpRuntime` 10, `McpRuntimeLifecycle` 7, `McpWorkflowRuntime` 6, `McpStdioServer` 1, `McpToolDispatcher` 1, `McpWorkflowToolHandlers` 1), giving 33 reflective call sites. `McpWorkflowOpenArgs` carries both `component: McpComponent?` and `context: Any?`.
- `McpStdioServer` has two 40-line `handleLine` bodies. The component-less one exists so tests can check validation errors without building a component, and it returns "A component is required for tool calls." for any valid call. `McpToolDispatcher.call` repeats validate-then-dispatch next to `callValidated`. Only tests call either duplicate.
- `McpRuntime.version` backs no tool. Its only caller is a test.
- `git log -S componentForLegacyContext` shows the shim arrived in `e3fc39f07` (SKILL-357), whose acceptance criterion 1 said `McpRuntimeContext` would be gone.
- `McpRuntimeTest` "workflow open retains context as its fourth positional argument" asserts the shim's signature, not any behavior.

Why it matters: reflection on a method name defeats the compiler. Renaming the test helper breaks production dispatch at runtime. The overloads double every handler signature, and the shim hides the real call path from readers and from the architecture guards.

Fix: delete `McpComponentAccess.kt`, every `context: Any` overload, the component-less `handleLine`, `McpToolDispatcher.call`, `McpRuntime.version`, and the `context` field. Tests build an `McpComponent` through a test fixture (the existing `McpRuntimeContext` helper can return one) and drive `McpStdioServer.handleLine(line, component)` or the dispatcher's validated entry. Delete the positional-signature test.

### F-002. High. A tool is declared in three tables, and one tool's rules live in four places

Evidence:

- `core/McpToolRegistry.kt`: `orderedToolNames` (25 literals) and `toolDescriptions` (25 entries). `core/McpToolDispatcher.kt`: `TOOL_HANDLERS` (25 entries). All three are keyed by the same strings.
- `McpTool.handler` is assigned from `McpToolDispatcher.handlerFor(name)` and never read. Dispatch goes through `TOOL_HANDLERS`.
- `McpTool.normalize` is chosen by `if (name == QUALITY_CHECK_FINISHED)` inside the registry.
- `quality_check_finished` rules are spread across four places: the registry `normalize` branch, `RUNTIME_OWNED_QUALITY_CHECK_KEYS` in the dispatcher, `advertisementRemovedPropertyKeys` in `McpInputSchemaProjection` (the same three keys again), and `stripRuntimeOnlyEnumValues`, which also special-cases `feature_verify_finished`.
- `internal typealias McpToolSpec = McpTool`, and `toolNamed` is a linear scan.
- `runtime-mcp/agent/history.md` (SKILL-357 subtask 1) records "Replaced parallel tool registries with one ordered declaration list". That did not land.

Fix: one ordered `List<McpTool>`. Each entry carries its name, description, handler, and any tool-specific normalizer, runtime-owned argument keys, and advertised enum subset. `tools/list`, validation, schema projection, and dispatch read that list. Dispatch looks tools up in a map built once from it. No tool-name comparison appears outside its declaration. Delete `TOOL_HANDLERS`, `orderedToolNames`, `toolDescriptions`, `McpToolSpec`, and `RUNTIME_OWNED_QUALITY_CHECK_KEYS`.

### F-003. Medium. Forwarding objects between handlers and services

Evidence:

- `core/McpRuntime.kt` (181 lines): 10 shim overloads plus methods that call one service and map the result. `newSkillScaffold` forwards every parameter to `McpScaffoldRuntime.newSkillScaffold`.
- `shared/McpRuntimeLifecycle.kt` (125 lines): 7 shim overloads, and five `withAutoSync { service.x(request).toPayload() }` methods.
- `workflow/McpWorkflowRuntime.kt` (155 lines): shim overloads plus one-line service calls. `McpWorkflowOpenArgs` is a parameter object with a single caller.
- Handler placement doesn't follow the tool families. Review, learning, remote-stats, and scaffold handlers sit at the bottom of `core/McpToolDispatcher.kt`. Lifecycle handlers sit in `lifecycle/` and call `shared/McpRuntimeLifecycle`. Review result mappers sit in `shared/McpResultMappers.kt`.

SKILL-357 F-008 asked for this fold. The shim (F-001) kept it from happening.

Fix: one handler file per tool family (review and learning, lifecycle telemetry, remote telemetry and system, workflow, feature-task settlement, scaffold). Each handler parses its arguments, calls the service, and maps the result. Delete `McpRuntime`, `McpRuntimeLifecycle`, `McpWorkflowRuntime`, and `McpWorkflowOpenArgs`. `core` keeps the server, dispatcher, registry, and schema projection. `shared` keeps the component, framer, and argument accessors.

### F-004. Medium. The removed feature-task surface left unreachable code and tests

Evidence:

- All seven `feature_verify_workflow_*` entries in `TOOL_HANDLERS` pass `WorkflowFamilyKind.VERIFY`. No tool passes `TASK_RUNTIME`. SKILL-175 subtask 4 deleted the feature-task MCP tools.
- `McpWorkflowRuntime.open` runs `openFeatureTask` when `kind != VERIFY`. That branch, `requiredRepositoryIdentity`, `requiredGovernedSpecPath`, and the `FeatureTaskRouteScope` import can't run.
- `workflowContinue` falls back to `issue_key` and reads `subtask_id`. The YAML branch `featureVerifyWorkflowContinueEvent` declares `additionalProperties: false` with only `workflow_id`, so validation rejects both arguments before the handler runs.
- `WorkflowService.continueWorkflow` produces the seven `Decomposition*` results only for `WorkflowFamily.TASK_RUNTIME` (`WorkflowService.kt:397`, via `DecompositionWorkflowContinuation`). The 103-line `WorkflowContinueMcpBranchMapsDecomposition.kt` maps results that the verify tools can't produce. The `workflow` branch of `readOnlyFullStateCommand` is unreachable for the same reason.
- `GoalPlanningStatusSnapshot.toMcpMap()` has no caller. `McpProtocolFramer` declares `SCHEMA_TYPE_KEY`, `SCHEMA_ADDITIONAL_PROPERTIES_KEY`, `SCHEMA_MIN_LENGTH_KEY`, `SCHEMA_PATTERN_KEY`, and `SCHEMA_ITEMS_KEY`, and none of them is referenced.
- Tests: 14 `TASK_RUNTIME` sites in `McpRuntimeTest` ("mcp workflow methods cover experimental feature-task-runtime verbs") and `McpWorkflowContinuationRuntimeTest` (both tests), plus the `mcp-feature-task-runtime-workflow.json` golden fixture. They reach these paths only through the F-001 shim.

Fix: delete the unreachable branches, mappers, constants, tests, and golden fixture. The Kotlin `when` over the sealed `WorkflowContinueResult` stays exhaustive. The `Decomposition*` variants share one arm that throws `UnsupportedOperationException` naming the variant. Reaching that arm means an application invariant broke, so the failure must go through the dispatcher's capture path. `dispatchMcpToolCall` returns `ShellContentContractException`, `IllegalArgumentException`, and `IllegalStateException` to the client without capture, and captures every other `Exception`, so `IllegalStateException` would hide the bug. No new exception type is needed.

Cross-module note, not in this bundle: the same reachability holds for runtime-cli. `WorkflowCliCommands` continues only `VERIFY_KIND`, and its `--subtask-id` option can't take effect. That leaves `DecompositionWorkflowContinuation` (337 lines), the decomposition half of `ContinuationStepResult` (149 lines), the seven `Decomposition*` variants, and the CLI mirror mappers unreachable from every entry point. Removing them touches runtime-application, which SKILL-370 is restructuring, so it belongs in a follow-up after SKILL-370 lands. Confirm with the compiler that no goal-runner path calls it.

### F-005. Medium. `McpComponent` exposes more than handlers read

Evidence (member reads in main / tests):

- `runtimeComponent`: public. `McpScaffoldRuntime` reads `runtimeComponent.scaffoldGateway` and `runtimeComponent.resolvedEnvironmentContext` through it, which is a service-locator reach into the parent graph.
- `databaseSessionFactory`: 0 main reads, 2 test reads.
- `clock`: 1 main read, used for the scaffold session id. SKILL-371 subtask 3 moves session-id generation into an application scaffold operation.

Fix: `McpComponent` declares an accessor for each type a handler reads, including the scaffold dependency. No handwritten code reads `runtimeComponent`, whose `val` stays only because kotlin-inject needs it for the parent-component parameter. Remove `databaseSessionFactory`, and remove `clock` once scaffold no longer reads it. Tests get test-only dependencies from their fixture.

### F-006. Low. Argument errors lose the tool name

Evidence: every accessor in `shared/McpToolArguments.kt` throws `InvalidMcpToolArgumentError("<unknown>", …)`. Schema validation catches most of these first, but open-object branches and handler-level checks still surface "MCP tool '<unknown>' argument …" to the client.

Fix: handlers read arguments through an accessor that knows the tool name (for example, an extension taking the tool name, or a small wrapper built by the dispatcher), so every argument error names its tool.

### F-007. Low. Output wire keys spelled in place

Evidence: the workflow, telemetry, and feature-task mappers write `"db_path"` 19 times, `"error"` 29 times, `"continue_status"` 6 times, and `"read_only_full_state_command"` and `"launch_projection"` twice each as literals. `WorkflowWirePayloadKeys`, `LifecycleTelemetryPayloadKeys.ERROR`, and `TelemetryProxyPayloadKeys` already own several of them. `LearningPayloadKeys.DB_PATH` also exists, but SKILL-374 moves `LearningPayloadKeys` into runtime-application, so workflow output must not borrow it. SKILL-357 acceptance criterion 6 required constants for every key. `WireVocabularyGovernedSeamInventory` lists the marker `cli/workflow/WorkflowContinueMcpBranchMapsDecomposition`, a path that doesn't exist.

`mapRemoteStatsWorkflow` spells `"bill-feature-verify"` and `"feature-task-runtime"`, which `remoteStatsWorkflows` in `runtime-domain` `TelemetryConstants` already owns.

Fix: keys that both CLI and MCP write (`db_path`, `error`, `read_only_full_state_command`, `continue_status`, `launch_projection`) reference their `runtime-contracts` owner, and a constant is added to the existing owner where none exists. Keys that only runtime-mcp writes follow the placement rule in force at implementation time. Today (AGENTS.md) that means a `runtime-contracts` owner. If SKILL-374 has landed, it means an MCP-owned `*Keys` object in runtime-mcp. `mapRemoteStatsWorkflow` accepts the values in `remoteStatsWorkflows` and keeps only the `verify` alias as its own rule. Remove the stale inventory marker. Scaffold output maps are out of scope because SKILL-371 subtask 3 replaces them.

### F-008. Low. A hand-rolled schema copy task duplicates the build convention

Evidence: `runtime-mcp/build.gradle.kts` defines `copyTelemetryEventSchema` as an untyped `Copy` task (about 35 lines), with `import java.io.File`, `rootProject.projectDir.parentFile`, a `doFirst { require(...) }`, a manual `sourceSets` resource directory, and `processResources`/`processTestResources` wiring. `runtime-infra/contracts` and `runtime-infra/workflow` declare the same kind of copy through the `skillbill.governed-resources` plugin, and SKILL-368 subtask 2 gives that plugin a typed `copy(taskName, source, owner, destination)` DSL.

Fix: apply `skillbill.governed-resources` and declare the schema with one `copy(...)` call to destination `skillbill/mcp/contracts`. This runs after SKILL-368 lands.

### F-009. Low. Validator plumbing

Evidence in `telemetry/TelemetryEventSchemaValidator.kt`:

- `loadSchemaDocument` and `compileSchema` each use `var failure` with three near-identical catch blocks that wrap an exception in `error.let { … }`. `compileSchema` catches `InvalidTelemetryEventSchemaError`, which it can't throw.
- `assertIdentity(yamlText: String)` is called only by `TelemetryEventSchemaCleanupTest`.
- `McpInputSchemaProjection` calls `canonicalSchemaDocument().path($defs)` twice per tool.
- The YAML header names `runtime-mcp/src/main/kotlin/skillbill/mcp/TelemetryEventSchemaPaths.kt` (the file lives in `src/repoTest/.../telemetry/`) and says coherence rules live in `McpToolDispatcher` (they live in the validator).

Fix: one `try` per function that maps `IOException` and `JsonProcessingException` to the typed error. Drop the string overload and have the test parse its own YAML. Resolve `$defs` once. Correct the header comment. Behavior and messages stay the same.

### F-010. Low. Tests sit in a package that main doesn't have

Evidence: 27 of 31 test files declare `package skillbill.mcp`. Main has no file in that package; its code lives in `core`, `shared`, `workflow`, `scaffold`, `review`, `telemetry`, `featuretask`, and `lifecycle`. `McpRuntimeTest` is 1,345 lines and covers review, telemetry, workflow, scaffold, and system tools.

Fix: while F-001 moves tests onto the tool boundary, put each test file in the family package whose handler it exercises. Protocol and stdio suites go in `core`. Don't split files by size alone: a file moves when its tests belong to different families.

## Feasibility notes

- **Parent component `val` (F-005).** The generated `InjectMcpComponent` reads `runtimeComponent.learningService` and the other accessors from its subclass (`build/generated/ksp/.../InjectMcpComponent.kt`). The constructor parameter must stay a `val` visible to the subclass. The criterion is therefore "no handwritten reader", not "private". Whether `protected` works with kotlin-inject is something only compiling can confirm, and the spec doesn't require it.
- **Accessor removal and SKILL-373.** SKILL-373 subtask 1 keeps every `RuntimeComponent` accessor that generated `InjectMcpComponent` reads. Removing `McpComponent.databaseSessionFactory` removes one reader of `RuntimeComponent.databaseSessionFactory`, and other readers remain (CLI, tests). No conflict.
- **Governed-resources plugin (F-008).** `GovernedResourcesConventionPlugin` adds `generatedRoot` to the `main` resources and makes `processResources` depend on each copy task. Tests read the schema from the main resources on the test runtime classpath, so the explicit `processTestResources` dependency isn't needed. The destination argument yields the same classpath path, `skillbill/mcp/contracts/telemetry-event-schema.yaml`. The `copy(taskName, source, owner, destination)` signature exists only on the SKILL-368 branch, so F-008 lands after SKILL-368.
- **Exhaustive `when` (F-004).** `WorkflowContinueResult` is sealed. Deleting the mappers requires one grouped arm for the `Decomposition*` variants. The compiler proves exhaustiveness. The arm throws `UnsupportedOperationException`, not `IllegalStateException`, because `dispatchMcpToolCall` (`McpStdioServer.kt:131-137`) captures only exceptions outside the three client-error types. A non-`Exception` throwable would be rethrown and end the stdio loop.
- **Tool-list refactor (F-002).** `McpInputSchemaProjection` today receives only a tool name. It changes to receive the `McpTool` declaration. `McpToolRegistry.tools` is built lazily from the projection, so declaration order and the lazy projection must avoid an init cycle. Compiling and the `tools/list` golden confirm this.

## Over-engineering register

Paths relative to `runtime-kotlin/runtime-mcp/src/main/kotlin/skillbill/mcp`.

- `shared/McpComponentAccess.kt`: delete. Reflection shim.
- 27 `context: Any` overloads, the component-less `McpStdioServer.handleLine`, `McpToolDispatcher.call`, `McpRuntime.version`: delete. Test-only production paths.
- `core/McpRuntime.kt`, `shared/McpRuntimeLifecycle.kt`, `workflow/McpWorkflowRuntime.kt`, `McpWorkflowOpenArgs`: fold into family handler files. Forwarding objects.
- `TOOL_HANDLERS`, `orderedToolNames`, `toolDescriptions`, `McpToolSpec`, `RUNTIME_OWNED_QUALITY_CHECK_KEYS`: replaced by one tool list.
- `workflow/WorkflowContinueMcpBranchMapsDecomposition.kt`, the feature-task open branch, the continue fallbacks, `GoalPlanningStatusSnapshot.toMcpMap`, and five framer constants: delete. Unreachable.
- `copyTelemetryEventSchema`: replace with a convention declaration.

Estimate: production roughly 2,994 → 2,300 to 2,400 lines, and about 26 files. Tests shrink by the TASK_RUNTIME cases and the positional-signature test. This is an estimate, not a measured diff.

## What stays unchanged

- The module and its main-source edges, including the engine settlement edge and the `runtime-core` parent component.
- The hand-written JSON-RPC framer and sequential stdio loop. SKILL-357 rejected an MCP SDK, and nothing here changes that.
- `orchestration/contracts/telemetry-event-schema.yaml` as the single tool-input schema, including its name. It is a published contract that the proxy, docs, and repo tests read. Renaming it for accuracy would cost more than it returns.
- `TelemetryEventSchemaValidator` in this module, its coherence rules, and the schema projection approach (SKILL-357 decisions).
- The two-mode `Main.kt` and its ambient-environment exemption.
- The dispatcher's failure classification. `ShellContentContractException`, `IllegalArgumentException`, and `IllegalStateException` are returned to the client as tool errors, and other exceptions are captured. Application code uses `require`/`check` for caller-input validation, so capturing those as exceptions would record expected input errors. The error is surfaced to the client, not swallowed.
- CLI/MCP presentation pairs kept by the 2026-09-03 decision, apart from the MCP decomposition mappers F-004 deletes.
- The CLI parity tests that use `runtime-cli` on the test classpath, the goldens other than the feature-task golden, and the infrastructure test dependencies SKILL-356 owns.
- `orchestrated` payload envelopes and every tool's wire output.
- `McpToolHandler`, the one typealias that names a function type rather than re-exporting another module's type.
- No handler interfaces or per-tool classes. A tool is a value in a list with a function handler. An interface per handler would have no second implementation and no test substitute.
- No `explicitApi()`. Every declaration except `main` is already `internal`, and nothing outside the module consumes it.
- No file splits by line count. `McpRuntimeTest` shrinks and moves by tool family (F-010), not by size.

## Coordination with concurrent bundles

| Bundle | Overlap with runtime-mcp | Owner | Sequencing |
| --- | --- | --- | --- |
| SKILL-368 build-logic | Adds the typed `governedResources.copy` DSL that F-008 uses | SKILL-368 | SKILL-375 after SKILL-368 |
| SKILL-370 runtime-application | F-002 removes `WorkflowService.goalObservabilityEventValidator` and puts the decoded summary on workflow get/open results. Subtask 2 renames application packages that MCP imports. | SKILL-370 | SKILL-375 after SKILL-370. Rebase and keep its mapping. |
| SKILL-371 runtime-cli | Subtask 1 restores `RuntimeEngineInboundApiTest` scanning. Subtask 2 aligns update-check fields. Subtask 3 replaces both scaffold parsers with one application decoder and operation, and moves session id and `repo_root`. | SKILL-371 | SKILL-375 after SKILL-371. The MCP scaffold handler calls SKILL-371's operation. |
| SKILL-372 runtime-domain | Subtask 2 moves the raw artifact reads in `WorkflowGoalObservabilityMcpMapping.kt:20-23` to domain accessors | SKILL-372 | SKILL-375 after SKILL-372 |
| SKILL-373 runtime-core | Subtask 1: moves `RuntimeContext`, `TransportContext`, `WorkflowOpsContext`, and `OptionalCallbacks` into runtime-core `skillbill.di.core` (changes imports in `Main.kt` and the `McpRuntimeContext` test fixture). It turns the bare `String` version binding into a runtime-ports value type. `McpProtocolFramer` keeps reading `SkillBillVersion.VALUE`, so bridge mode is unaffected (confirmed by the SKILL-373 session). It prunes accessors no handwritten or generated code reads. Subtask 2 deletes the learning-payload source-text pin by name, and skips it if SKILL-375 already did. Subtask 3 moves the suite to `runtime-core/src/repoTest`. | SKILL-373 for composition and the suite. SKILL-375 for MCP code. | Either order. SKILL-373 states that each bundle updates the suite wherever it lives. Whichever of 373 subtask 1 and 375 lands second rebases the fixture and `Main.kt` imports. |
| SKILL-374 runtime-contracts | Subtask 1 moves `ShellContentContractException` to `skillbill.error.core` (import change in the dispatcher) and narrows `JsonCodec.parseObjectOrNull`'s catch. The MCP framer relies on it returning null for malformed JSON so the server answers `-32700`. Subtask 2 applies the placement rule: the four DTOs in `McpAdapterContracts.kt` (`McpReviewImportSkippedContract`, `McpTriageSkippedContract`, `McpLearningsSkippedContract`, `McpOrchestratedPayloadContract`) move into runtime-mcp, and `LearningPayloadKeys` moves into runtime-application. `UpdateCheckContract` and `UpdateCheckPayloadKeys` stay in runtime-contracts, because SKILL-371 subtask 2 renders CLI update-check output through them. SKILL-374 subtask 1 now guarantees that `parseObjectOrNull` returns null for malformed text and non-object roots, so `-32700` behavior is kept. Both bundles would edit the learning-payload source-text pin; SKILL-375 deletes it (agreed with SKILL-373). | SKILL-374 | Either order. F-007 follows whichever rule is in force. If SKILL-374 lands first, SKILL-375 puts the moved DTOs in the family package of the handler that writes them. If SKILL-375 lands first, SKILL-374's census moves MCP-only constants and places the DTOs beside the family handlers. |
| SKILL-376 runtime-infra | Keeps MCP integration tests on real adapters. No MCP source overlap. | SKILL-376 | Independent |
| SKILL-377 runtime-ports | Subtask 2 makes `WorkflowStateRepository` family-keyed and removes the `WorkflowFamily` extension helpers. It requires CLI and MCP workflow get, list, latest, and continue output to stay byte-identical. MCP reaches this only through `WorkflowService`. | SKILL-377 | Independent. SKILL-375's workflow goldens are part of SKILL-377's proof. |
| SKILL-378 runtime-engine | Subtask 1 deletes experiment support, which SKILL-373 subtask 1 waits on. Subtask 3 deletes engine typealiases (`GoalRunnerPersistenceModelAliases.kt` and others), requires CLI and MCP to import the owning types, updates the pinned inbound API list, and makes engine types that nothing outside the engine imports `internal`. `FeatureTaskPhaseSettlementService` and its request and ack types stay inbound. | SKILL-378 | Independent. Whichever lands second fixes MCP imports of `skillbill.goalrunner.model.*`. SKILL-375 already deletes the `GoalPlanningStatusSnapshot` import. |

No ordering cycle: SKILL-375 waits on 368, 370, 371, and 372. None of those, nor 373, 374, 376, 377, or 378, waits on SKILL-375. SKILL-373's subtasks 2 and 3 wait on 371 subtask 1, 374, and 376 subtask 3, and they update the suite wherever SKILL-375 left it.

Gaps in sibling bundles were passed to their owning sessions and both are resolved. SKILL-373: the framer keeps reading `SkillBillVersion.VALUE`, so bridge mode is unaffected. SKILL-374: subtask 1 scope item 4 and AC10 keep `parseObjectOrNull` returning null for malformed JSON and non-object roots, so MCP keeps answering `-32700`.

I didn't edit any other bundle.

## Limits

This is a static census. I didn't run tests or the quality gate while preparing it. The line estimate is not a measured diff. Reachability claims for F-004 rest on the tool table, the YAML branches, and `WorkflowService.continueWorkflow`. The implementer should confirm each deletion with the compiler and the existing golden and stdio tests.

Only compiling can confirm these points:
- kotlin-inject accepts the trimmed `McpComponent`, and the parent `val` visibility works as described.
- The declaration-driven `McpInputSchemaProjection` has no init cycle with `McpToolRegistry`.
- The grouped `Decomposition*` arm keeps the `when` exhaustive after the SKILL-370 and SKILL-372 changes to `WorkflowContinueResult`.
- The file-level instructions still match the code after SKILL-368, 370, 371, and 372 land. The spec requires a refresh pass before implementation for this reason.
- The governed-resources copy produces the same classpath resource.
