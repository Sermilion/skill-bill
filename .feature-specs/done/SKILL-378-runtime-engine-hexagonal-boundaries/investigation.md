# Runtime-engine hexagonal boundary investigation

## Execution rule

This bundle runs on the current tree. It does not wait for a subtask of another issue. Ordering notes later in this file are overlap context. If a change this bundle's acceptance criteria need is missing, make it here. If it is already present, keep it.

## Judgment

`runtime-engine` has the right position in the graph and the wrong internal
shape. Its edges point inward. It reaches SQLite, git, processes, and the
filesystem only through ports, with one exception: experiment code, which this
bundle deletes. No CLI or MCP type appears in it. Inside, the run loop and goal
runner are written as stateless objects and extension functions that pass the
same collaborators through hundreds of signatures and 134 parameter bags. They
reach collaborators through public getters. Nine services build their own
dependencies by hand. On top of that sit a speculative experiment framework
with no experiment, a vendor error string used as control flow, four in-memory
sequence counters for one durable stream, and guards that do not cover the
engine or scan nothing. Two SKILL-352 criteria did not land as specified.

The fix removes structure rather than adding it: delete the experiment
framework, turn objects plus bags into ordinary classes with constructor
dependencies, delete aliases and getters, and point existing guards at the
engine. No new module, interface hierarchy, framework, or architecture-test class.

## Method and baseline

- HEAD `dbf9f4830` on `feat/SKILL-368-build-logic-architecture-cleanup`, 2026-09-22 (sibling cross-read at `85209c086`).
  The working tree carried concurrent sessions' uncommitted edits: `detekt.yml`,
  two engine prompt/probe files, build-logic files, and a re-recorded
  `runtime-engine-ambient-clock-baseline.txt`.
- Read first: `../../../CLAUDE.md`, `runtime-kotlin/ARCHITECTURE.md` (Design Principles,
  Gradle Modules, Package Ownership, Boundary Rules, Guardrails),
  `docs/code-principles.md`, `runtime-engine/agent/history.md`,
  `featuretask/agent/decisions.md` and `history.md`, `goalrunner/agent/history.md`,
  and runtime-kotlin `agent/decisions.md` entries from 2026-09-04 to 2026-09-17.
- Prior work: SKILL-247 (run-loop helper inputs) and SKILL-352 (engine boundaries,
  complete 2026-09-17). The SKILL-352 check is below.
- Censuses: grep and Python over `runtime-kotlin/*/src/{main,test,testFixtures}`,
  excluding `build/`. Symbol-level consumers are counted from `import skillbill.engine.*`
  statements. Every file a finding cites was read in full at the cited lines. No
  delegated review ran, and no tests or quality gates ran.

## Census

### Size

| Source set | Files | Lines |
| --- | ---: | ---: |
| main | 335 | 51,073 |
| test | 156 (139 with tests; 1,045 `@Test`) | 43,908 |
| testFixtures | — | 1,062 |

| Area (main) | Files | Lines | Top-level funs | `@Inject` classes |
| --- | ---: | ---: | ---: | ---: |
| `featuretask/runloop` | 33 | 14,532 | 37 | 1 |
| `featuretask/lifecycle` | 44 | 4,897 | 144 | 7 |
| `featuretask/phase` | 37 | 4,282 | 89 | 8 |
| `featuretask/review` | 18 | 2,331 | 40 | 1 |
| `goalrunner/execution` | 23 | 3,657 | 56 | 10 |
| `goalrunner/planning` | 38 | 4,416 | 107 | 9 |
| `goalrunner/experiment` + `engine/experiment` | 17 | 2,981 | 8 | 5 |

`featuretask/runloop` declares 23 top-level `object`s.

### Gradle edges (`runtime-engine/build.gradle.kts`)

| Configuration | Edges |
| --- | --- |
| `api` | runtime-contracts, runtime-domain, runtime-ports, runtime-application |
| `implementation` | kotlin-inject runtime, kotlinx-serialization-json (no engine source imports `kotlinx`) |
| `testFixturesImplementation` | domain, ports, infra-sqlite, testFixtures(application, ports, domain) |
| `testImplementation` | testFixtures(ports, domain, application), infra host/contracts/skills/workflow/sqlite, testFixtures(infra-sqlite), junit, kotlin-test, jackson-yaml |

Consumers: runtime-core `api`, runtime-cli and runtime-mcp `implementation`,
runtime-application `testImplementation` (SKILL-370 removes it). No engine test
imports `skillbill.di`, `skillbill.cli`, or `skillbill.mcp`. 26 test imports reach
infra adapters, which the test configuration declares. Every test package exists
in main.

### Symbol-level consumers

| Measure | Count |
| --- | ---: |
| Public top-level types (class/interface/object/typealias) | 388 |
| Internal top-level types | 261 |
| Public types imported by another module's main | 93 (cli 50 only-cli, core 28 only-core, mcp 3 only-mcp) |
| Public types imported by other modules only from tests | 30 |
| Public types never imported outside the engine | 265 (104 in `model` packages) |
| runtime-application symbols the engine imports | 56 (16 used by no other module; SKILL-370 owns) |

### Declarations and seams

| Measure | Count | Where |
| --- | ---: | --- |
| `typealias` re-exporting another module's type | 38 | `goalrunner/model/GoalRunnerPersistenceModelAliases.kt` 18, `work/model/IdeStatusModels.kt` 19, `goalrunner/persist/DurableChildRecoveryClass.kt` 1 |
| Non-sealed interfaces | 18 | 12 `fun interface`s. Each has a production implementation or SAM, and all but 3 have a test substitute. The 3 without one (`GoalPlanningRejectionRecorder`, `GoalPlanningStatusReasonCoherence`, `ExperimentTelemetryRecorder`) are substituted in tests through their companion `NONE`, or are deleted with experiments. |
| runtime-ports interfaces implemented in engine | 13 | 3 launcher observer callbacks (fine), 3 experiment-only, `DiffResolverPort`, 2 goal-runner callbacks consumed by SQLite (SKILL-376), `FeatureTaskRuntimeHeartbeat`/`ShutdownHookRegistration` callbacks |
| `@Inject` classes exposing constructor params as non-private `val` | 9 | `FeatureTaskRuntimeRunner` 11, `GoalRunnerStatusProjectionAssembler` 9, `GoalRunnerStatusProjectionDataSources` 5, others 1–2 |
| `@Inject` classes hand-constructing collaborators | 9 | e.g. `GoalRunnerStatusService` builds `GoalRunnerStatusControlVerbs`, `GoalRunnerRepairCoordinator`, `GoalRunnerAcceptanceCoordinator`. None of the 10 built classes is `@Inject`. |
| Parameter-bag classes (`*Args/*Context/*Inputs/*Boundaries/*Dependencies/*Bundle`) | 134 | 97 `Args` (71 featuretask, 26 goalrunner); 100 constructed at one site |
| `Map<String, Any?>` in public top-level signatures | 18 | 66 files use the type |
| JSON parse sites (`JsonCodec.parse*/decode*/anyToStringAnyMap`) | 71 | 13 files |
| `runCatching` | 124 | 61 files; 26 cancellation/interrupt rethrow sites in total |
| `var` fields | 81 | per-run state (`RunLoopModels` 15, `RunLoopSession` 8, `RunState` 3); one process singleton map (F-003) |
| Ambient time/random | 7 | `OffsetDateTime.now` ×3 (non-experiment), `Clock.systemUTC()` ×2 and `Instant.now` ×2 (experiment) |
| `java.nio.file.Files` | 4 files | all experiment |
| Vendor text | 4 | `"[SQLITE_BUSY]" in …` (F-004) |

## SKILL-352 follow-through

| SKILL-352 criterion | As specified | Landed |
| --- | --- | --- |
| AC3 dissolve single-site fact bags | bags gone; census and threshold recorded | Census recorded (97 `Args`, 2026-09-17), with no dissolution. 100 of 134 bags are still single-site. |
| AC9 liveness reads record or fail typed | both liveness resolutions | `resolveChildExecutionLiveness` still returns `UNKNOWN` silently (`GoalPlanningRefreshLiveness.kt:39`). |
| AC10 no fourth brace scanner | scanner deleted | Landed (guard present). |
| AC11 engine-only application types moved | moved | Landed for 30 types. 16 more engine-only symbols remain in application (SKILL-370). |
| AC12 internal by default; guard fails a new public declaration | census plus guard | Guard passes `includeDefaultPublic = false` (`RuntimeEngineBoundaryArchitectureTest.kt:79–109`), so it ignores Kotlin's default-public declarations and cannot fail. 265 public types are never imported outside the engine. History records "full engine visibility census … partial follow-up". |
| AC4 no `@Inject data class` under `model` | none | Landed. |

SKILL-352's retention decisions stay: the recorder facade stays concrete, the
`*Boundaries` collapse stands, and phase order and persistence do not change.

## Principle assessment

| Checklist item | Verdict | Evidence |
| --- | --- | --- |
| 1 Dependency direction | Clean, with one ABI note | Main edges inward only. `api` on application is justified today because engine public types expose application types (`FeatureTaskRuntimeAgentContext`, `GoalContinuationCandidate`); SKILL-370 narrows it after moving those. `api` on domain/ports is the pinned ABI closure (ARCHITECTURE.md Gradle Modules). |
| 2 Inbound side | Mostly clean | CLI and MCP call engine services directly (`GoalRunner`, `GoalRunnerStatusService`, `FeatureTaskPhaseSettlementService`); none reads a service's getters. The getters exist for engine-internal extension files (F-002). The pinned list names two port DTOs through aliases (F-009). |
| 3 Outbound side | One leak | SQLite error text drives live classification (F-004). Git NUL payload decoding in engine is SKILL-377's. Experiment declared engine-only seams as ports (F-001). |
| 4 Domain richness | Owned elsewhere | Goal-continuation decode and goal-parent projection copies are SKILL-372 F-006. `WorkflowEngine` hand-built at 6 engine sites is SKILL-372 F-007. Engine's `SQLITE_TIMESTAMP_FORMATTER` (`work/IdeStatusProjectorMapping.kt:152`) re-parses stored time and goes away with SKILL-372 F-009. |
| 5 Composition | Minor | No second root in the engine after experiments go (`RuntimeComponent.experimentGoalRunnerFactory` builds a second component, deleted by F-001). 9 classes hand-construct collaborators (F-002). |
| 6 Entry-point leakage | Clean because… | The only flag-like text is an operator hint naming `--apply` in `FeatureTaskRuntimeRemediationBaseReconciler.kt:360`. It is a user-facing remedy message the CLI prints verbatim. Prompt directives mention tools by design. |
| 7 Ambient effects | 7 sites | 3 `OffsetDateTime.now(ZoneOffset.UTC)` (`FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint.kt:86,100`, `GoalRunnerAcceptanceCoordinator.kt:43`), 4 experiment. No `System.getenv`, `nanoTime`, or `Thread.sleep`. The engine ambient-clock baseline is recorded but no test asserts it (F-008). |
| 8 State and transactions | One risk | Per-run `var`s are owned by per-run objects. Progress sequence numbers are allocated in memory by independent writers (F-003). Transactions are opened by engine callers through `DatabaseSessionFactory` (93 sites), except SQLite-side coordination moving in SKILL-376. |
| 9 Error model | Mostly typed | Typed `skillbill.error` families at decode seams (SKILL-352). No `catch (Exception)`. 124 `runCatching` sites, of which 4 swallow durable failures silently (F-006). `runCatching` also traps interrupts unless rethrown (26 rethrow sites exist). |
| 10 Cohesion and ownership | Findings | 265 public types with no outside consumer (F-005). 30 public types used by other modules only from tests. The goal runner reads through the run loop's write facade (F-007). |
| 11 YAGNI | Findings | Experiment framework (F-001), 38 aliases (F-009), 6 test-only companion null objects, unused serialization dependency, 134 bags (F-002). |
| 12 Naming and packages | Clean because… | No stutter paths in engine packages. Every test package exists in main. Sibling counts are enforced by `PackageSiblingCountArchitectureTest`, which scans engine roots (verified). |
| 13 Guard validity | See F-008 | Verified below. |

### Guard validity (step 2.13)

| Guard | Scan root | Reads engine files? |
| --- | --- | --- |
| `RuntimeLayerBoundaryArchitectureTest` "application domain and ports avoid direct file IO" | `sourceFiles()` relative to repo root; filter `runtime-kotlin/runtime-{application,domain,ports}/src/main/kotlin/` | Reads files. Engine excluded by filter. |
| `PortNullObjectAbsenceArchitectureTest` | module dir parent, `<module>/src/main` for non-nested modules | Reads engine. Regex matches only `object (Noop|Unavailable|Empty|Unconfigured)\w*`, so it misses companion `val` and `class`. SKILL-377 subtask 3 also edits it. |
| `RuntimeRawMapArchitectureTest` | filter `runtime-application/src/main/kotlin/` against paths starting `../../../runtime-kotlin` | Scans nothing (vacuous). SKILL-371 subtask 1 repairs it. |
| `RuntimeApplicationAmbientClockArchitectureTest` | per-module test methods | No engine method. `runtime-engine-ambient-clock-baseline.txt` is written by the recorder and read by nothing. |
| `InjectConstructorDefaultsArchitectureTest` | per-module baselines | Engine has no case (`PrincipleEnforcementInventory.injectDefaultsBaselineForModule` returns `null`). |
| `RuntimeEngineBoundaryArchitectureTest` visibility case | `../../../runtime-kotlin/runtime-engine/src/main/kotlin` | Reads files. `includeDefaultPublic = false` makes it inert (see SKILL-352 AC12). |
| `FeatureTaskRuntimeParameterBagArchitectureTest`, `FeatureTaskRuntimeRunLoopContextExtensionCensusArchitectureTest` | `runtime-kotlin/runtime-engine/...` | Read files. They pin names and source strings of the procedural form. |
| `RuntimeEngineInboundApiTest` | consumer roots | Vacuous per SKILL-371 F-001. SKILL-371 repairs it. |

## Findings

Ranked by priority.

### F-001 — High — Experiment support has no experiment

Evidence. SKILL-366 (`8ab347817`, 178 files, +8,616) was built for SKILL-365
(CodeGraph, spec removed `17e63e1d9`) and SKILL-367 (Jev, spec removed inside the
SKILL-366 merge). `RuntimeExperimentProvides` binds `experimentDescriptorCatalog()`
and `experimentArmMeasurementPort()` to `null`. `ExperimentSelectionService.resolveForLaunch`
(`engine/experiment/ExperimentSelectionService.kt:54–83`) therefore throws
`ExperimentDescriptorUnavailableError` for any name and returns an empty selection
otherwise. Both pair coordinators then delegate to the plain goal runner, and the
measurement branches (`ExperimentPairCoordinator.kt:876`,
`ExperimentNavigationPairCoordinator.kt:604`) are unreachable.

Size: about 4,800 main lines in ten modules (engine 2,981, sqlite 463, ports 400,
contracts 334, domain 201, core 164, infra-contracts 134, cli 104, host 37,
launcher 25), about 2,700 test lines, four `experiment-*-schema.yaml`
contracts, two named migrations, a `skill-bill experiments` group, `--experiments`
on `goal` and `goal preflight`, an `experiments` config key, and text in
`../../../skills/bill-feature/content.md`.

It also holds every engine filesystem IO site (`Files.walk/list/readAllBytes`
in `ExperimentPairCoordinator.kt:772–806`, `ExperimentFrozenSpecBundle.kt`,
`BoundedReadOnlyExperimentNavigationSessionRunner.kt`), 4 of the 7 ambient-time
sites, 3 ports only the engine implements and consumes
(`ExperimentSelectionPort`, `ExperimentParentDeliveryPort`,
`ExperimentNavigationSessionRunnerPort`), a production `NoopExperimentNavigationSessionRunner`,
88 lines duplicated between the two coordinators, and a second `RuntimeComponent`
per goal run.

Fix: delete the feature (user decision, 2026-09-22). Keep the SKILL-366
migration entries and append a drop migration. Existing config files keep
loading because `FileSystemRepoLocalConfig` reads only the keys it knows
(L67–L69).

### F-002 — High — Procedural run loop and goal runner

Evidence. `featuretask/runloop` is 20 stateless `object FeatureTaskRuntimeRunLoop*`
namespaces. Of its 539 functions, 84 take `FeatureTaskRuntimeRunRequest`, 75
`FeatureTaskRuntimeRunState`, 68 `FeatureTaskRuntimePhaseRecorder`, 33
`FeatureTaskRuntimeRunObservability`, 30 `FeatureTaskRuntimePhaseGates`, 18
`FeatureTaskRuntimeRunLoopSession`, and 16 `FeatureTaskRuntimeGoalContinuationRecorder`.
Past six parameters, the code adds a bag. Bags repeat the same collaborators
(`RunState` in 27, `RunObservability` in 27, recorder in 22, request in 21).
Example: `SettleValidatedOutputCommitArgs` (`FeatureTaskRuntimeRunLoopAttemptSettlement.kt:101`,
one construction at L865) carries nine collaborators and four facts.

The goal runner repeats the pattern. `GoalRunnerSharedArgs.kt` holds 13 bags.
`DefaultGoalPlanningSweep` has 32 extension functions across planning files,
fed by `Produce*Args` and `GoalPlanning*Context` bags, and reached through a
public `manifestFileStore` getter. `FeatureTaskRuntimeRunner` exposes 11
constructor collaborators as public `val`s so that four sibling files of
`FeatureTaskRuntimeRunner.*` extension functions can reach them. Nothing outside
the engine reads them. `GoalRunnerStatusProjectionAssembler` exposes 9 the same
way. Nine `@Inject` services build their collaborators by hand (for example,
`GoalRunnerStatusService` builds three coordinators), so tests cannot substitute
them and the container cannot wire them.

Fix: a responsibility is a class. The per-run owner constructs it once. Its
constructor takes the collaborators it uses, and its methods take per-call
facts. Extension-function files merge into their receiver class or become such
classes. Constructor getters become `private`. Hand-built collaborators become
`@Inject` constructor dependencies where kotlin-inject can resolve them (public
types, no per-run values). Classes that need per-run values stay constructed by
the per-run owner. This follows ARCHITECTURE.md "pass helpers the facts or
capabilities they use": each class receives only its own collaborators. It
supersedes the 2026-09-15 SKILL-247 helper-input form and the 2026-09-17 bag
census.

### F-003 — High (plausible; only a test can confirm) — One durable sequence, four in-memory counters

Evidence. Progress-event sequence numbers for a goal's ledger come from
counters seeded independently from `ledgerSequenceWatermarks`:

- `GoalRunnerProgressEventEmitter.kt:19,29`: seeded once at construction,
  `sequence++`.
- `DurableGoalPlanningAttemptRecorder` (`GoalPlanningAttemptRecorder.kt:25–54`):
  a `@RuntimeSingleton` holding `mutableMapOf<String, Int>` per workflow, seeded
  once per process lifetime and `@Synchronized`.
- `GoalRunnerLedgerRecorder.kt:30,93` and `GoalRunnerObservabilityEmitter.kt:21,54`
  do the same for the ledger and observability streams.

When the planning sweep records attempts during a goal run that also emits
progress events for the same workflow, both counters start from the same
watermark and can assign the same numbers. The singleton map also never forgets
a workflow and never sees another process's writes.

Fix: assign the sequence where the row is written, inside the write transaction
(max + 1). Delete the per-writer counters and the singleton map. The port
signature loses the caller-supplied number. Row shape stays the same.

### F-004 — Medium — SQLite error text classifies live failures

Evidence. `FeatureTaskRuntimeRunLoopPhaseRunner.kt:435` decides RETRYABLE versus
NEEDS_USER_ACTION with `"[SQLITE_BUSY]" in error.message`. L207–L209 and
`FeatureTaskRuntimeRunState.kt:347` match the same text in persisted block
reasons. SKILL-370 excludes these sites (its non-goal), and SKILL-372 finds none
in domain.

Fix: the SQLite session boundary translates a busy failure into one typed
exception in the existing `skillbill.error` taxonomy (runtime-contracts). SQLite
already depends on contracts, and so does the engine. L435 checks the type. The
persisted-reason checks stay because they read historical rows, and new reasons
keep the same message text, so stored bytes do not change.

### F-005 — Medium — Public surface with no consumer; visibility guard inert

Evidence. 265 of 388 public engine types are never imported by another module.
161 of them sit outside `model` packages. The SKILL-352 AC12 guard cannot fail
(see guard table).

Fix: narrow what compiles. kotlin-inject generates runtime-core's component in
runtime-core, so every type that component constructs or injects must stay
public. Only types outside the DI graph can become `internal`. Then set
`includeDefaultPublic = true` on the existing guard.

### F-006 — Medium — Four durable reads swallow failures

Evidence.

- `UnaddressedFindingsLedgerService.kt:86,91`: `runCatching{…}.getOrNull()` on the
  review-state decode.
- `resolveChildExecutionLiveness` (`GoalPlanningRefreshLiveness.kt:39`):
  `getOrDefault(UNKNOWN)`.
- `preplanProseValue`/`preplanProsePrompt` (`GoalPlanningProvenanceRecoverability.kt:50,62`):
  `getOrDefault("")`/`getOrNull()` on a durable payload.
- `DurableGoalPlanningRejectionRecorder.record` (`GoalPlanningRejectionRecorder.kt:21`):
  a bare `runCatching`.

`../../../docs/observability-policy.md` requires a record for each.

Fix: typed failure or one bounded record through `RuntimeDiagnosticsBestEffortWarning`,
with interrupts rethrown.

### F-007 — Medium — Goal runner reads through the run loop's write facade

Evidence. `FeatureTaskRuntimePhaseRecorder` has 56 functions, is used by 40
files, and builds its parts by hand, including `FeatureTaskRuntimeWorkflowPersistence`
twice (L49–L75). Goal-runner consumers use only reads:
`GoalRunnerStatusProjectionAssembler` (4 reads), `GoalRunnerStopReports` (1),
`GoalRunnerRepairCoordinator` (1), and `ChildAwareGoalPlanningRefreshLiveness` (2).

Fix: a concrete read-only query over the existing parts. Goal-runner readers
depend on it, and the facade delegates the same reads to it. No new interface.

### F-008 — Medium — Guards skip the engine

Evidence. See the guard validity table. The engine is filtered out of the
file-IO rule. Its clock baseline is written but never asserted, and it has no
inject-defaults case. The null-object regex misses companion `val`s. Six
test-only companion null objects sit in production (`GoalPlanningRejectionRecorder.NONE`,
`GoalPlanningRefreshLiveness.IDLE`, `GoalPlanningAttemptRecorder.NONE`,
`GoalPlanningStatusReasonCoherence.NONE`, `GoalPlanningSweep.NONE`,
`GoalRunnerExecutionCoordinator.NONE`), used only by `GoalPlanningSweepTest`,
`GoalRunnerTestFactory`, and `GoalRunnerSharedTestFactory` in engine test/testFixtures.
The 2026-09-06 decision (a) sends these to testFixtures.

Fix: add runtime-engine to the existing file-IO filter, add companion-`val`
detection to the null-object census (SKILL-377 subtask 3 adds `class`
detection), and inject a `Clock` at the three non-experiment sites. Engine
coverage for ambient clock, ambient environment, and inject defaults belongs to
SKILL-373 subtask 2, which runs after 378.1 and drops the dead baseline rows.
The raw-map extension waits for SKILL-371 subtask 1.

### F-009 — Low — 38 re-export aliases

Evidence. See the census. `GoalRunnerRepairRequest` and `IdeStatusRequest` are
on the pinned inbound list as engine names for port DTOs. The CLI imports
`skillbill.engine.work.model.IdeStatus*` 38 times. SKILL-372's non-goals leave
engine port-type aliases unowned.

Fix: delete them and import the owners. CLI and MCP already depend on runtime-ports.

### F-010 — Low — Small debris

- `runtime-engine/build.gradle.kts`: `kotlinx-serialization-json` is declared and
  never imported. SKILL-374 subtask 1 removes it, so it is not in this bundle's scope.
- `FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint.kt:84–89` inlines the wire
  keys `"reason"`, `"retried_at"`, `"previous_blocked_reason"`, `"reopened_phase_ids"`,
  and `"goal_continuation_outcome"` beside an owned artifact key. AGENTS.md requires
  one owner. The fix is to encode through the domain `FeatureTaskRuntimeOperatorBlockRetry`
  owner and the typed artifact accessors SKILL-372 subtask 2 adds. SKILL-372 makes
  domain artifact-key constants `internal`.

## Over-engineering register

| Item | Kind | Disposition |
| --- | --- | --- |
| Experiment framework, 4 schemas, 3 engine-only ports, null-bound seams | Speculative generality | Delete (F-001) |
| 134 parameter bags, 100 single-site | Lint-driven indirection | Dissolve into classes (F-002) |
| 20 stateless run-loop objects plus extension files reaching public getters | Procedural decomposition | Classes with private constructor dependencies (F-002) |
| 38 re-export aliases | Pass-through layer | Delete (F-009) |
| 6 test-only companion null objects | Test code in production | Move to testFixtures (F-008) |
| Procedural-form census tests (`…ParameterBag…`, `…RunLoopContextExtensionCensus…`) | Tests pinning structure | Rewrite the first to the step-class rule; delete the second |
| Four in-memory sequence counters | Duplicated ownership | One allocation at write (F-003) |

## What stays unchanged

| Item | Reason |
| --- | --- |
| Module graph and edge direction; runtime-engine/runtime-application split | Long-running loop versus request/response use cases is a sound seam. SKILL-370 owns the `api` → `implementation` change. |
| `DatabaseSessionFactory`/`UnitOfWork` persistence boundary | Recorded 2026-09-06 decision. Engine callers already own their transactions. |
| Engine `fun interface` seams with test substitutes (`GoalPlanningAttemptRecorder`, `GoalPlanningSweep`, `GoalRunnerExecutionCoordinator`, …) | House rule: a test substitute justifies an interface. Only their production null objects move. |
| `GoalRunnerEventSink.NONE`, `FeatureTaskRuntimeRunEventSink.NONE` | Production defaults (request defaults, CLI formatting). |
| `FeatureTaskRuntimePhaseRecorder` as a concrete facade for run-loop writes | SKILL-352 AC5 retention. Only reads split off (F-007). |
| `FeatureTaskRuntimeRunState` as single owner of per-run transitions | ARCHITECTURE.md State Ownership. |
| Launcher observer callbacks the engine implements (`AgentRunOutputSink`, `AgentRunProgressEmitter`, `AgentRunProgressProbe`) | AGENTS.md prescribes injected strategies. |
| Persisted-reason `[SQLITE_BUSY]` checks | They read historical rows. |
| `LongParameterList.functionThreshold` 6 | detekt default. The bags, not the threshold, were the wrong response. |
| Rejected: interfaces for step classes, a step/strategy framework, per-run DI subcomponents | One implementation each, no substitute: speculative. |
| Rejected: inbound use-case interfaces for CLI/MCP | No second implementation, no substitute. |
| Rejected: splitting files by size, renaming the `FeatureTaskRuntime*` prefix | Churn with no boundary gain. Ceilings stay 1,200 lines / 40 functions. |
| Rejected: typing `DecompositionSubtask.status` (compared to `"in_progress"` in `GoalRunnerHardResetOrphanTrap.kt:70,75`) | The field is a `String` in domain wire models. An enum ripples through the manifest codec. SKILL-372 owns domain types. |
| Rejected: Kotlin `explicitApi()` | Default-public plus a working visibility guard is enough. `explicitApi` adds modifiers to 388 declarations. |
| Rejected: collapsing `GoalRunnerChildRepairRunnerPort`/`GoalRunnerChildRepairStore`/`GoalChildPlanningHydratorPort` now | SKILL-376 subtask 2 moves their SQLite callers into the engine and keeps the interfaces. Collapse is a follow-up after it lands. |

## Coordination with concurrent bundles

All sibling bundles are uncommitted. They were cross-read on 2026-09-22 at HEAD
`85209c086`, where the SKILL-368 commit also committed the engine ambient-clock
baseline with the four experiment rows. Each overlap below names one owner.

| Bundle | Overlap | Owner | Sequencing |
| --- | --- | --- | --- |
| SKILL-368 build-logic | Governed-resource entries in `runtime-infra/contracts/build.gradle.kts` include the experiment schema copies | 368 owns the DSL; 378.1 deletes the experiment entries | 378.1 after SKILL-368 merges |
| SKILL-370 application | Moves engine-only helpers into engine; deletes application aliases (`WorkflowFamily`); engine→application becomes `implementation`; adds the inject-property rule to `InjectConstructorDefaultsArchitectureTest` for application | 370 | 378.2 and 378.3 after 370. 378.2/378.3 extend 370's inject-property rule to engine packages instead of writing their own. |
| SKILL-371 CLI | 371.1 repairs vacuous scanners (raw-map, inbound API) and pins SKILL-366 experiment types. 371.2 reworks the `experiments` CLI command. 371.3 edits `GoalPlanningSweep.kt`, `GoalPlanningSharedPreplanProduction.kt`, and `GoalPlanningStatusReasonCoherence.kt` for repo identity. | 371; experiment items become moot once 378.1 lands | 378.1 before 371, so 371 skips its experiment items. 378.3 after 371.1 and 371.3. |
| SKILL-372 domain | 372.1 types `WorkflowStateSnapshot` and migrates 108 artifact call sites incl. engine `artifactsFrom`. 372.2 migrates engine raw artifact reads and collapses goal-parent shells. 372.3 deletes the engine `GoalContinuation` alias (domain target). Stored-time typing removes the engine `SQLITE_TIMESTAMP_FORMATTER`. | 372 | 378.2 after 372.1 and 372.2. 378.3 deletes the remaining 37 aliases. |
| SKILL-373 core | F-009 (experiment wiring) delegated to 378.1. 373.2 collapses the ambient-clock, ambient-environment, and inject-default tests into module loops that include runtime-engine, and prunes tests by a survival rule that removes count and source-restating pins. 373.3 moves the suite to runtime-core `src/repoTest` (no new module). | 373 owns ambient/inject-default guard coverage; 378 fixes engine sites | 373.1 and 373.2 after 378.1. 378.1 injects the three `OffsetDateTime.now` sites and adds no clock guard; 373.2 drops the now-dead baseline rows. 378.2's step-class rule lives in `RuntimeEngineBoundaryArchitectureTest` (kept by 373) because 373.2 may delete `FeatureTaskRuntimeParameterBagArchitectureTest` and the run-loop census test first. Recommendation for 373.2: keep SKILL-370's inject-property rule scoped to application until 378.3 extends it to engine; otherwise 373.2 has to restructure engine getters to pass it. |
| SKILL-374 contracts | 374.1 removes the unused engine kotlinx dependency (and infra:http's) and moves the failure-kind enums into `error.featuretask`. 374.2 moves engine-owned keys objects into engine. 374 excludes every experiment item and runs 374.2 after 378.1. | 374 (dependency) | 378.1 does not touch the dependency. 378.2's typed busy exception goes in the existing `skillbill.error.core`. |
| SKILL-375 MCP | Handlers call engine services directly | 375 | Either order (375's own statement) |
| SKILL-376 infra | 376.1 fixes the experiment pair store's clock (skips if 378.1 deleted it). 376.2 moves 3,877 lines of SQLite goal-runner coordination into `skillbill.engine.goalrunner` and keeps port signatures (its AC4). | 376 | 378.3 after 376.2. 378.3 changes the progress-write signature (F-003) and includes the moved classes in the step-class rule. Child-repair and hydrator port collapse is a follow-up after 376.2. |
| SKILL-377 ports | 377.1 types git results that engine decodes on NUL in 13 places (featuretask) and flattens the remaining git capabilities. 377.3 extends `PortNullObjectCensus` to `class` declarations, repairs its scan roots, and renames `*Model` types behind the 21 ports aliases. 377 has dropped every experiment item. | 377 | 377.1 after 378.1. 378.1 adds companion-`val` detection scoped to runtime-engine; `class` detection and runtime-ports' own test-only `NONE`s are 377.3's. 378.2 after 377.1. 378.3 after 377.3 (alias renames). |

Proposed global order, consistent with every sibling's stated dependencies and
with no cycle: SKILL-368 → **378.1** → SKILL-370 → (371.1, 372.1, 373.1, 374,
376.1) → 376.2 → 372.2 → 377.1 → 371.2/371.3, 373.2 → **378.2** → **378.3**.
Contract and semantic changes land first. The two large engine restructurings
run last, so they rebase once onto settled contracts instead of forcing every
sibling to rebase onto them.

Corrections recorded: F-009's 38 aliases are 36 ports targets, 1 domain target
(`GoalContinuation`, deleted by 372.3), and 1 intra-engine alias
(`DurableChildRecoveryClass`). F-010's dependency item belongs to SKILL-374.

## Limits and what only compiling can confirm

- Regex censuses under-count multi-line headers and SAM lambdas. Reflective and
  generated consumers are invisible.
- Which public types can become `internal` depends on kotlin-inject's generated
  component. Only compiling runtime-core decides (F-005).
- F-003 is plausible from the code, not observed. A test with a concurrent
  planning sweep and goal progress emission confirms or refutes it.
- The run-loop class boundaries in F-002 are a design direction. Exact grouping
  is decided in implementation under the 1,200-line / 40-function ceilings.
- Experiment deletion must be preceded by a fresh reference census. The
  SKILL-377 census confirms that the linked-worktree git operations have only
  experiment consumers.
