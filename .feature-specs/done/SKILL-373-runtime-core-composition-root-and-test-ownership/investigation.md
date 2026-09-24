# SKILL-373 investigation - runtime-core and its place in the hexagonal graph

## Execution rule

This bundle runs on the current tree. It does not wait for a subtask of another issue. Ordering notes later in this file are overlap context. If a change this bundle's acceptance criteria need is missing, make it here. If it is already present, keep it.

## Judgment

runtime-core is a sound composition root with a small amount of misplaced work,
and a test module that carries the whole repository's architecture suite with
real enforcement holes. Keep the root, kotlin-inject, the accessor surface, and
the single module topology that SKILL-350 established. Fix four things:

1. The architecture guards do not cover runtime-engine for three rules, and code
   that violates them has already merged (F-001).
2. Repository checks run in a cached Gradle task that does not declare the files
   they read (F-002).
3. The composition root holds five parameter bags and a vacuous `@JvmSynthetic`
   mandate, and a second composition root lives in infrastructure (F-004 to F-006).
4. Redundant guards, prose pins, migration guards, and line-number baselines make
   the suite expensive to change without adding protection (F-003, F-007, F-008).

No new module, framework, abstraction, or architecture-test class is needed.

## Method and baseline

- Baseline: HEAD `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b` on
  `feat/SKILL-368-build-logic-architecture-cleanup`. The first pass measured
  `11d8615ba`; a concurrent session rewrote that history (SKILL-368 recommitted as
  `2fa21c330`/`dbf9f4830`). runtime-core main is identical between the two; four
  baseline files differ.
- SKILL-368 then amended its subtask 2 commit to `85209c086` (verified in the
  cross-bundle pass). Relative to `dbf9f4830`, runtime-core changes only in four
  baseline files: line shifts from a Spotless reformat, plus four engine
  ambient-clock rows for the experiment coordinators. Counts are from
  `dbf9f4830` unless marked; the four baseline files are as of `85209c086`.
- Numbers come from `wc`, `grep`, and Python scripts over the tree and over the
  kotlin-inject generated sources in `build/generated/ksp` (built 2026-09-22
  17:50). No review subagents were used.
- Prior work read in full: `../SKILL-350-runtime-core-composition-and-architecture-guards`.
- Area logs: runtime-core has no `../../../agent` directory; the owning logs are
  `runtime-kotlin/agent/decisions.md` and `history.md`.

## Census

### Size

| Part | Files | Lines |
| --- | --- | --- |
| `src/main` (`skillbill.di.*`) | 25 | 1,322 |
| `src/test/.../architecture` | 65 Kotlin + 59 baselines | 14,989 |
| `src/test` other | 26 | 7,871 |
| `src/testFixtures` | 1 | 17 |

Main packages: `di.core` 4, `di.goal` 4, `di.experiment` 3, `di.install` 3,
`di.review` 3, `di.featuretask` 2, `di.scaffold` 2, `di.workflow` 2,
`di.featurespec` 1, `di.telemetry` 1. All under the sibling limit.

### Gradle edges

| Configuration | Edges |
| --- | --- |
| `api` | runtime-application, runtime-ports, runtime-engine |
| `implementation` | runtime-domain, runtime-contracts, 7 runtime-infra modules, kotlin-inject runtime |
| `ksp` | kotlin-inject compiler |
| `testImplementation` | testFixtures of application, engine, ports, domain, infra:sqlite; serialization-json, jackson-yaml, junit, kotlin-test |
| Consumers | runtime-cli and runtime-mcp `implementation`; runtime-mcp `testImplementation(testFixtures(runtime-core))` |

The `api` edges are the kotlin-inject ABI: `RuntimeComponent`'s public members
expose application, engine, and ports types. No infrastructure or entrypoint
module is `api`.

### Declarations and consumers

| Declaration | Visibility | Cross-module consumers |
| --- | --- | --- |
| `RuntimeComponent`, generated `create` | public | cli main 2 files, mcp main 2, cli test 5, mcp test 2 |
| `SkillBillVersion` | public | mcp main 1, cli test 3, mcp test 2 |
| `GoalRunnerManifestPersistenceDependencies`, `GoalRunnerManifestProjectionDependencies` | public | generated `InjectCliComponent` only |
| 3 more `GoalRunner*Dependencies` bags | internal | none |
| 21 `Runtime*Provides` mixins, `RuntimeBootstrapBindings`, `FilesystemExperimentIsolationCapability`, `resourceVersion` | internal | runtime-core tests only |
| `repoRootFromTest()` (testFixtures) | public | runtime-mcp tests |

Typealiases: 0. `Map<String, Any?>` in signatures: 0. JSON parsing: 0. Path
literals: 1 (`".skill-bill/runtime.db"`, F-009). Broad catches: 0.

### Accessors

`RuntimeComponent` declares 50 `abstract val` accessors; the component and its
mixins declare 137 `@Provides` functions. A kotlin-inject child component
(`CliComponent`, `McpComponent`) reads a parent accessor when one exists;
otherwise it inlines the parent's provider chain and builds the arguments itself.
A provider whose parameter is a concrete adapter is therefore reachable from a
child only through an accessor, because runtime-cli and runtime-mcp have no
infrastructure dependency. The accessors keep adapter construction inside
runtime-core.

Counting readers in handwritten sources and in the generated CLI and MCP
components, 3 accessors have none: `goalPlanningPreparationCheckpoint`,
`installedWorkspaceBaselineStatusPort`, `uninstallPathsPort`.

### Construction

25 providers construct an adapter by hand (for example `GhGoalPullRequestPort()`,
`SqliteFeatureTaskPhaseSettlementRepository(database)`, five schema validators);
none of those classes has an `@Inject` constructor. About 100 providers take an
`@Inject` adapter as a parameter. Both are ordinary composition.

### Package graph

Tarjan SCC over `skillbill.di.*` imports finds one component:
`{di.core, di.telemetry, di.workflow}`. `di.core` mixes in every area;
`RuntimeTelemetryProvides` imports `SkillBillVersion` and `RuntimeWorkflowProvides`
imports `RuntimeBootstrapBindings` back from `di.core`. The baseline records it as
two pairs (`core|telemetry`, `core|workflow`) because the acyclicity guard reports
pairs.

### Tests

| Test package | Files | Main package exists |
| --- | --- | --- |
| `skillbill.architecture` | 65 | no |
| `skillbill.application` | 16 | no |
| `skillbill.application.featurespec` | 1 | no |
| `skillbill.telemetry` | 3 | no |
| `skillbill.contracts.review` | 1 | no |
| `skillbill.review.review` (stutter) | 1 | no |
| `skillbill.di.absent`, `skillbill.di.runtime` | 2 | no |
| `skillbill.di.core`, `skillbill.di.experiment` | 2 | yes |

No runtime-core test imports runtime-cli or runtime-mcp. 6 of the 26 non-suite
test files construct `RuntimeComponent`; most of the rest run a service against
real adapters. Three non-suite files and six suite files (installer and launcher
shell tests, one ports behavior test) test a subject another module owns (F-010).

Architecture suite: 321 `@Test`, 160 `Regex(`, 88 synthetic-source fixtures,
59 baseline files (53 empty; 20 rows in the other six at HEAD).

## Prior work: SKILL-350

| SKILL-350 item | Landed? | Evidence |
| --- | --- | --- |
| F-001 one resolved context and requester per component | yes | `RuntimeComponent.resolvedRuntimeContext by lazy`; `RuntimeComponentInvocationSnapshotTest` |
| F-002 launcher uses the selected `ExecutableLookup` | yes | `FileSystemAgentRunLauncher` `@Inject` constructor takes `executableLookup` |
| F-003 classify public callables; `@JvmSynthetic` is not access control | partly | Classifier keys on `@Provides`; the rule mandating `@JvmSynthetic` remains and is vacuous (F-005) |
| F-004 alias-aware composition guard | yes | `composition guard rejects alias import construction` |
| F-005 one expected topology; serialization test-scoped | yes | `RuntimeModuleCatalog.moduleEdgeExpectations`; `testImplementation(libs.kotlinx.serialization.json)` |

Retention decisions kept: kotlin-inject; single-implementation ports and one-line
provides; scopes only where identity matters; the curated accessor inventory;
one test-owned exact topology with rejection tests; no separate test module;
burst schedule and review broker factory; no rename based on the word "core".

Retention decision revisited with new evidence: SKILL-350 rejected moving tests
out of runtime-core because "a separate test module adds build structure without
fixing the demonstrated failures". This bundle adds no module. It moves the suite
into a `repoTest` source set of runtime-core itself, because F-002 is a failure
that only declared inputs fix, and it moves nine test files whose only subject
lives in another module.

## Principle assessment

| # | Question | Answer and evidence |
| --- | --- | --- |
| 1 | Dependency direction | Inward. `api` edges are the kotlin-inject ABI closure. Clean. |
| 2 | Inbound side | CLI and MCP obtain services through child components. They also consume driven ports exported as accessors (`scaffoldGateway` 11 generated reads, `installNativeAgentLinkPort` 8). Out of scope, unowned (F-013). |
| 3 | Outbound side | runtime-core declares no port. Clean because it is the root. |
| 4 | Domain richness | One rule sits in composition: `FilesystemExperimentIsolationCapability`; SKILL-378 deletes it (F-009, F-013). |
| 5 | Composition | Five parameter bags (F-004); second root `ScaffoldStandaloneEntrypoint` in runtime-infra/skills main (F-006); no service locator. |
| 6 | Entry-point leakage | None in runtime-core main. Clean. |
| 7 | Ambient effects | Bootstrap reads `System.getenv` and `user.home` (sanctioned edge); `SkillBillVersion` reads a classpath resource (documented). runtime-engine is unguarded (F-001). |
| 8 | State and transactions | `resolvedRuntimeContext` is a synchronized lazy; scoped: database factory, attempt recorder, worker supervisor. SQLite opens a connection per operation (`.use {}` in `SQLiteDatabaseSessionFactory`). Clean. |
| 9 | Error model | Typed: `UnresolvedRemoteTransportPortError`, `ExperimentIsolationCapabilityRefusalError`; version fallback records a substitution. No broad catch. Clean. |
| 10 | Cohesion and ownership | Nine test files have a subject outside runtime-core (F-010). MCP reads the global `SkillBillVersion.VALUE` (F-012). |
| 11 | YAGNI | 137 `@JvmSynthetic` (F-005); 5 bags (F-004); 3 unread accessors, a function binding with no substitute, a discarded statement (F-012). |
| 12 | Naming and packages | 7 test packages without a main package, one stutter (F-010). Main packages within limits. |
| 13 | Guard validity | `ArchitectureScanSupport.runtimeRoot` is the repository root. Guards cited here use `runtime-kotlin/...` paths and read files: comment ban (roots `runtime-kotlin`, `intellij-plugin`, `../../../runtime-kotlin/build-logic`), composition guard (`runtime-kotlin/runtime-core/src/main/kotlin/skillbill/di`, non-empty census asserted), acyclicity (runtime-core rows present), catalog edge tests (`runtimeKotlinModuleDirectory`). Vacuous: the `@JvmSynthetic` rule (F-005); `PortsDeclarationArchitectureTest` (resolves `runtime-kotlin/runtime-kotlin/runtime-ports`) and `PortNullObjectAbsenceArchitectureTest` (drops the infra modules; doubled prefix in its testFixtures case), both kept and repaired by SKILL-377 subtask 3; the infra package-cycle scan (`packagePrefixForModule` returns `skillbill.` for infra modules, so every edge is dropped), repaired by SKILL-376 subtask 3. Missing coverage: runtime-engine in three rules (F-001). SKILL-371 F-001 owns the other vacuous scanners. |

## Findings

### F-001 High - runtime-engine is outside three architecture rules, and violations merged

`RuntimeApplicationAmbientClockArchitectureTest`, `AmbientEnvironmentArchitectureTest`,
and `InjectConstructorDefaultsArchitectureTest` declare one hand-written test
method per module. None has a runtime-engine method, although
`runtime-engine-ambient-clock-baseline.txt` (3 rows),
`runtime-engine-ambient-environment-baseline.txt`, and
`runtime-engine-inject-constructor-defaults-baseline.txt` (empty) exist and
`ArchitectureBaselineRecorder` writes them. `PrincipleEnforcementInventory.moduleArchitectureScanCases`
lists every module, engine included; the tests do not iterate it. (Package
cycles cover the engine through `engine subareas have an empty package cycle
baseline`.)

Merged violations the gap let through, all from SKILL-366:

- `ExperimentPairCoordinator.kt:64` and `ExperimentNavigationPairCoordinator.kt:80`:
  `private val clock: Clock = Clock.systemUTC()` in `@Inject` constructors, which is
  both an ambient clock read and an inject-constructor default.
- `ExperimentPairCoordinator.kt:884`, `ExperimentNavigationPairCoordinator.kt:612`:
  `Instant.now(clock)`, which the scanner encodes as an ambient site.
- At least four more `@Inject` defaults in the same constructors
  (`telemetryRecorder = null` twice, `random = Random.Default`, `measurementPort = null`).

SKILL-368 (`85209c086`) re-recorded the engine baseline with those four clock
rows, which confirms the recorder sees them and no test compares them.

Fix: each of the three rules runs one test over `moduleArchitectureScanCases`, so
a module in settings is covered by construction. SKILL-378 subtask 1 deletes both
coordinators with experiment support and injects `Clock` at the three remaining
engine `OffsetDateTime.now` sites, but adds no guard. Engine coverage for all three
rules is this bundle's, and so is dropping the engine baseline rows whose sites
are gone. Any site the restored rules still report is fixed by injection, not
baselined. SKILL-370's inject-property rule stays scoped to runtime-application;
SKILL-378 subtasks 2 and 3 extend it to engine after restructuring the engine's
public-getter classes.

Feasibility: iteration reuses the existing scan-case list and assertion helpers.
Only compiling confirms how many sites the restored rules report after SKILL-378;
the subtask stops and reports if it exceeds 15.

### F-002 High - repository checks run without declared inputs under the build cache

`gradle.properties` sets `org.gradle.caching=true`. `:runtime-core:test` runs the
architecture suite and the installer shell tests, which read `ARCHITECTURE.md`,
`agent/*.md`, every `build.gradle.kts`, every module's main and test sources, and
`../../../install.sh`/`uninstall.sh`. The test task declares only its classpath. A
comment-only edit to any main source yields identical jars, so the comment ban
can come back `FROM-CACHE`; edits to scripts, docs, or other modules' test sources
do not invalidate the task.

The repository already owns the fix: `skillbill.repo-test` (`RepoTestConventionPlugin`)
creates a `repoTest` source set whose task declares `governedRepositorySources()`
(including `../../../install.sh` and `uninstall.sh`). runtime-core does not apply it;
runtime-cli does.

Fix: runtime-core applies `skillbill.repo-test`; the suite moves to
`runtime-core/src/repoTest`; runtime-core's build file adds the runtime-kotlin trees
the suite reads as inputs of that task. Installer and launcher shell tests move to
runtime-cli `repoTest`, whose inputs already cover the scripts.

Feasibility: the `repoTest` compilation extends `test` implementation and depends on
`test` output, so support code and fixtures keep resolving.

### F-003 Medium - redundant module-graph guards beside the single topology

SKILL-350 made `RuntimeModuleCatalog.moduleEdgeExpectations` the one expected
topology, compared per configuration in `RuntimeCoreCompositionOnlyTest` and
`RuntimeAdapterDependencyAllowlistTest`. Two hand-listed deny-lists still restate
parts of it: `RuntimeGradleModuleLayeringTest.top level runtime modules do not
depend upward` (26 module literals) and
`ImplementationOwnershipArchitectureTest.infrastructure modules do not depend on
adapters or runtime core` (7 literals, substring match over any configuration).
Every edge they forbid is absent from the pinned topology, so they cannot fail
unless the catalog changes too.

Fix: delete both; the catalog comparisons stay.

### F-004 Medium - parameter bags work around two missing accessors

`RuntimeGoalRunnerStoreProvides.kt` (175 lines) declares five `GoalRunner*Dependencies`
classes and five providers that fill them, then unpacks them into
`WorkflowGoalRunnerManifestStore(...)` and `WorkflowGoalRunnerOutcomeStoreDependencies(...)`,
both already `@Inject`. The CLI child reaches `GoalRunnerManifestStore` by inlining
`experimentParentDeliveryPort(manifestStore = runtimeComponent.goalRunnerManifestStore(persistence = ...))`,
so each provider had to take only port-typed parameters. Two bags are public for
the generated CLI code.

Fix: declare `goalRunnerManifestStore` and `goalRunnerWorkflowOutcomeStore`
accessors, bind both ports to their `@Inject` adapters, delete the bags.

Feasibility: the same pattern already serves `installNativeAgentLinkPort`, whose
provider takes `FileSystemInstallNativeAgentLinks` and which the child reads 8
times through the accessor. Only regenerating KSP confirms the child reads the new
accessors.

### F-005 Medium - 137 `@JvmSynthetic` annotations enforced by a rule that cannot fail

`RuntimeImplementationImportRules` requires `@JvmSynthetic` "so concrete adapter
signatures do not become Java-visible runtime-core API". Its pattern
`@Provides\s+(?!@JvmSynthetic\s+)(internal\s+fun ...)` runs over `RuntimeComponent.kt`
only, which declares no `internal fun` provider; every provider is
`@Provides @JvmSynthetic fun` inside an internal interface. The rule matches
nothing with or without the annotation. The runtime has no Java consumer, and
SKILL-350 established that `@JvmSynthetic` is not Kotlin access control.

Fix: remove the annotations and the rule. Feasibility: the SKILL-350 callable
classifier keys on a preceding `@Provides` line (`compositionPrefixHasProvidesAnnotation`),
so it still passes.

### F-006 Medium - a second composition root in infrastructure main source

`runtime-infra/skills/.../scaffold/runtime/service/standalone/ScaffoldStandaloneEntrypoint.kt`
wires `FileSystemScaffoldRepoValidation`, `FileSystemScaffoldSourceLoader`, and
no-op install seams into a public `scaffold(...)`. Its callers are eight
runtime-infra/skills test files and two repoTest files. Production scaffolding goes
through `RuntimeComponent`. `PrincipleEnforcementInventory.sanctionedCompositionEntrypoints`
exempts the file from the composition guard; it is the list's only entry.

Fix: move it to runtime-infra/skills `src/test` and delete the exemption list.
Feasibility: it calls `internal` `scaffoldWithAdapters` and `ScaffoldAdapterSeams`.
`testFixtures` is not a friend compilation and cannot see them; `src/test` can,
and `repoTest` sees test output.

### F-007 Medium - prose pins and migration guards

- `RuntimeArchitectureDocumentationTest` (420 lines, 12 tests) and assertions in
  nine other files pin `ARCHITECTURE.md` and `../../../agent/decisions.md` wording, for
  example `"If Kotlin-Inject ever requires a"` in `RuntimeImplementationImportRules`.
- About 15 tests guard completed migrations: `implementation ownership moved out of
  runtime core`, `nested infrastructure ids ... replace flat directories`, `runtime
  build sources contain no flat infrastructure project references`, `retired review
  and telemetry adapters stay absent from production main`, `infrastructure modules
  do not retain infra-fs area source sets or verification task`, `boundary decisions
  record raw-map enforcement supersession`, `legacy scaffold service forbidden
  top-level declaration regex ...`.
- Count pins: `inventory lists nineteen enforceable rules ...`.

`../../../runtime-kotlin/ARCHITECTURE.md` is 1,917 lines, much of it ticket history
("SKILL-52.2 subtask 5 adds ..."), partly because tests pin its wording.

Fix: delete these tests and assertions; rewrite the runtime-core and guardrail
sections of `ARCHITECTURE.md` as current state.

### F-008 Medium - baselines keyed by line number

Ambient-site baselines record `path:line:call`. SKILL-368's Spotless reformat
(`85209c086`) changed four runtime-core baseline files purely by shifting line numbers
(for example `FileTelemetryConfigStore.kt:45` to `:48`). Any edit above a
tolerated site churns a baseline and forces a re-record, and a new site can ride
along in the same re-record.

Fix: key rows by path and call text with an occurrence count, so a reformat
changes nothing and a new call changes the count. This changes only the existing
scanners' encoding.

### F-009 Medium - experiment wiring in the composition root

- `ExperimentGoalRunnerFactory` is declared in runtime-engine, has no engine
  consumer (the coordinator consumes only `ExperimentGoalRunnerPort`), and forces
  the engine to import the composition input `RuntimeContext`.
- `experimentGoalRunnerPort` builds a second `RuntimeComponent` for requests
  without an arm (reached when `skill-bill goal` gets an `experiments` parameter
  that selects nothing), duplicating the scoped database factory and worker
  supervisor. Plain goal runs call `GoalRunner` directly from `GoalCliCommands`.
- The arm database path is computed in `RuntimeExperimentProvides`
  (`armRoot.resolve(".skill-bill/runtime.db")`) and in
  `ExperimentPairCoordinator.statePaths` (`armWorktree.resolve(".skill-bill/runtime.db")`).

Owner: SKILL-378 subtask 1 deletes experiment support, including runtime-core
`di/experiment/**` and `experimentGoalRunnerFactory`, which removes all three
defects. This bundle does not fix them separately. Had experiments stayed, the
fix would be: delete the factory, inject `GoalRunner` for non-arm requests (no
cycle: `GoalRunner` does not depend on the experiment port), and give the arm
database path one engine owner.

### F-010 Low - test ownership and orphan packages

| Test | Subject | Owner |
| --- | --- | --- |
| `DecompositionManifestValidationTest` (379) | contracts validator + workflow file store | runtime-infra:contracts (has the infra:workflow test dep) |
| `SchemaValidatorPortLoudFailTest` (239) | contracts validators + workflow file store | runtime-infra:contracts |
| `TelemetryReleaseAttributionTest` (54) | SQLite outbox | runtime-infra:sqlite |
| `GoalRunnerControlBindingArchitectureTest` (25) | `UnavailableGoalRunnerControlRepository` | runtime-ports |
| `InstallerShell*` 4 files (1,670), `GoalRuntimeDelegationParityTest` (100) | `../../../install.sh`, `uninstall.sh`, launcher stubs | runtime-cli `repoTest` |

`InstallerShellDelegationTest.installer delegates install application to durable
installed runtime` asserts 29 substrings of `../../../install.sh`; the other 26 installer
tests execute the scripts.

The remaining runtime-core tests exercise a service with two or more real
adapters or the component itself. They stay, under `skillbill.di.*` packages.
`ReviewContextSchemaValidatorTest` (737 lines) is one of them: it validates
`ReviewPreparationService` (application) output with the infra-contracts schema
validator, so moving it to runtime-infra/contracts would need an upward
application test dependency.

### F-011 Low - composition inputs and test hooks declared in runtime-ports

`RuntimeContext`, `TransportContext`, `WorkflowOpsContext`, and `OptionalCallbacks`
live in runtime-ports. Their main readers are runtime-core, runtime-cli,
runtime-mcp, and the engine factory from F-009; test readers are runtime-core (8
files), runtime-cli (2), runtime-mcp (1). `EnvironmentContext` is read by 13
infrastructure files and stays. Production entry points set none of the ten
override hooks; `OptionalCallbacks.reviewNativeAgentPreflight` has no user.

Fix: move the four types into runtime-core `skillbill.di.core` in the same commit
that removes the engine reader; delete `reviewNativeAgentPreflight`.

### F-012 Low - small composition defects

- `skillBillVersion(): String` binds a bare `String`; its one consumer is
  `SystemService(versionValue: String)`. `McpProtocolFramer` reads the global
  `SkillBillVersion.VALUE`. SKILL-372 subtask 3 adds a third consumer: it passes the
  version from `RuntimeBootstrapBindings` into `SQLiteDatabaseSessionFactory`. The
  typed value therefore belongs in runtime-ports, which application and
  infrastructure both see; runtime-application would be unreachable from SQLite.
  `McpProtocolFramer` keeps reading `SkillBillVersion.VALUE`: `GovernedReviewEvidenceBridge`
  calls `McpProtocolFramer.initialize` without a component (SKILL-375 review), and the
  global is the documented packaged-metadata source.
- `featureSpecPreparationCore()` binds `FeatureSpecPreparationPolicy::prepare` as a
  function type. Its one consumer is `FeatureSpecPreparationRuntime`; the only test
  construction passes the same function (`FeatureTaskRuntimeRunnerTestSupport.kt:1209`).
  `parallelReviewParseRegister` has a seam test, and SKILL-370 keeps it.
- `goalPlanningBoundaryBodyResolver` evaluates `GoalPlanningDiscoveryExclusions.excludedRoots`
  as a discarded statement.
- The `{di.core, di.telemetry, di.workflow}` package cycle.
- Three accessors with no reader.

### F-013 Out of scope - recorded for owners

- CLI main consumes driven ports exported by runtime-core in 31 files
  (`ScaffoldGateway`, `WorkflowGitOperations`, `InstallerProcessPort`,
  `ExecutableLookup`, `RepoValidationGateway`). No sibling bundle owns this.
- Experiment support is 4,843 main lines across seven modules and cannot be
  selected in production (descriptor catalog bound to `null`; SKILL-365 removed in
  `17e63e1d9`; SKILL-367 not in the tree). SKILL-378 subtask 1 deletes it,
  including `FilesystemExperimentIsolationCapability`, whose check its only
  production caller always passes.

## Over-engineering register

| Item | Cost | Disposition |
| --- | --- | --- |
| 5 `GoalRunner*Dependencies` bags + 5 providers | 150 lines, 2 public types | Delete (F-004) |
| 137 `@JvmSynthetic` + vacuous rule | noise on every provider | Delete (F-005) |
| Standalone scaffold root + exemption list | test harness in the production jar, guard allow-list | Move to tests (F-006) |
| Two hand-listed module deny-lists | duplicate of the catalog pin | Delete (F-003) |
| Prose pins, migration guards, count pins | about 30 tests; block doc edits | Delete (F-007) |
| Line-number baseline keys | churn on every reformat | Re-key (F-008) |
| Per-module test methods for three rules | about 40 methods; already missed the engine | One iterating test per rule (F-001) |
| Engine-declared factory used only by composition | engine imports a composition input | Deleted by SKILL-378 (F-009) |
| `featureSpecPreparationCore` function binding | indirection with no substitute | Delete (F-012) |

## What stays unchanged

| Item | Reason |
| --- | --- |
| kotlin-inject, one `@RuntimeSingleton` component, per-area mixins | SKILL-350 retention; compiles and has consumers |
| The accessor surface and its curated inventory | Accessors are the export mechanism for child components; SKILL-350 kept the inventory as the logical service API; only the 3 unread accessors go |
| One exact topology in `RuntimeModuleCatalog` with rejection tests | SKILL-350 decision; independent actual-versus-expected evidence |
| No separate test module | SKILL-350 decision and this bundle's constraint; `repoTest` is a source set |
| 25 hand-constructed adapters in providers | Explicit construction is normal in a composition root; converting to `@Inject` changes nothing observable |
| Concrete application and engine services as the inbound API | One implementation per use case; an interface would be a same-module forwarder |
| Constructor `@Inject` in inner layers, `@RuntimeSingleton` in runtime-application | JSR-330 convention (Dagger, Anvil); no runtime behavior outside the component |
| `UnresolvedRemoteTransportPortError` | Loud-fail on a broken bootstrap invariant, per AGENTS.md |
| Experiment ports and bindings | Not this bundle's to change; SKILL-378 subtask 1 deletes them. Had they stayed, the isolation port and outbox sink would stay on their test substitutes |
| Ambient rules covering infrastructure, CLI, MCP | The rows inventory where ambient reads live; narrowing the scope loses that |
| `(String) -> ParallelReviewParseResult` binding | Seam test; SKILL-370 keeps it |
| Burst schedule, review broker factory | SKILL-350 retention |
| Module name `runtime-core` | SKILL-350: the name is not evidence of misplacement; a rename churns six sibling bundles |
| `EnvironmentContext` sentinels | Changing them splits the type; no defect demonstrated |
| Konsist or ArchUnit | SKILL-350 rejected a scanner framework; reconsider only if retained scan support stays above 2,000 lines |
| Multi-adapter integration tests in runtime-core | They are composition tests |
| Validator instance counts | Stateless; not an observable outcome |

## Coordination with concurrent bundles

| Bundle | Overlap | Owner | Sequencing |
| --- | --- | --- | --- |
| SKILL-368 build-logic (in progress on this branch) | `85209c086` re-recorded four runtime-core baselines, including four engine clock rows that SKILL-378 removes by deleting their code | SKILL-368 | SKILL-373 starts after SKILL-368 merges |
| SKILL-370 runtime-application | Adds a `TimeSource` binding in runtime-core; extends `InjectConstructorDefaultsArchitectureTest`; edits `RuntimeModuleCatalog`; keeps the review-parse binding. Its summary of SKILL-373 is stale: the suite does not leave runtime-core (it moves to `src/repoTest`), and no relocated test enters runtime-application | SKILL-370 | SKILL-373 after SKILL-370 |
| SKILL-371 runtime-cli | Subtask 1 repairs vacuous scanners. It dropped the repairs of tests this bundle deletes (`ImplementationOwnershipArchitectureTest` package checks, `RuntimeLayerBoundaryArchitectureTest` retired adapters) and now runs after SKILL-378 subtask 1 | SKILL-371 repairs; SKILL-373 deletes | SKILL-378 subtask 1, then SKILL-371 subtask 1, then SKILL-373 subtask 2 |
| SKILL-372 runtime-domain | Moves `WorkflowSnapshotValidator` out of domain (runtime-core provider import changes). Subtask 3 passes the version from `RuntimeBootstrapBindings` into `SQLiteDatabaseSessionFactory` and changes the acyclicity algorithm to exact-package SCC for runtime-domain. Its text assumes SKILL-373 collapses `ApplicationPackageAcyclicityArchitectureTest`; this revision does not, so 372 applies its change inside the current class | SKILL-372 for the algorithm and the SQLite version input; SKILL-373 for the typed version value | Either order: if SKILL-373 subtask 1 lands first, 372 passes the runtime-ports version type; otherwise 373 converts 372's `String` parameter |
| SKILL-374 runtime-contracts | Deletes the `GoalPlanningDiscoveryExclusions` object, and with it the discarded statement in `RuntimeGoalPlanningProvides.kt` (references updated to F-012); both of its subtasks land before this bundle's subtasks 2 and 3; edits `PrincipleEnforcementInventory`, `WireVocabularyGovernedSeamInventory`, `RuntimeArchitectureTest`, `ApplicationPackageAcyclicityArchitectureTest` | SKILL-374 | SKILL-374 before SKILL-373 subtasks 2 and 3; the second to reach the statement skips it |
| SKILL-375 runtime-mcp | Deletes `McpRuntime.version` and the reflection shim. SKILL-373 no longer touches `McpProtocolFramer`, because the governed-review bridge calls it without a component | SKILL-375 | Either order |
| SKILL-376 runtime-infra | Subtask 2 moves goal-runner coordination out of the two SQLite stores and edits `RuntimeGoalRunnerStoreProvides`; subtask 3 repairs the infra package-cycle prefix and closes every newly visible cycle by moves except `nativeagent ↔ scaffold`, which needs a design and is recorded as one shrink-only row. That is one row in a baseline the broken guard never checked, not growth of a working baseline. Subtask 1 adds `System.currentTimeMillis` to `AMBIENT_CLOCK_FORMS` with zero rows | SKILL-376 for the stores, the prefix repair, and its baseline row; SKILL-373 for the bags | 376 subtask 3 before SKILL-373 subtasks 2 and 3. 373 subtask 1 and 376 subtask 2 in either order; the second applies one rule: one `@Inject` binding per port, an accessor when a child needs it, no bag. Subtask 2 here carries 376's row and clock form forward unchanged |

| SKILL-377 runtime-ports | Subtask 3 deletes never-injected ports and their runtime-core bindings, including `installedWorkspaceBaselineStatusPort` (one of F-012's unread accessors), and repairs the scan roots of `PortsDeclarationArchitectureTest`. Subtasks 2 and 3 wait on SKILL-373 subtask 2 (guard pruning); if the suite is still in `src/test`, they edit in place and subtask 3 here moves the edits. Subtask 3 repairs the two vacuous guards this bundle keeps. The `installedWorkspaceBaselineStatusPort` accessor is deleted by SKILL-373 subtask 1; 377 deletes the port, binding, and adapter. Its stale references are fixed | SKILL-377 for ports and bindings | SKILL-373 subtask 1 deletes only the accessors that remain |
| SKILL-378 runtime-engine | Subtask 1 deletes experiment support across seven modules, including runtime-core `di/experiment/**` and `experimentGoalRunnerFactory` (it cites this bundle's old "F-6"; now F-009), and the engine sites behind F-001. It injects `Clock` at the three remaining engine clock sites without adding a clock guard, and adds engine to the filesystem-IO, null-object, and raw-map rules. Its subtasks 2 and 3 extend SKILL-370's inject-property rule to engine and move its step-class rule into `RuntimeEngineBoundaryArchitectureTest`; `FeatureTaskRuntimeParameterBagArchitectureTest` and `FeatureTaskRuntimeRunLoopContextExtensionCensusArchitectureTest` pin the procedural form and are deleted by whichever bundle lands first. It also expects a `runtime-architecture` module that this revision does not create; the suite stays in runtime-core (`src/repoTest` after subtask 3) | SKILL-378 for experiments and engine rules; SKILL-373 for iteration and the move | SKILL-378 subtask 1 runs right after SKILL-368, before SKILL-370, and before SKILL-373 subtask 1 |

SKILL-371 subtask 1 names `RuntimeApplicationSharedEngineEdgeArchitectureTest`; it
is a class inside `RuntimeEngineBoundaryArchitectureTest.kt`, which this bundle
keeps.

Cross-bundle order (verified against every sibling's stated dependencies; no
cycle):

1. SKILL-368 merges.
2. SKILL-378 subtask 1.
3. SKILL-370.
4. In parallel: SKILL-371 subtask 1 (after SKILL-378 subtask 1), SKILL-372
   subtask 1, SKILL-374, SKILL-376 subtask 1, SKILL-373 subtask 1.
5. SKILL-376 subtasks 2 and 3; SKILL-377 subtask 1 (after SKILL-376 subtask 1).
6. SKILL-373 subtasks 2 and 3 (after SKILL-371 subtask 1, SKILL-374, SKILL-376
   subtask 3, SKILL-378 subtask 1).
7. SKILL-377 subtasks 2 and 3 (after SKILL-373 subtask 2), SKILL-375, and the
   remaining SKILL-371, SKILL-372, and SKILL-378 subtasks, each rechecking anchors.

## Limits and what only compiling can confirm

- Generated-code reader counts come from `build/generated/ksp` built at 17:50 on
  2026-09-22; regenerate before deleting accessors.
- That the CLI child reads the two new store accessors instead of inlining
  providers (F-004).
- The exact number of engine sites the restored rules report after SKILL-378
  subtask 1 (F-001).
- That moving the suite to `repoTest` keeps every support import resolving.
- The 321-test classification is heuristic by name and body; subtask 2 applies
  the survival rule test by test and records each deletion.
- No tests were run for this investigation.
