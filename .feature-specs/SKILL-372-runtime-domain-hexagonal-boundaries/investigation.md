# Runtime-domain hexagonal boundary investigation

## Execution rule

This bundle runs on the current tree. It does not wait for a subtask of another issue. Ordering notes later in this file are overlap context. If a change this bundle's acceptance criteria need is missing, make it here. If it is already present, keep it.

## Judgment

Keep the module, its dependency direction, and its `runtime-domain` → `runtime-contracts` edge. Domain production code imports only `java.time`, `java.security`, `java.math`, and Kotlin stdlib. It has no ambient clock, randomness, environment, IO, threads, coroutines, `@Inject`, or DI. Its tests import nothing above domain. SKILL-351 fixed most of the module's internal problems.

The remaining defects are at the boundaries with other modules, and they are real:

- **The central aggregate is a database row.** `WorkflowStateSnapshot` carries `stepsJson` and `artifactsJson` text. Every layer parses that text itself, and 108 call sites turn corrupt JSON into an empty workflow.
- **The aggregate does not enforce its own rules.** `updateRecord` skips the domain's own validation. Only one of 31 update paths validates first.
- **The artifact map inside the row is a public, untyped bag.** 38 of 39 domain artifact keys are read directly from engine, application, SQLite, and MCP. Lenient copies of strict domain decoders have grown in three modules.
- **Domain rules call infrastructure.** 27 domain call sites take JSON-schema validator ports.
- **Domain has four package cycles** that the acyclicity guard cannot see, because it compares only top-level areas and only finds two-node cycles.

The fix removes code. Decode once at the port mapping. Run the existing validation on every write. Put one typed accessor on each artifact and make the keys `internal`. Validate wire payloads in the adapters. Move shared vocabulary to break cycles. Delete forwarding extensions, aliases, dead code, and a dead resource. The only guard change is making the existing cycle scanner run at real package granularity. No module, framework, dependency bag, or new architecture-test class is added. Net production change is about −950 lines.

## Method and baseline

- Baseline HEAD: `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b`, rechecked after `git fetch` immediately before writing. runtime-domain has no uncommitted change. The sorted production-file digest is `b7799fd116e4ffd9a8533d85861d8a3df44564d1188c10de04cb2f4107c727dd`.
- Tests: `./gradlew :runtime-domain:test` passed, 844 tests, 0 failed, 0 skipped.
- Censuses: Python and grep scripts over every `.kt` file outside `build/` and `build-logic/` in `runtime-kotlin`. No review subagents were used. Files cited in findings were read at the cited ranges.
- Context read: `CLAUDE.md`, `runtime-kotlin/ARCHITECTURE.md` (Design Principles, Gradle Modules, Package Ownership, Enforcement Status), `docs/code-principles.md`, `runtime-kotlin/runtime-domain/agent/decisions.md` and `history.md`, the prior SKILL-351 bundle, and the seven sibling bundles SKILL-370, 371, 373, 374, 375, and 376.
- First pass and verification: the first pass of this bundle missed the cycle, invariant, validator-parameter, timestamp, vocabulary, resource, coercion-landing, single-consumer, interface-substitute, construction, orphan-test, and guard-validity checks. They were added in two follow-up passes and are measured below.

## Census

### Size and build

| Source set | Files | Lines |
| --- | ---: | ---: |
| main | 349 | 30,368 |
| test | 118 | 18,701 |
| testFixtures | 3 | 34 |

`runtime-domain/build.gradle.kts` declares:

- main: `implementation(project(":runtime-contracts"))`
- test: `jackson-databind`, `jackson-dataformat-yaml`, `junit-jupiter`, `kotlin-test`
- `java-test-fixtures`, holding three `Noop*Validator` files
- a `processResources` block expanding `skillbill/version.properties`, which nothing in domain reads (F-014)

`runtime-ports`, `runtime-application`, and `runtime-engine` expose domain as `api(...)`, so every public domain declaration is transitive ABI for CLI and MCP. Infra modules and `runtime-core` use `implementation`.

Packages: 86, of which 22 hold one file and 32 hold two or fewer. Maximum depth below `skillbill` is 7.

### Consumers

Main-source files in each module that import at least one domain package:

| Module | Files | Module | Files |
| --- | ---: | --- | ---: |
| runtime-engine | 844 | infra/workflow | 51 |
| runtime-application | 330 | infra/launcher | 31 |
| infra/skills | 228 | infra/contracts | 30 |
| infra/sqlite | 206 | runtime-core | 25 |
| runtime-ports | 142 | runtime-mcp | 18 |
| runtime-cli | 66 | infra/host, infra/http | 11, 7 |

481 public domain declarations are imported by exactly one other module's main source:

| Module | Declarations | Main packages |
| --- | ---: | --- |
| engine | 206 | `workflow.taskruntime` 148 |
| application | 98 | `review.context` 40 |
| infra/skills | 71 | `scaffold.policy` 30, `scaffold.model` 18 |
| infra/sqlite | 58 | `review.model` 18, `goalrunner` 13 |
| all others | 48 | — |

Engine and application consuming domain is normal layering. The outlier is `infra/skills`, the only consumer of the scaffold rules. It hosts `ScaffoldService` (planning, execution, rollback, install) behind a coarse `ScaffoldGateway` port, and application has no scaffold use case. That is outside this module and is recorded under Coordination.

### Visibility

The census is FQN-aware: a declaration counts as used when another file imports its fully qualified name or uses the simple name in the same package. There are no wildcard imports anywhere.

| Bucket | Public top-level declarations |
| --- | ---: |
| used by another module's main code | 891 |
| used outside domain only by tests | 78 |
| used only elsewhere in domain | 99 |
| used only in the declaring file | 54 |
| unreferenced | 17 |

### Interfaces and substitutes

| Interface | Production implementations | Test substitutes |
| --- | --- | ---: |
| `WorkflowSnapshotValidator` | 1 (infra/contracts) | 18 |
| `ReviewContextEnvelopeValidator` | 1 (infra/contracts) | 17 |
| `DecompositionManifestValidator` | 1 (infra/contracts) | 8 |
| `FeatureTaskRuntimeWireArtifactValidator` | 1 (infra/contracts) | 5 |
| `InstallPlanWireValidator` | 1 (infra/contracts) | 3 |
| `FeatureTaskRuntimePhaseOutputValidator` | 2 (infra/contracts, engine) | 1 |
| `RequiredArtifactPresenceResolver` (`fun interface`) | 2 (domain strategies) | 0 |

The other 20 interfaces are sealed result types. Every port above has an adapter and a substitute or a second strategy, so each is justified as an interface. F-007 concerns where the validators are called, not whether they exist.

### Aliases, construction, tests

- **Typealiases:** 10 in domain. 5 more in other modules target domain types (4 in application, 1 in engine). The repository has 95 in total.
- **Domain classes built by hand:** `WorkflowEngine(workflowSnapshotValidator)` is constructed at 10 main sites (application 2, engine 6, SQLite 2) and about 30 test sites. Its second parameter, `checkpoint`, is never passed anywhere (F-007).
- **Tests:** 9 domain test files sit in 8 packages that do not exist in main (`skillbill.config`, `skillbill.install`, `skillbill.review.review`, `skillbill.workflow`, `…workflow.failureidentity`, `…taskruntime.review`, `…taskruntime.semantic`, `…taskruntime.unbounded`). No domain test imports ports, application, engine, infrastructure, DI, CLI, or MCP.

### Maps, JSON, tokens

- `Map<String, Any?>` appears 327 times in 78 domain files. There are 67 `toArtifactMap`/`fromArtifactMap` functions and 71 `fromWire` functions.
- JSON text handling in domain: `parseObjectOrNull` 5, `parseValue` 3, `parseJsonArrayStrict` 1, `mapToJsonString` 8, `valueToJsonString` 2. `JsonCodec.anyToStringAnyMap`, a map coercion rather than a parse, is used 88 times.
- **Workflow row text:** `artifactsJson` is referenced in 43 main files across six modules and `stepsJson` in 15. Lenient decoders are called from 108 sites (F-002).
- **Artifact keys:** domain declares 39 `*_ARTIFACT_KEY` constants, and 38 are referenced from other modules' main code. About 60 raw `artifacts[KEY]` reads sit outside domain and 14 inside (F-005).
- **Enum wire tokens:** 170 distinctive domain tokens (containing `_` or `-`). 56 of them appear as literals in other main files, 153 occurrences in all. Most are homonymous wire keys, such as `feature_size` as a column name, so this is not a finding. The genuine restatements in application (`in_progress` for `DecompositionStatus`) belong to SKILL-370 F-012.

### Guard validity

I opened every guard this bundle cites and traced its scan root. `ArchitectureScanSupport.runtimeRoot` is the repository root, so a guard reads domain files only when it resolves a `runtime-kotlin/…` path.

| Guard | Scan root | Reads domain? |
| --- | --- | --- |
| `ApplicationPackageAcyclicityArchitectureTest` | `PrincipleEnforcementInventory.mainScanRoot` = `runtime-kotlin/runtime-domain/src/main/kotlin` | Yes, but cuts packages to their first segment and finds only 2-node cycles (F-004) |
| `PackageSiblingCountArchitectureTest`, `PackageClusteringArchitectureTest` | `runtimeKotlinModuleDirectory(...)/src/main/kotlin` | Yes |
| `ProductionFileLineCeilingArchitectureTest` | `runtime-kotlin` | Yes |
| `TypedParseBoundaryArchitectureTest` | explicit `runtime-kotlin/runtime-domain/...` files, read with `readText` | Yes |
| `WireVocabularyArchitectureTest` | `mainSourceRoots` via `runtimeKotlinModuleDirectory` | Yes |
| `RuntimeLayerBoundaryArchitectureTest` (import rules) | `startsWith("runtime-kotlin/runtime-domain/...")` | Yes |
| `AmbientEnvironmentArchitectureTest` | `mainScanRoot` | Yes |
| `RuntimeGradleModuleLayeringTest` | `runtimeKotlinModuleDirectory` | Yes |
| `RuntimeRawMapArchitectureTest` (inner-layer raw maps) | `startsWith("runtime-domain/src/main/kotlin/")` against repo-relative paths | **No, scans nothing.** SKILL-371 subtask 1 owns the fix |
| `RuntimeArchitectureTest` domain rules | same bare-path filter | **No, scans nothing.** SKILL-371 subtask 1 owns the fix |

This bundle does not cite the two broken guards as evidence. Its claims about domain imports and raw maps come from its own censuses.

## Prior work: did SKILL-351 land as specified?

SKILL-351 (runtime-domain, completed 2026-09-17) is the most recent investigation of this module. Its retention decisions stand:

- keep the `→ runtime-contracts` edge
- no per-area modules
- no `FeatureTaskRuntime*` rename
- no DTO layer
- the documented handoff-projection split

Landing check against its acceptance criteria:

| SKILL-351 criterion | Status at baseline |
| --- | --- |
| AC1 typed decode failures | Landed. Review `fromWire` and decoders use typed errors. |
| AC2 one internal reader, "one exact and one documented-lenient integer coercion" | **Not landed as specified.** 13 coercion functions remain with at least four semantics (F-008). |
| AC3 lane accounting through `JsonCodec` | Landed. |
| AC4 fallbacks recorded, liveness reader deleted | Landed. |
| AC5 validator ports: one home, no default bodies | Landed as a domain home (decision (a), 2026-09-16). Superseded here with new evidence (F-007). |
| AC6 ownership moves, no `engine` → `taskruntime` import | Landed. No `workflow.engine` file imports `workflow.taskruntime`. |
| AC7 typed workflow status | Landed. |
| AC10 dead declarations deleted, visibility narrowed | **Partly landed.** Seven listed names are gone. Four scaffold constants and four repair-receipt limits it listed remain. 99 module-only and 54 same-file public declarations remain (F-013). |
| AC11 no public mutable state | Landed. `summarizeAttemptLedgerFromEntries` is a pure reduction; the remaining `var`s are function locals. |

## Principle assessment

| Principle | Assessment |
| --- | --- |
| Dependency direction | Correct. Main uses `implementation` only toward contracts. Downstream `api(...)` exposure is a pinned decision. |
| Persistence ignorance | Fails. The aggregate is a row with JSON text (F-001, F-002). |
| Aggregate invariants | Fail. Update validation is opt-in (F-003). |
| Acyclic dependencies | Fails at package level: four cycles and 24 model → logic edges (F-004). |
| Encapsulation / single owner | Fails for artifacts: 38 of 39 keys are public and used outside (F-005). Rules are copied across modules (F-006). |
| Dependency inversion | Domain rules take infrastructure validator ports (F-007). |
| Typing | Integer coercion diverges (F-008). Time is `String` (F-009). Skill kind is strings (F-010). |
| YAGNI | Forwarding extensions and a dead `checkpoint` parameter (F-007), aliases (F-011), orphan packages and tests (F-012), dead declarations (F-013), a dead resource (F-014). |
| Ambient effects, concurrency, DI | Clean. No clock, env, threads, coroutines, or DI in domain. |
| Testing | Tests are behavioural and pure, but 43 test files outside domain build row text, and `Noop*` fixtures mask validation (F-001, F-007). |

## Checklist

1. **Dependency direction.** Clean. Main edges point inward to `runtime-contracts` only, via `implementation`. `runtime-ports`, `runtime-application`, and `runtime-engine` re-export domain as `api`. The pinned ABI closure in `ARCHITECTURE.md` requires that, and changing it is not justified.
2. **Inbound side.** CLI imports `FeatureTaskRuntimePhaseWorkflowDefinition` (3 files), `GoalObservabilityEventValidator` (2), `projectionWireMap`/`presentationWireMap` (1 each), and skill-remove types (2). MCP imports `GoalObservabilityEventValidator` (2). CLI, MCP, and SQLite all re-decode domain data (F-002, F-005).
3. **Outbound side.** Domain ports are narrow. `FeatureTaskRuntimeWireArtifactValidator.validate(kind, payload: Any, label)` erases the payload type by design, per SKILL-351 decision (b), and stays. No vendor protocol lives in domain: no SQL, HTTP, or `SQLITE_BUSY`. `SQLITE_TIMESTAMP_FORMATTER` in `AttemptLedgerAccumulator.kt:87` parses a stored legacy format and is part of F-009. The problem is that domain rules call the validators (F-007).
4. **Domain richness.** Domain rules live outside domain: decomposition invariants in application (SKILL-370 F-012), goal-continuation decode in engine and SQLite, and goal-parent projection in engine and SQLite (F-006). Divergent copies were measured by `diff`.
5. **Composition.** Clean inside domain. Other modules build `WorkflowEngine` by hand at 10 sites, which F-007 removes by making it dependency-free.
6. **Entry-point leakage.** Three operator-guidance strings name CLI commands: `GoalRunnerPolicy.kt:282` (`skill-bill goal findings --issue-key <KEY>`), `ReviewOperationPolicy.kt:213`, and one checkpoint message. They are durable guidance shown by both CLI and MCP. They stay; see What stays.
7. **Ambient effects.** Clean. No `System.*`, `Instant.now`, `UUID.randomUUID`, `Random`, or `Thread` in domain main.
8. **State and transactions.** Domain owns no transaction. Mutable state is confined to function locals and one private merger class (`ParallelReviewMerger.ClusterHead`). Transactions are owned by the SQLite session and the callers of `updateRecord`.
9. **Error model.** Typed errors come from `runtime-contracts`. There are 10 `runCatching` sites:
   - 6 are `longValueExact` probes inside coercions (F-008 removes the duplicates).
   - 2 are operator-refusal mappings in `ReviewSpecAdjudicationAdmission`, accepted by SKILL-351.
   - 1 is operator-input date parsing that rethrows.
   - 1, `GoalSubtaskReviewStructuredFindingsParse.kt:123`, filters agent-reported paths but catches every `Throwable`. It should catch only the path-validation exception (F-013).

   Domain has no coroutines, so there is no cancellation to propagate.
10. **Cohesion and ownership.** See the single-consumer census. `scaffold.policy` rules are consumed only by an infra-hosted use case, recorded under Coordination. Everything else consumed by one module is consumed by engine or application, which is correct layering.
11. **YAGNI.**
    - 12 forwarding `validateX` extensions and the dead `checkpoint` parameter (F-007)
    - 15 aliases (F-011)
    - 17 unreferenced declarations and 153 over-visible ones (F-013)
    - the dead resource (F-014)
    - Clean: no test-only `NONE` objects in domain main.
12. **Naming and packages.** Stutter in `workflow.taskruntime.model.persistence.task.runtime.persistence`. 22 single-file packages. `skillbill.domain.skillremove` is the only area under a `domain` segment. 9 test files sit in 8 packages absent from main. Two same-name file pairs remain in domain after the alias files are removed (F-012).
13. **Guard validity.** See the table above. Two guards scan nothing and belong to SKILL-371. The acyclicity guard reads domain files but at a granularity that hides the cycles.

## Findings

Paths are relative to `runtime-kotlin/`; `…/` abbreviates `src/main/kotlin/skillbill/`.

| ID | Priority | Anchor | Summary | Subtask |
| --- | --- | --- | --- | --- |
| F-001 | Major | `runtime-domain/…/workflow/engine/model/WorkflowModels.kt:21` | The workflow aggregate is a persistence row | 1 |
| F-002 | Major | `runtime-domain/…/workflow/engine/model/DurableWorkflowArtifacts.kt:16` | Corrupt `artifactsJson` becomes an empty workflow at 108 call sites | 1 |
| F-003 | Major | `runtime-domain/…/workflow/engine/WorkflowEngine.kt:52` | Aggregate update validation is opt-in; 30 of 31 update paths skip it | 1 |
| F-004 | Major | `runtime-core/src/test/kotlin/skillbill/architecture/ArchitectureScanSupport.kt:677` | Four package cycles hidden by guard granularity | 3 |
| F-005 | Major | `runtime-domain/…/workflow/taskruntime/model/persistence/task/runtime/persistence/FeatureTaskRuntimePersistenceArtifactKeys.kt` | The artifact map is a public untyped bag read around domain decoders | 2 |
| F-006 | Major | `runtime-infra/sqlite/…/infrastructure/sqlite/goalrunner/control/GoalContinuationArtifactCodec.kt:30` | Domain rules copied into engine, application, and SQLite with divergent semantics | 2 |
| F-007 | Minor | `runtime-domain/…/workflow/taskruntime/artifact/FeatureTaskRuntimeWireArtifactValidatorExtensions.kt` | Domain rules take schema-validator ports; forwarding extensions; dead engine parameter | 2 |
| F-008 | Minor | `runtime-domain/…/goalrunner/GoalWorkerSubtaskRequestArtifactCodec.kt:70` | SKILL-351's single integer coercion did not land; 13 functions, 4+ semantics | 2 |
| F-009 | Minor | `runtime-domain/…/workflow/engine/WorkflowEngine.kt:76` | Time as `String` with an empty-string sentinel; consumers re-parse | 1 |
| F-010 | Minor | `runtime-domain/…/scaffold/policy/scaffold/ScaffoldPolicyConstants.kt:8` | Skill kind is string constants restated in infra and CLI | 3 |
| F-011 | Minor | `runtime-domain/…/workflow/goal/GoalProgressEventValidator.kt:3` | Compatibility typealiases around domain types | 3 |
| F-012 | Minor | `runtime-domain/…/workflow/taskruntime/model/persistence/task/runtime/` | Count-driven packages and orphan test packages | 3 |
| F-013 | Minor | `runtime-domain/…/scaffold/model/command/ScaffoldCommandConstants.kt` | 17 dead declarations, 153 over-visible, one broad catch | 3 |
| F-014 | Minor | `runtime-domain/build.gradle.kts:9` | Dead version resource and build block; SQLite duplicates the version read | 3 |

### F-001. Make `WorkflowStateSnapshot` a domain aggregate

**Evidence.** `WorkflowStateSnapshot` holds `stepsJson: String`, `artifactsJson: String`, `mode: String?`, and three `String?` timestamps (`WorkflowModels.kt:21-34`). `WorkflowEngine.updateRecord` (`WorkflowEngine.kt:52-80`) runs these steps on every update:

1. parses both strings (`decodeObject`, `decodeSteps`)
2. merges
3. re-encodes (`jsonString`)
4. calls `WorkflowSnapshotValidator.validate` on the row

`runtime-ports` declares a second row, `WorkflowStateRecord`, with `mode: FeatureTaskWorkflowMode?`, a stronger type than the domain side. `WorkflowRecordMapping.kt` copies fields between the two. 43 main files in six modules reference `artifactsJson`. Only 2 main sites construct the snapshot.

**Fix.**

- The snapshot carries `steps: List<WorkflowStepState>`, `artifacts: DurableWorkflowArtifacts`, `mode: FeatureTaskWorkflowMode?`, and `Instant?` timestamps (F-009).
- `WorkflowStateRecord.toSnapshot()` in `runtime-ports` decodes strictly, and `toRecord()` encodes.
- Delete the lenient public step decoder `decodeWorkflowSteps` (`AttemptLedgerWorkflowDecoding.kt:25`, imported by SQLite).

**Feasibility.**

- The strict decoders (`decodeSteps`, `decodeObject`, `parseDurableJson` in `WorkflowEngineSnapshotCodecDurable.kt`) are `internal` to domain, and the goal is no workflow JSON parsing in domain. So they move, not just widen: into `runtime-ports/…/workflow/model/WorkflowRecordMapping.kt`, beside the existing `toSnapshot()` extension over the port row. That follows the 2026-09-06 precedent for extensions over port types. `runtime-ports` already depends on domain and on `JsonCodec` via contracts.
- `WorkflowStepState` is lossless: the canonical `step` schema (`orchestration/contracts/workflow-state-schema.yaml:70-84`) has `additionalProperties: false` with exactly `step_id`, `status`, and `attempt_count`, and `workflowStep` writes them in a fixed order.
- Artifacts keep insertion order through `LinkedHashMap`, and `JsonCodec` preserves exact numbers, so `toRecord()` re-encodes today's bytes.
- Snapshot schema validation moves to the write seam (F-007).
- `WorkflowStateRecord` stays the persistence row.

### F-002. One strict decode for workflow JSON

**Evidence.** `decodeObject` (`WorkflowEngineSnapshotCodecDurable.kt:20`) raises `InvalidWorkflowStateSchemaError`. Four other decoders return an empty map on corrupt text:

- `DurableWorkflowArtifacts.fromJson` (`DurableWorkflowArtifacts.kt:16-22`, `parseObjectOrNull(...).orEmpty()`), reached from 82 main sites through `decodeWorkflowArtifacts` (application), `FeatureTaskRuntimeWorkflowPersistence.artifactsFrom`/`artifactsFromJson` (engine), and direct calls
- SQLite `decodeArtifacts` (`DecompositionManifestAccess.kt:78`, 26 sites)
- `sparseArtifactKeys` (`WorkflowGoalRunnerProgressRecording.kt:319`)

Resume, status, and goal progress then treat a corrupt row as a fresh workflow. `GoalParentProjectionWriter.rewrite` with `replaceArtifacts = true` can persist that empty view.

**Fix.** F-001 removes most sites. Delete the four lenient decoders and their wrappers. If the progress-poll projection is kept for cost, it raises the typed error on malformed text.

### F-003. Enforce update rules inside the aggregate

**Evidence.** `validateWorkflowUpdate` (`WorkflowEngineValidation.kt:14-26`) checks that the status is allowed by the definition, that the step id is declared, and that step updates are unique and well formed. It runs only through `WorkflowEngine.validateUpdate` (`WorkflowEngine.kt:244`), whose only caller is `WorkflowService.kt:151`.

`updateRecord` does not call it. The other update paths build `WorkflowUpdateInput` by hand:

- infra/sqlite: 16
- engine: 9
- application decomposition: 6

**Fix.** Run `validateWorkflowOpen`/`validateWorkflowUpdate` inside `openRecord`/`updateRecord`, raise the typed workflow-state error, and make `validateUpdate` non-public. Keep today's rules exactly. Do not add transition rules or named transition methods (see What stays).

### F-004. Break package cycles and make the existing guard see them

**Evidence.** A Tarjan pass over exact domain packages finds four strongly connected components:

1. **20 packages across `workflow.goal`, `workflow.goal.model`, and 18 `workflow.taskruntime.*` packages.** `goal` → `taskruntime` has 21 import edges and `taskruntime` → `goal` has 16.
   - Goal imports taskruntime's `DurableArtifactMapReader`, `toStringKeyedArtifactMap`, the wire-validator port, `FeatureTaskRuntimeVerdict`, the review severity and pass-sequence types, and the repair-ledger types.
   - Taskruntime imports goal's `ValidationDepth`, `GoalSubtaskReviewState`, `GoalSubtaskReviewCompactFinding`, `GoalSubtaskReviewPassResult`, `appendBoundedHistoryBySequence`, and `GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY`.
2. **9 packages across `review.attribution`, `review.model`, and the `review.context.model.*` subpackages.**
3. **`review.finding` ↔ `review.parsing`**, 6 and 4 edges. One parser is split into regexes and functions.
4. **`experiment` ↔ `experiment.model`**, 1 and 2 edges.

There are 24 model → logic edges, for example `scaffold.model.command` → `scaffold.policy.scaffold` (12 edges, all skill-kind constants).

The guard misses all of this: `packageImportEdges` (`ArchitectureScanSupport.kt:668-686`) cuts every package to its first segment, and `mutualImportCyclesForEdges` (`:716-726`) finds only pairs.

**Fix.** Move the shared vocabulary to the lower side:

- the durable artifact reader and the goal-review state types to `workflow.model`, or whichever sub-area both import
- skill kind to `scaffold.model` (F-010)
- merge `review.finding` into `review.parsing`
- resolve the review and experiment edges the same way

`model` packages import no logic packages. Add a granularity option to the existing scanner (`ArchitectureScanSupport.packageImportEdges` and `packageCycles`). The current first-segment, mutual-pair mode stays the default. runtime-domain opts into exact packages with strongly-connected-component detection, against its existing empty baseline. The option is chosen per scan case. A global switch would change SKILL-376's infra baselines, which rely on first-segment semantics. SKILL-374 subtask 1 runs before 372.3 and fixes its error-package cycle with a second default-granularity scan (prefix `skillbill.error.`). It does not depend on the exact mode, so the default must stay unchanged for the runtime-contracts case. SKILL-373 does not restructure the acyclicity test (it collapses only the ambient-clock, ambient-environment, and inject-default rules), so the option goes into the current class. The `experiment` cycle disappears with SKILL-378 subtask 1; if that has landed, the criterion is met for it.

**Feasibility.** Every moved type stays inside runtime-domain, so the import rules are unchanged.

### F-005. One domain owner per durable artifact

**Evidence.** 38 of 39 domain `*_ARTIFACT_KEY` constants are referenced from other modules' main code, and those modules declare 8 more keys of their own. About 60 raw `artifacts[KEY] as? Map<*, *>`/`as? List<*>` reads sit outside domain: engine about 35, application about 16, SQLite 10, MCP 2 (`WorkflowGoalObservabilityMcpMapping.kt:20-23`). Several use literal keys:

- `"goal_continuation"`, `"commit_push_result"`, `"goal_continuation_outcome"` in `DecompositionManifestRuntimeStateDerivation.kt:34-218`
- `"goal_continuation"` in the SQLite `GoalContinuationArtifactCodec.kt:31`
- `"progress_event"` in `WorkflowGoalRunnerProgressRecording.kt:58`

The goal-continuation artifact has a strict domain decoder (`FeatureTaskRuntimeGoalContinuationArtifact.fromArtifactMap`). Engine (`GoalContinuationArtifactCodec.kt:27`) and SQLite each re-implement a lenient `goalContinuation(artifacts)` that returns `null` on a missing field, and four more sites read the raw map. Engine also keeps `FeatureTaskRuntimePhaseArtifactDecoders.kt`, which has six raw reads.

The phase-record and phase-ledger decode alone exists in five variants:

- domain `phaseRecordsFromWorkflowArtifacts`/`phaseLedgerFromWorkflowArtifacts` (`artifact/FeatureTaskRuntimeWorkflowArtifactWire.kt:61,77`)
- domain `phaseRecordsFrom`/`phaseLedgerFrom` (`phaseartifacts/`)
- a private copy in `FeatureTaskRuntimeRequiredArtifactPresenceResolver.kt:97`
- application `decodeFeatureTaskRuntimePhaseRecords` and `FeatureTaskRuntimePhaseLedgerDecoder`
- engine `decodePhaseRecords`/`decodePhaseLedger`

SQLite `decodePhaseRecords`/`decodePhaseLedger`/`encodeWorkflowArtifact` (`FeatureTaskRuntimeSqliteArtifactWire.kt`) forward to domain.

**Fix.** Add typed extension reads, and writes where the artifact is written, on `DurableWorkflowArtifacts` in each family's owning domain package. Implement them through the existing decoders and encoders. Accessors return typed values, so SKILL-371's restored raw-map guard stays at zero violations. Make domain `*_ARTIFACT_KEY` constants `internal`. Delete the lenient re-implementations and engine's decoder file. Keys that engine alone owns stay with engine.

Domain owner packages, named here for SKILL-376 subtask 2's fallback:
- phase records, phase ledger, and workflow-artifact encoding: `skillbill.workflow.taskruntime.artifact`
- goal continuation: the package of `FeatureTaskRuntimeGoalContinuationArtifact`
- decomposition runtime: `skillbill.workflow.decomposition.runtime`

The SQLite `resolveDecompositionManifest` is not a pure codec (`java.nio.file.Path` plus three port types) and cannot move to domain. It duplicates application's `resolveDecompositionManifest` (`DecompositionManifestRuntimeState.kt:88`) and collapses into that once it sits in engine.

At the SKILL-378 F-010 site (`FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint.kt:84-89`), the operator-block-retry payload is written through its domain owner rather than new key constants. The MCP reads move into application under SKILL-370 F-002, and the migration applies wherever they land.

**Feasibility.** The accessors live in domain and use only domain types and contracts keys.

### F-006. Move copied rules below their consumers

**Evidence.** Same-named files with divergent bodies, measured by `diff` excluding package and import lines:

| File | Modules | Differing lines |
| --- | --- | ---: |
| `GoalContinuationArtifactCodec.kt` | engine, infra/sqlite | 97 |
| `DecompositionManifestProjectionFailurePersistence.kt` | application, infra/sqlite | 70 |
| `DecompositionWorkflowRuntimeLookup.kt` | application, infra/sqlite | 33 |
| `GoalParentProjectionWriter.kt` | engine, infra/sqlite | 19 |
| `DecompositionWorkflowRuntimeLookupParentDiscovery.kt` | application, infra/sqlite | 10 |
| review-policy and out-of-band decode | application `LegacyGoalRunnerControlMigration.kt:39,65`, SQLite `GoalContinuationArtifactCodec.kt:117,149` | — |

The 2026-09-06 decision accepted the application/SQLite pairs until "a third module needs one." Engine now has two of them, so that trigger has fired.

**Feasibility.** I checked the imports of each copy:

- **Pure rules that compile in domain:**
  - goal-continuation decode, targeting domain `goalrunner.model.GoalContinuation`
  - goal-parent artifact projection, taking an already-encoded manifest map once F-007 moves validation out
  - the decomposed-parent predicate over a snapshot
  - decomposition-runtime decode from snapshot artifacts
  - the projection-failure artifact entry shape
- **The review-policy and out-of-band decode cannot move.** `GoalRunnerReviewPolicy` and `GoalRunnerOutOfBandAcceptance` are `runtime-ports` types that depend on other port models (`GoalPlanningIdentity`, `GoalSubtaskReviewBaseline`, `java.nio.file.Path`).
- **`GoalRepositoryIdentity` cannot move** (`java.nio.file.Path`, `RepositoryEnclosingRootPort`). SKILL-371 owns repository identity.
- **The class shells** take `WorkflowStateRepository` or `UnitOfWork` receivers and stay outside domain.

**Fix.** Move the pure rules into domain. After SKILL-376 subtask 2, reconcile the 14 diverged twins it carries into engine as private functions: `clearDecompositionManifestProjectionFailure`, `decompositionRuntime`, `findDecomposedParentOrCorruptFallback`, `findMatchingDecompositionManifests`, `goalContinuation`, `goalRepositoryIdentity`, `goalReviewArtifacts`, `goalReviewEmissionEnvelope`, `hasDecompositionPlan`, `isGoalContinuationChildWorkflow`, `loadDecompositionManifest`, `missingResultPrefixTerminalOutcomeArtifact`, `persistDecompositionManifestProjectionFailure`, `validatedGoalReviewPasses`. Each ends with one definition. Pure rules go to domain, repository-driving code to engine or application, and each semantic difference is kept or removed deliberately with a test. The two legacy-artifact decoders stay in SQLite with their ledger migration; that duplication is accepted until the migration is retired. Collapse the shells after SKILL-376 subtask 2 moves the SQLite goal-runner files into engine. At that point each duplicate pair sits in engine, or in engine and application, and engine may call application directly. That removes the review-policy duplicate without moving port types. `normalizedBlockedReason` becomes a single domain copy through SKILL-370 F-012.

### F-007. Validate wire payloads at the adapters; delete forwarding layers

**Evidence.** 27 domain call sites in 10 files take a validator port:

- `FeatureTaskRuntimeWireArtifactValidatorExtensions.kt`: 12 one-line forwarders, `validateX(p, l) = validate(KIND, p, l)`
- `GoalObservabilityParsing.kt` 3, `GoalObservabilityArtifacts.kt` 3
- `FeatureTaskRuntimeWorkflowArtifactWirePhaseMappings.kt` 2, `DecompositionManifestValidatorWireExtensions.kt` 2
- the handoff projection inputs and declaration 2
- `WorkflowEngine`, `InstallPlanPolicy`, `InstallPlanWireMap`

Domain tests inject three `Noop*Validator` fixtures to skip the schema check. `WorkflowEngine`'s `checkpoint: CheckpointResolver = unresolvedCheckpoint` parameter (`WorkflowEngine.kt:25`) is never passed by any caller in any source set, so `resolvedRepositoryCheckpointIdentity` always defaults to `""`. Every production construction passes only the validator.

**Fix.**

- Remove validator parameters from domain functions. Callers validate at the adapter or application seam before decoding, or after encoding.
- Delete the 12 forwarders; callers call `validate(kind, …)`.
- Move the validator interfaces and `FeatureTaskRuntimeWireArtifactKind` to `runtime-ports`. The composing extensions `decodeManifest`/`encodeManifestWireMap` move into the port's declaring file, following the 2026-09-06 precedent that extensions over a port type live beside it. Their imports are domain models only, and ports depend on domain.
- Delete the `Noop*` fixtures and the three goal validator aliases.
- Remove the `checkpoint` parameter. With no collaborators left, `WorkflowEngine` needs no construction at the 10 sites.
- Rename the infra implementation class that shares the port's name.

- Type the two `Any` members before they reach ports: `FeatureTaskRuntimeWireArtifactValidator.validate(kind, payload: Any, …)` and `FeatureTaskRuntimePhaseOutputValidator.validateAndReadPhaseOutput(): Any` (3 callers). Use the family's typed wire carrier, or delete the member where `normalizePhaseOutput` already serves the caller. Do not use a raw `Map<String, Any?>`, which the raw-map guard forbids in ports.
- Remove the `= Unit` default on `ReviewContextEnvelopeValidator.validateSpecIntentProjection` (`ReviewContextEnvelopeValidator.kt:9`). An implementation that forgets to override it validates nothing, the same defect class SKILL-351 removed from the handoff validator.

After these changes, ports satisfy SKILL-377's AC3 (no constant-result defaults) and AC9 (no `Any`-typed port members), whichever of the two bundles lands first.

This supersedes decision (a) of the 2026-09-16 runtime-domain entry with new evidence: domain no longer consumes the ports.

### F-008. Finish SKILL-351's coercion unification

**Evidence.** Integer coercion functions and their semantics:

- **Truncating `toInt()`, also accepting strings:** `asGoalRunnerIntOrNull` (public, `GoalWorkerSubtaskRequestArtifactCodec.kt:70`, 9 main consumer files including SQLite and engine), `asGoalObservabilityIntOrNull` (`GoalObservabilityArtifacts.kt:121`), `asLenientIntOrNull` (`AttemptLedgerWorkflowDecoding.kt:52`, the documented exception)
- **Whole-number `Double` check:** `asIntOrNull` (`FeatureTaskRuntimePlanOutcomeDecoders.kt:98`)
- **Exact `Long`, no `BigDecimal`:** three `asIntegerOrNull` in `FeatureTaskRuntimeReadinessEvidence.kt:252`, `FeatureTaskRuntimeValidationEvidence.kt:128`, and `FeatureTaskRuntimeValidationGateExecutionEvidence.kt:192`
- **Exact `BigDecimal`:** `DurableArtifactMapReader.kt:145`, plus identical copies in `WorkflowEngineNumericCoercion.kt:8` and `ReviewRunLaneSegmentAccountingJson.kt:106`

A value like `2.7` decodes to `2` through `asGoalRunnerIntOrNull` and fails through `DurableArtifactMapReader`.

**Fix.** Keep `DurableArtifactMapReader`'s exact coercion and the one documented lenient coercion. Migrate the rest and delete them. Values that decode today keep decoding to the same result, except that non-integral numbers on the truncating paths become typed failures. That behaviour change is intended and needs a test.

### F-009. Type time where it is reasoned about

**Evidence.** About 50 domain timestamp fields are `String`/`String?` and 3 are `Instant`. `recordedAt` has both types across models. `updateRecord` writes `finishedAt = ""` as a sentinel (`WorkflowEngine.kt:76`).

Domain parses its own strings: `GoalRunnerExecutionLease` parses in its initializer, and `AttemptLedgerAccumulator.parseInstantOrNull` accepts ISO and SQLite formats. Consumers parse again in at least 10 places, some with `runCatching … getOrNull()`: `GoalPlanningLogService.kt:138`, `SQLiteWorkListRepository.kt:145`, `GoalRunnerTelemetryEmitter.kt:170`, and `FeatureTaskRuntimeWorkflowPersistence.kt:233`.

**Fix.**

- The aggregate timestamps become `Instant?`. The sentinel becomes an explicit `Instant` that the caller supplies; domain reads no clock.
- Every other timestamp that domain or a consumer compares, orders, or does arithmetic on becomes `Instant`: the execution lease, the attempt ledger, phase records used for durations, and goal progress event time.
- Codecs parse and format the formats stored today.
- A carried-only display timestamp may stay `String`, with the reason noted where it is declared.

### F-010. Skill kind as an enum

**Evidence.** `SKILL_KIND_*` string constants and `SUPPORTED_SKILL_KINDS: Set<String>` (`ScaffoldPolicyConstants.kt:8-15`) model a closed set. Literals are restated in infra/skills (`ScaffoldServicePlanning*.kt`, `ScaffoldTemplateRendering.kt:51`, `FileSystemScaffoldGateway.kt:249-259`, `File*AddonSourceConfig*.kt`) and in CLI (`ScaffoldWizardValueNormalization.kt:7-10`). `docs/code-principles.md` requires enums for closed sets.

**Fix.** Add `enum class SkillKind(val wireValue: String)` in `scaffold.model` with one `fromWire`. CLI alias normalisation returns the enum. The wire bytes are the same strings, so no codec output changes. `APPROVED_CODE_REVIEW_AREAS` follows the same rule while `AGENTS.md` keeps that list closed.

### F-011. Delete compatibility typealiases around domain types

**Evidence.** Domain has 10 aliases:

- the three goal validator names (removed under F-007)
- `FeatureTaskRuntimeWireArtifactKind`, `RejectedVerificationFindingsResult`, `DurableDecodeSubstitutionRecord`, and `DurableDecodeSubstitutionObservations`, each in a same-name sibling file of its `model` type
- `ReviewContextWireMap`, `InstallAgent`, `AgentSymlinkProvider`

Engine aliases domain `GoalContinuation` (`GoalRunnerPersistenceModelAliases.kt:22`). Application's four domain-type aliases are deleted by SKILL-370 F-008.

**Fix.** Use the real types and delete the alias declarations and alias-only files. The engine `GoalContinuation` alias stays with 372.3, as SKILL-378's owner confirmed; 378.3 removes the other 37 engine aliases.

### F-012. Flatten count-driven packages; align test packages

**Evidence.**

- `workflow.taskruntime.model.persistence.task.runtime.{checkpoint,goal,implementation,persistence,prior,run}` plus `…persistence.artifact` spread 11 files over seven packages.
- `…model.handoff.envelope` holds 1 file and `…model.repair.task` holds 7.
- `skillbill.domain.skillremove` is the only area nested under `domain`.
- 9 test files sit in 8 packages absent from main.
- `review/attribution/ReviewIssueCategory.kt` and `learnings/LearningEntry.kt` share file names with types they do not declare.

The nesting came from the SKILL-361 sweep under the sibling-count guard.

**Fix.** Merge into `…model.persistence` (11 files), `…model.handoff` (4), and `…model.repair` (11). Move to `skillbill.skillremove`. Move orphan tests to their subject's package. Rename the two files by content. The guard thresholds stay.

### F-013. Delete dead code, narrow visibility, narrow one catch

**Evidence.** 17 unreferenced public declarations:

- four `REPAIR_RECEIPT_MAX_*` limits and four scaffold command-kind constants, both listed by SKILL-351 and still present
- four review budget and digest constants in `ReviewContextWireLimits.kt`
- `phaseOutputEnvelopeFromArtifact`, `isDecompositionPackagePhaseOutput`, `validateSharedEvidenceProjection`, `acceptanceCriterionOrdinal`
- domain `normalizedBlockedReason`

Also, 54 public declarations are used only in their file and 99 only in the module. `GoalSubtaskReviewStructuredFindingsParse.kt:123` catches every `Throwable` to filter paths.

**Fix.** Delete the unreferenced declarations, except `normalizedBlockedReason`, which SKILL-370 makes the single referenced copy. Make same-file-only declarations `private` and module-only ones `internal`. Move test-only external declarations into `testFixtures`, or note why they stay public. Narrow the catch to the path-validation exception. Re-run the census at implementation time; compilation plus the full suite is the deletion authority.

### F-014. Remove the dead version resource and the duplicate read

**Evidence.** `runtime-domain/src/main/resources/skillbill/version.properties` and the `processResources` block in `runtime-domain/build.gradle.kts:9-15` survive the SKILL-351 move of `SkillBillVersion` to `runtime-core`, and nothing in domain reads them. `runtime-infra/sqlite` holds a third copy and its own reader, `SkillBillRuntimeVersion.kt:9`. That reader is the default of `TelemetryOutboxStore(version = …)` and is used from `SQLiteRepositories.kt:86`, `ReviewTelemetryState.kt:118`, and `LifecycleTelemetryEmit.kt:104`.

**Fix.** Delete the domain resource and build block. Pass the version into `SQLiteDatabaseSessionFactory`, and through it to the outbox store, using SKILL-373's typed version value in runtime-ports if it has landed. Delete the SQLite resource and reader.

**Feasibility.** Kotlin-inject resolution is not involved. `RuntimeBootstrapBindings.kt:65` constructs `SQLiteDatabaseSessionFactory(context, clock, diagnostics)` by hand and can pass `SkillBillVersion.VALUE`, which `RuntimeTelemetryProvides.kt:32` already exposes.

## Over-engineering register

| Item | Action | Net production lines |
| --- | --- | ---: |
| Four lenient artifact decoders and their three wrappers, plus per-site JSON parsing (F-001, F-002) | delete | about −150 |
| Lenient goal-continuation copies, engine phase-artifact decoders, raw reads (F-005) | delete; route through domain accessors | about −250 |
| Copied rule bodies across engine, application, and SQLite (F-006) | delete; one domain rule | about −200 |
| 12 forwarding validator extensions, validator parameters on 27 sites, 3 `Noop*` fixtures, dead `checkpoint` parameter, 10 hand-constructions (F-007) | delete | about −150 |
| 11 redundant integer coercions (F-008) | delete | about −60 |
| Timestamp re-parsing at consumers (F-009), restated skill-kind literals (F-010) | delete | about −60 |
| 11 aliases in domain and engine and their alias-only files (F-011) | delete | about −30 |
| 17 dead declarations (F-013), dead resource, build block, and SQLite reader (F-014) | delete | about −85 |
| Exact-package SCC scan in the existing cycle scanner (F-004) | change existing guard | about +30 test lines |

Net: about −950 production lines. No module, dependency, framework, bag, or architecture-test class is added.

## What stays unchanged

| Item | Reason |
| --- | --- |
| The six validator ports and `RequiredArtifactPresenceResolver` | Each has an adapter and test substitutes (1 to 18) or two strategies. F-007 moves where validators are called, not whether they exist. |
| `FeatureTaskRuntimeWireArtifactValidator` as one kind-keyed port | SKILL-351 decision (b) collapsed seven identical ports into it; still sound. Its payload gets a type when it moves (F-007). |
| `runtime-domain` → `runtime-contracts` edge; errors in `skillbill.error.shellcontent` | Shared kernel for keys, versions, `JsonCodec`, and typed errors. SKILL-374 keeps the package name. |
| `WorkflowContinueSessionSummary` in a domain view | SKILL-374 classifies it as a shared contract that stays in contracts. Changing it here would conflict for no gain. |
| `api(...)` re-export of domain by ports, application, engine | Pinned ABI closure in `ARCHITECTURE.md`. |
| `WorkflowStateRecord` in `runtime-ports` | It is the persistence row. Only the domain snapshot changes. |
| Operator guidance text naming CLI commands in domain policy | Durable guidance shown by CLI and MCP alike. Moving it would change stored reasons for no behavioural gain. |
| `toArtifactMap`/`fromArtifactMap` codecs in domain | They are the typed layer. SKILL-351 rejected a DTO layer, and the accessors reuse these codecs. |
| Handoff-projection file split, `WorkflowEngine*` split | Recorded by responsibility on 2026-09-17. |
| Named transition methods on the aggregate | F-003 makes the existing invariants unavoidable. Methods become worth it when a transition rule exists. |
| Inbound use-case interfaces for domain services | No substitute exists or is needed. |
| Enum-typing workflow step ids or artifact payload internals | They ripple through wire codecs and pack-declared vocabularies. |
| `explicitApi()` | Adds `public` to 891 declarations in a non-published module. F-013 narrows visibility instead. |
| Splitting files by size; per-area domain modules; `FeatureTaskRuntime*` rename | SKILL-351 rejections still hold. |
| Domain events, event sourcing, identifier value classes | No consumer, and `ARCHITECTURE.md` rejects blanket identifier wrappers. |
| New architecture scanners for artifact keys or aliases | `internal` visibility and compilation enforce them. |
| Sibling-count thresholds | The guard stays; only the fragments it produced are merged. |

## Coordination with concurrent bundles

On 2026-09-22 I re-read all eight uncommitted sibling bundles (SKILL-370, 371, 373, 374, 375, 376, 377, 378) at HEAD `85209c086`, which changed no runtime-domain file since baseline. None was edited. Overlap was checked by intersecting the files and symbols each bundle names, then reading each shared item in context.

| Bundle | Overlap | Owner and resolution | Sequencing |
| --- | --- | --- | --- |
| SKILL-370 runtime-application | Moves `withParentStatus`, `intentFor`, and the blocked and retry transitions into domain `workflow.decomposition`. Deletes application `normalizedBlockedReason` and all application typealiases, including 4 domain-type aliases. F-002 moves goal-observability decoding out of CLI and MCP into application. | 370 owns those. 372 keeps domain `normalizedBlockedReason` as the single copy and applies artifact accessors wherever the reads land after 370, including literal-key reads that move into domain with F-012. | 370 before 372.1. |
| SKILL-371 runtime-cli | Restores the raw-map and `RuntimeArchitectureTest` scan roots. Fixes `ReviewAccountingSummary.toBoundedPayload` in domain. Owns repository identity (`GoalRepositoryIdentity`). | 371. 372's accessors return typed values, so the restored raw-map guard stays at zero. | 371.1 before 372.1. |
| SKILL-373 runtime-core | Collapses the ambient-clock, ambient-environment, and inject-default rules (not the acyclicity test), moves the suite to runtime-core `src/repoTest` in 373.3, adds a single-field version value type in runtime-ports `skillbill.model`, and updates the `WorkflowSnapshotValidator` provider import. | 373 owns the suite location and version type. 372 adds the cycle-scan option in the current acyclicity class (F-004). F-014 takes the version type if 373.1 has landed; otherwise it takes `String` and 373 converts it (confirmed by 373's owner). | Either order; 373.3 moves whatever 372.3 added. |
| SKILL-374 runtime-contracts | Adds a `skillbill.error.` prefix scan that relies on first-segment semantics. Narrows `parseObjectOrNull`'s catch. Moves `InstallPlanContract`, `InstallPlanPayloadKeys`, `ReadinessEvidencePayloadKeys`, and a pure discovery-exclusions value (`skillbill.goalrunner.planning`) into domain. Its contracts cycle scan uses the default granularity and does not depend on 372.3. Keeps `DecompositionPlanningResult`, `WorkflowContinueSessionSummary`, `DurableArtifactMapReader`, and `skillbill.error.shellcontent` where they are. | 374. 372's scanner change is an opt-in mode, so 374's scan is unaffected. 372 deletes three lenient `parseObjectOrNull` callers (F-002). An earlier version of this bundle wrongly said 374 moves `DecompositionPlanningResult`. | Either order. |
| SKILL-375 runtime-mcp | MCP goal-observability raw reads. | 370 and 372. | 375 after 372.2. |
| SKILL-376 runtime-infra | 376.2 moves `sqlite.goalrunner` into engine. Its codec fallback moves SQLite codecs to domain owners named here. 376.3 changes infra cycle-scan prefixes. | 376 owns the move. Its twin census found 36 SQLite functions sharing a name with an engine or application function: 20 identical, which 376.2 deletes in favour of the existing copy, and 16 diverged. 376.2 carries 14 of the diverged ones as private functions of the moved engine files, listed in its commit body, and 372.2 reconciles them. The other two, `reviewPolicyFromLegacyArtifacts` and `outOfBandAcceptancesFromLegacyArtifacts`, stay in SQLite beside their only caller, a ledger migration. `resolveDecompositionManifest` calls application's copy. 376.3 moves `ReviewTelemetryState.kt` and `LifecycleTelemetryEmit.kt`, which 372.3's version injection also touches; whichever lands second rebases. | 372.1 → 376.2 → 372.2. |
| SKILL-377 runtime-ports | Rewrites `WorkflowRecordMapping.kt` (family-keyed repository, deletes the `toContract` mappers) and keeps `toSnapshot`/`toRecord` as 372.1 leaves them. AC3 bans constant-result defaults and AC9 bans `Any` members in ports. Touches `GoalPlanningLogService` and `SQLiteRepositories`. | 377. 372.2 types the validator `Any` members and removes the `= Unit` default before they move into ports (F-007). 377's note that 372.1 rewrites the continue-session summary is stale and harmless. | 372.1 before 377.1. |
| SKILL-378 runtime-engine | 378.1 deletes experiment support, including domain `skillbill.experiment/**` and `experimentsAvailability`. AC9 removes all engine typealiases. F-010 inline keys sit beside a domain artifact key. 378.2 and 378.3 restructure the run loop and goal runner. | 378. 372's `experiment` cycle and census rows go away with 378.1. 372.3 removes the engine `GoalContinuation` alias; 378.3 removes the other 37. At the F-010 site, prefer encoding through the domain owner. | 378.1 first, before SKILL-370. 378.2 after 372.1 and 372.2; 378.3 after 372.3 and 376.2 (confirmed by 378's owner). |

Verified global order, acyclic across every stated constraint. SKILL-374.2 runs after SKILL-376.3, per SKILL-374's owner via SKILL-376.

1. SKILL-374.1, SKILL-376.1, SKILL-378.1
2. SKILL-370
3. SKILL-371.1, 371.2, 371.3
4. SKILL-372.1
5. SKILL-373.1
6. SKILL-376.2
7. SKILL-372.2, SKILL-372.3
8. SKILL-375
9. SKILL-376.3
10. SKILL-374.2
11. SKILL-373.2
12. SKILL-377.1, 377.2, 377.3
13. SKILL-378.2, 378.3
14. SKILL-373.3

**Unowned follow-up.** The scaffold use case (`ScaffoldService` with planning, execution, rollback, and install) lives in `runtime-infra/skills` behind a coarse `ScaffoldGateway`, and application has no scaffold service. No bundle claims it.

## Limits

- Counts come from text censuses and are not compiler-verified. Raw artifact reads were matched on `artifacts[...ARTIFACT_KEY]` and `artifacts["literal"]`, so reads through other names are undercounted. "About" marks approximate counts.
- Only compiling can confirm these:
  - that every relocated rule in F-006 compiles in domain without `java.nio` or `skillbill.ports`
  - that `toSnapshot()` can decode strictly without breaking readers that relied on the empty-map fallback
  - that the timestamp codecs reproduce every stored format byte-for-byte
  - that the moved validator interfaces leave no domain import behind
- No full repository gate, installed-runtime launch, or review-driver pass ran. No production source changed.
- Public engineering comparisons, not certification:
  - Microsoft's persistence-ignorance principle: [architectural principles](https://learn.microsoft.com/en-us/dotnet/architecture/modern-web-apps-azure/architectural-principles#persistence-ignorance)
  - Reddit Engineering's model and transport separation, cited by SKILL-351 and not re-read here: [link](https://www.reddit.com/r/RedditEng/comments/xivl8d)
  - Meta's Project LightSpeed consolidation: [link](https://engineering.fb.com/2020/03/02/data-infrastructure/messenger/)
