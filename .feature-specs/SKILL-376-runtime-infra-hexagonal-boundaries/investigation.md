# Runtime-infra hexagonal boundary investigation

## Judgment

Keep the seven `runtime-infra` Gradle modules, their names, and the production
dependency direction. Every infra module depends inward on `runtime-ports`,
`runtime-domain`, and `runtime-contracts` only. No infra production file imports
`skillbill.application`, `skillbill.engine`, `skillbill.di`, `skillbill.cli`, or
`skillbill.mcp`. Sibling edges form a DAG (`host` and `contracts` are leaves;
`skills` → `contracts`, `host`; `launcher` → `skills`, `host`; `workflow` →
`skills`, `contracts`, `host`; `http` and `sqlite` stand alone). No package is
split across modules. JSON-Schema compilation has one owner
(`ClasspathContractSchemaLoader`). Only 8 interfaces are declared in infra, and
83 `@Inject` classes are wired by the container; only one file constructs them by
hand. That structure meets the bar and stays.

The problems are inside the modules, and several are regressions of fixes that
earlier specs recorded as done:

1. The SQLite adapter holds goal-runner application logic: 3,877 lines, 0 SQL
   statements, coordinating ports plus git, worker supervision, child repair, and
   filesystem projections. It is fed by an 11-field dependency bag that SKILL-356
   was specified to remove and that its own commit introduced.
2. Process lifetime is implemented three times. The newest copy, added after
   SKILL-353 declared "one shared owner", hangs on verbose output.
3. The infra package-cycle guard cannot fail. Its package prefix puts every file
   in one area, so it hides 8 real package cycles.
4. Smaller defects: SQLite has an identical second copy of the review-accounting
   encoder, a lease check reads the ambient wall clock, three validator sites turn
   discovery failures into empty input, and several over-engineered carriers or
   hooks exist for no consumer.
5. Package layout, in main and in tests, is fragmented beyond anything the
   sibling ceiling requires.

None of the fixes needs a new module, framework, abstraction layer, dependency
bag, or architecture-test class. The fixes are moves, deletions, one bug fix
through the existing process owner, and a one-line guard correction.

## Method and baseline

- HEAD `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b` on
  `feat/SKILL-368-build-logic-architecture-cleanup`. Other sessions have
  uncommitted edits to build-logic, engine, and ambient baselines. No infra
  production file was modified.
- Sorted infra production-file digest:
  `1d7116d14737dedd9d6995d657bd997fc8a7a7ff4b47e876dd1a16f17b40351f`.
- Read: `CLAUDE.md`, `runtime-kotlin/ARCHITECTURE.md` (Design Principles,
  Gradle Modules, Package Ownership, Guardrails), `docs/code-principles.md`,
  `runtime-kotlin/runtime-infra/agent/history.md`,
  `runtime-infra/sqlite/agent/history.md`, the infra entries in
  `runtime-kotlin/agent/decisions.md` (2026-09-18 SKILL-354 split, 2026-09-18
  SKILL-356 fixtures, 2026-09-17 SKILL-353, 2026-06-26 WAL, 2026-09-03 ambient
  baselines), and the prior infra investigations SKILL-353 (fs), SKILL-354
  (split), SKILL-356 (sqlite), and SKILL-359 (http), plus SKILL-361
  (package nesting).
- Censuses are Python and grep scripts over `src/{main,test,testFixtures,repoTest}`
  and every build script. No delegated review. Full reads only of the files each
  finding cites.
- Every guard this bundle relies on was opened and its scan root resolved (see
  Guard validity).

## Census

### Size

| Module | Main files | Main lines | Test+fixture+repoTest files | Public decl. | Internal decl. | Public, no reader outside module |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| host | 24 | 1,822 | 8 | 46 | 15 | 1 |
| contracts | 43 | 5,858 | 56 | 40 | 74 | 13 |
| skills | 242 | 26,177 | 122 | 102 | 894 | 39 |
| launcher | 36 | 4,216 | 20 | 26 | 120 | 19 |
| workflow | 69 | 7,753 | 40 | 28 | 179 | 5 |
| http | 8 | 667 | 6 | 4 | 16 | 0 |
| sqlite | 158 | 21,449 | 53 | 13 | 521 | 1 |
| **total** | **580** | **67,942** | **317 (63,651 lines)** | | | |

### Build edges

| Module | `implementation` (project) | `api` | test-only project edges |
| --- | --- | --- | --- |
| host | ports, domain, contracts | — | testFixtures(ports) |
| contracts | ports, domain, contracts | — | testFixtures(ports), testFixtures(host), **workflow** |
| skills | ports, domain, contracts, infra:contracts, infra:host | — | testFixtures(ports), testFixtures(host), **workflow** |
| launcher | ports, domain, contracts, infra:skills, infra:host | — | testFixtures(ports), testFixtures(host) |
| workflow | ports, domain, contracts, infra:skills, infra:contracts, infra:host | — | testFixtures(ports, host, skills) |
| http | domain, ports, contracts | — | — |
| sqlite | domain, ports, contracts | — | **runtime-application** |

No infra module declares `api`. That is right: no infra type appears in another
module's public ABI except through `runtime-core` composition. Bold edges point
backwards and are findings (F-008).

Unused declared dependencies (import census per source set):

- `runtime-application`: `testImplementation` of infra host, contracts,
  workflow, sqlite, and `testFixturesImplementation` of infra sqlite (0 imports)
- `runtime-engine`: `testImplementation` of infra host, skills (0 imports)
- `infra:workflow`: `libs.json.schema.validator` (0 `com.networknt` imports)
- `infra:launcher`: `libs.jackson.dataformat.yaml` (0 imports)

### Cross-module consumers (main)

| From → to | Symbols | Examples |
| --- | ---: | --- |
| skills → contracts | 6 | `ClasspathContractSchemaLoader`, `sha256Hex` |
| skills → host | 16 | atomic writes, rollback helpers, telemetry-config file I/O |
| launcher → host | 7 | `BoundedExternalProcessRunner`, `GateJvmResolver` |
| launcher → skills | 9 | MCP config codec, native-agent link inventory |
| workflow → contracts | 5 | two schema validators, digest helpers |
| workflow → host | 21 | process-tree cleanup, deadline constants, atomic writes |
| workflow → skills | 6 | link inventory, platform-pack discovery |
| runtime-core → infra | 7 edges | composition only |

Symbols used by exactly one other module are the MCP config codec and link
inventory (launcher) and the platform-pack discovery (workflow). Each has one
owner in `skills`. The consumers read them and do not reimplement them.

### Interfaces

| Interface | Main impls | Test substitutes | Verdict |
| --- | ---: | ---: | --- |
| `AgentRunProcessRunner` | 1 (+2 wrappers) | 1 | keep |
| `AgentRunCommandBuilder` | 4 | 1 | keep |
| `AgentRunActivityProbe` (fun) | 1 | 3 | keep |
| `AgentRunIdlePolicy` (fun) | 3 constants | 0 | keep (strategy constants, per CLAUDE.md) |
| `AgentRunOutputDecoder` | 2 objects | 0 | keep (Cursor stream decoder vs plain) |
| `NativeAgentPlatformPackLoader` | 4 | 0 | keep |
| `AgentAddonSchemaResourceLoader` (fun) | 1 | 1 | keep |
| **`AgentRunAdapter`** | **1** | **0** | **delete (F-007)** |

### Typealiases

| Alias | Target | Uses | Verdict |
| --- | --- | ---: | --- |
| `FeatureTaskRuntimePhaseOutputSchemaValidator` (contracts `…runtime.phase`, internal, L103) | `FeatureTaskRuntimePhaseOutputWireSchema` | inside its package | delete. It shadows the real class `skillbill.infrastructure.contracts.FeatureTaskRuntimePhaseOutputSchemaValidator` one package up, so the same name means a schema inside that package and a validator everywhere else. |
| `WorkflowStateRow` (sqlite, internal) | port `WorkflowStateRecord` | 3 files | delete |
| `TelemetryOutboxRow` (sqlite, internal) | port `TelemetryOutboxRecord` | 1 file | delete |
| `DirName`/`DirSlug`/`DirTarget`, `TrackedRepoFilesProvider`, `AuthoringTargetRenderer` (skills) | local `Pair` or function types | local | keep; they name tuple roles |

### Dependency bags, getters, and hand construction

- `WorkflowGoalRunnerOutcomeStoreDependencies` is the only `@Inject` class
  exposing constructor parameters as non-private `val`s (11 of them)
  (`sqlite/goalrunner/outcome/WorkflowGoalRunnerOutcomeStoreDependencies.kt:15`).
- `runtime-core` `RuntimeGoalRunnerStoreProvides` declares five more
  `GoalRunner*Dependencies` bags to fill it. SKILL-373 owns those.
- Hand construction of `@Inject` classes: 2 of 83, both in
  `skills/…/scaffold/runtime/service/standalone/ScaffoldStandaloneEntrypoint.kt:16-17`,
  a test-only second composition root. SKILL-373 moves it to test sources.

### Ambient effects (main)

| Kind | Sites | Verdict |
| --- | --- | --- |
| `System.nanoTime` | 25 in process runners, wait loops, validation-gate timing | keep; monotonic deadlines are not `Clock` values |
| `System.currentTimeMillis` | `sqlite/experiment/SqliteExperimentPairStore.kt:245` (lease expiry), `launcher/…/JvmAgentRunProcessOutputDrain.kt:228` (change token) | fix (F-005) |
| `Instant.now`/`*.now()` | 0 | clean |
| `System.getenv`/`getProperty` | 6 files: `JdkHostPlatformPort` (the seam), two config stores, `PathExecutableLookup`, `AgentRunCommandBuilders` | recorded in the shrink-only ambient-environment baselines (decision 2026-09-03) |
| Threads | 14 files, all in host JDK ports, process runners, or the review endpoint | keep |

### State

Mutable class-level state appears only in per-invocation session objects
(process sessions, wait loops, review-evidence broker per launch, parsers), with
two exceptions:

- `GhGoalPullRequestPort` (`workflow/git/goal/GhGoalPullRequestPort.kt:14-17`),
  a DI-bound adapter, holds `private var ghExecutableResolver` so that an
  internal test constructor can overwrite it. That is a test-only mutable hook
  in production.
- `DatabaseRuntime` (object) declares `private var writeReadinessGate` and never
  reassigns it. It should be `val`; it is folded into the subtask that touches
  the file.

### Error model

- `catch (_: …)`: 36 sites. Each converts a named exception into a typed error,
  a finding, or an explicit "absent" value.
- `catch (e: Exception)`: 3 sites. Two rethrow `CancellationException` first and
  convert to a typed finding; one rethrows a typed schema error.
- `runCatching` swallowing to `null`/default with no record: 52 sites. 49 are
  probes where absence is the answer (`toRealPath`, `relativize`, exact-number
  narrowing, optional file reads). Three are validator input discovery (F-006).

### Wire vocabulary and raw maps

- Status-token literals: 29 in main, 26 of them in SQLite. 19 sit in
  `sqlite.goalrunner` (they move with F-001). `ReviewWorkflowStats.kt:21-26`
  holds local lists of telemetry outcome tokens with no owning enum.
- Public `Map<String, Any?>` signatures: 29, all at adapter parse or persist
  seams (schema validators, payload mappers, stores).
- JSON/YAML parse sites: 59, all in adapters.

### Package layout

| Module | Main packages | Single-file | Stutter paths | Orphan test packages (files) |
| --- | ---: | ---: | --- | --- |
| host | 5 | 2 | — | 0 |
| contracts | 18 | 11 (61%) | `contracts.workflow.workflow` | 12 (16) |
| skills | 49 | 13 (26%) | `install.staging.staging.*` (5), `install.nativeagent.install.*` | 6 (30) |
| launcher | 7 | 2 | — | 3 (18) |
| workflow | 18 | 5 (27%) | — | 9 (10) |
| http | 1 | 0 | — | 0 |
| sqlite | 60 | 33 (55%) | `workflow.workflow`, `telemetry.lifecycle.telemetry.*` | 8 (9) |

An orphan test package is one with no production package of the same name.
Examples: `launcher.launcher` (15 files), `sqlite.review.stage.and`,
`sqlite.review.stats.recorded`, `contracts.workflow.featuretask.phase.task.runtime.buildreceipt`,
and `skillbill.scaffold` (22 repoTest files, the pre-SKILL-354 name).

## Principle assessment

| # | Question | Answer and evidence |
| --- | --- | --- |
| 1 | Dependency direction inward; `api` justified? | Clean. 0 upward imports in main; 0 `api` edges; sibling DAG as above. Test scope has 3 backward edges (F-008). |
| 2 | Inbound side: adapters call use cases, reach into service getters, re-decode domain data? | Infra has no inbound adapters. It re-decodes domain data in two places: `sqlite.decomposition.decodeArtifacts` (8 imports, owned by SKILL-372 subtask 1) and the duplicate accounting encoder (F-004). |
| 3 | Outbound ports purpose-built or generic transports with vendor protocol inward? | Ports are purpose-built; no port imports JDBC, HTTP, Jackson, networknt, or SnakeYAML. One protocol leak originates here: SQLite passes the driver's `[SQLITE_BUSY]` text through untyped, so application (`SelfManagedWriteBusyRetry.kt:28`) and engine (`FeatureTaskRuntimeRunLoopPhaseRunner.kt:207,209,435`) match message text. SKILL-370 moves the retry into SQLite; see Coordination. |
| 4 | Domain rules sitting in adapters; duplicated rules? | Yes. Goal-runner policy (crash reconcile, stale-block displacement, scoped replan, lease accounting) lives in `sqlite.goalrunner` (F-001). The accounting encoder is duplicated byte-for-byte (F-004). |
| 5 | Second composition root, bag, or service locator outside runtime-core? | `WorkflowGoalRunnerOutcomeStoreDependencies` (F-001); `ScaffoldStandaloneEntrypoint` (SKILL-373). No service locator. |
| 6 | Entry-point names or text inside use cases? | Infra has no use cases. It renders 25 CLI command hints (21 in scaffold: `renderCommand`, `recommendedCommands`, `REPAIR_COMMAND`) in port results that CLI and MCP both show. They stay (see What stays). |
| 7 | Ambient effects not injected? | 2 wall-clock reads (F-005). Monotonic time and env reads sit at JDK seams or in shrink-only baselines. |
| 8 | Mutable state on long-lived objects; transaction ownership; unit-of-work shape? | `GhGoalPullRequestPort` test hook (F-007). Transactions: `SQLiteDatabaseSessionFactory` owns them; `sqlite.goalrunner` opens them for use-case scripts, which is the engine's job (F-001). `UnitOfWork` shape stays (SKILL-356 rejected per-use-case sessions). |
| 9 | Typed errors, broad catches, cancellation? | Mostly clean (see Error model). Three validator swallows (F-006). |
| 10 | Code or tests used only by another module; consumed API shape? | Two tests belong in `workflow` (F-008). Two SQLite tests build payloads through application mappers (F-008). Consumers use services and ports; `skills` exports free functions to `launcher`/`workflow` (9 and 6 symbols), which is acceptable at that size. |
| 11 | YAGNI: aliases, test-only members, dead code, forwarders, interfaces without substitutes? | `AgentRunProcessRequest` in four forms, `AgentRunAdapter`, three typealiases, the `GhGoalPullRequestPort` hook, nine unused dependency declarations (F-007, F-008). |
| 12 | Stutter, orphan test packages, sibling limits? | Yes (F-009). All packages are ≤12 files; fragmentation is the problem, not size. |
| 13 | Guard validity | The infra package-cycle guard is vacuous (F-003). The others this bundle cites read files (see Guard validity). |

## Findings

### F-001 (P1) — Goal-runner coordination lives in the SQLite adapter

| `runtime-infra/sqlite` root | Files | Lines | SQL statements | `java.sql` files | `UnitOfWork` files |
| --- | ---: | ---: | ---: | ---: | ---: |
| `sqlite.goalrunner` | 20 | 3,877 | 0 | 0 | 13 |
| `sqlite.workflow.goalrunner` | 19 | 2,246 | 35 | 11 | 0 |
| `sqlite.core` | 29 | 4,164 | 147 | 23 | 0 |
| `sqlite.telemetry` | 28 | 3,023 | 58 | 21 | 0 |
| `sqlite.review` | 28 | 3,543 | 43 | 17 | 0 |

Every other SQLite root issues SQL; `sqlite.goalrunner` issues none. It opens
`database.transaction { unitOfWork -> … }` through the `DatabaseSessionFactory`
port, as 50 engine and application files already do. Its collaborators are not
persistence:

- `WorkflowGoalRunnerOutcomeStore` implements `GoalRunnerWorkflowOutcomeStore`,
  `GoalRunnerAttemptLedgerStore`, and `GoalRunnerChildRepairStore`. It takes the
  11-field `WorkflowGoalRunnerOutcomeStoreDependencies` bag: `WorkflowGitOperations`,
  `FeatureTaskRuntimeWorkerSupervisor`, `GoalRunnerChildRepairRunnerPort`, the
  decomposition file store and projection writer, four validators, and `Clock`.
- `WorkflowGoalRunnerManifestStore` takes the file store, projection writer,
  `RepositoryRoot`, and `GoalChildPlanningHydratorPort`, an engine capability
  reached back through a port.
- `GoalRunnerControlCoordinator` runs manifest reconciliation inside lease
  transactions. The lease rules are six public extension functions on the
  `GoalRunnerControlRepository` port (`GoalRunnerControlRepositoryExtensions.kt`).

**Prior-fix check.** SKILL-356 subtask 2 required
`WorkflowGoalRunnerOutcomeStore` to take "its collaborators … in its `@Inject`
constructor" with the bag deleted, and required SQLite to keep only four public
types. The commit that shipped it (`a2d349261`) added
`WorkflowGoalRunnerOutcomeStoreDependencies`. The module now has 13 public
declarations: the four, the bag, six lease extensions, a duplicate encoder
extension (F-004), and `SqliteExperimentPairOwnerStore` (SKILL-366). SKILL-356
kept the goal-runner stores in SQLite because SKILL-354 owned module layout. It
did not argue that the code belongs there.

**Feasibility.** Imports of the 20 files, checked against the destination:

- Ports, domain, contracts, `java.time`, `java.nio`: allowed in runtime-engine.
- 33 imports of SQLite internals:
  - The `sqlite.workflow.decomposition` lookups and `sqlite.decomposition`
    helpers use no JDBC and no `UnitOfWork`. `decodeArtifacts` goes to its
    SKILL-372 subtask 1 owner.
  - The `sqlite.featuretask.artifact` codecs already forward to domain
    `skillbill.workflow.taskruntime.artifact` (`phaseRecordsFromWorkflowArtifacts`,
    `phaseLedgerFromWorkflowArtifacts`), so the moved code calls those.
    `resolveDecompositionManifest` imports `java.nio.file.Path` and three ports
    types, so it cannot go to domain. It is an identical twin of application's
    copy (`DecompositionManifestRuntimeState.kt:88`) and the moved code calls
    that one (correction from the SKILL-372 session).
- Twins. 36 top-level functions in the moved files and their SQLite helpers
  share a name with a function already in runtime-engine or runtime-application.
  A normalized body diff (verification pass, HEAD `85209c086`) splits them:
  - **20 identical.** Examples: `withParentStatus`, `findDecomposedParentWorkflow`,
    `requireRuntimeModeForEngineWrite`, `resolveDecompositionManifest`,
    `selectAuthoritativeOutcome`, `workflowFamilyFor`, `taskRuntimeRecordOrNull`,
    `pauseAtOperatorBoundary`.
  - **16 diverged.** Examples: `goalContinuation`, `goalReviewArtifacts`,
    `validatedGoalReviewPasses`, `goalRepositoryIdentity`, `decompositionRuntime`,
    `hasDecompositionPlan`, `findMatchingDecompositionManifests`,
    `persistDecompositionManifestProjectionFailure`.

  Three whole files already exist in engine under the same name:
  `GoalContinuationArtifactCodec.kt`, `GoalParentProjectionWriter.kt`, and
  `GoalRepositoryIdentity.kt`. Moving the package as-is would put a second
  copy next to the first. The rule:
  - For an identical twin, delete the SQLite copy and call the existing
    function. That is the engine one, or the application one (engine depends
    on application), or the domain one once SKILL-370 subtask 2 has moved it
    (`withParentStatus`).
  - For a diverged twin, keep the SQLite body as a `private` function of the
    moved file and do not reconcile it. Reconciling changes behavior, and
    SKILL-372 subtask 2 owns it: its own text says the collapse is "recorded as
    remaining work for SKILL-376" if the move comes first. List the diverged
    twins in the commit body.
  - SKILL-371 subtask 3 replaces `goalRepositoryIdentity` with the repository
    identity port.
  - `recordDegradedValue`/`InternalSqliteDiagnostics` becomes the engine's
    injected `RuntimeDiagnostics`.
  - `generateWorkflowId` becomes the existing `IdentifierGeneratorPort` or its
    domain rule.
- Reverse users that keep code in SQLite:
  - `reviewPolicyFromLegacyArtifacts` and `outOfBandAcceptancesFromLegacyArtifacts`
    are used only by `core/migration/goal/LegacyGoalRunnerControlLedgerMigration.kt`,
    so they move next to that migration.
  - `clearRunnerInterruptedPauseState` is used only by SQLite
    `GoalRunnerControlStore.kt:41`, so it stays beside it.
- Engine public surface: `RuntimeEngineBoundaryArchitectureTest` (L79) checks
  the real engine tree with `includeDefaultPublic = false`, so it flags only
  declarations written with an explicit `public` modifier. The moved classes
  are default-public, like the other engine types runtime-core imports (for
  example `ExperimentTelemetryOutboxSink`, which is not pinned), so they pass
  as written. SKILL-378 subtask 3 sets `includeDefaultPublic = true` and makes
  every engine type internal unless another module needs it. Whichever of the
  two lands second keeps the two classes runtime-core binds public and pins them. They replace two public SQLite types, so the
  product-wide public count does not grow.
- DI: both classes keep `@Inject` constructors. kotlin-inject generates their
  construction in runtime-core's component from the concrete types, exactly as
  today. SKILL-373 removes the five core bags and binds through accessors.

**Fix.** Move the package into `runtime-engine` under `skillbill.engine.goalrunner`.
Keep the four port interfaces, so the 30 engine and application consumers and
their fakes do not change. Delete the bag. Tests (5,184 lines in SQLite) move
with their subject and use `:runtime-infra:sqlite` test fixtures for a real
database. SKILL-378 subtask 3 then folds the moved classes into its step-class
rule, and its follow-up collapses the child-repair and hydrator ports.

### F-002 (P1) — Three process lifetimes and a pipe-blocking PR-check runner

| Site | Deadline | Kills tree | Drains output concurrently |
| --- | --- | --- | --- |
| `host/process/BoundedExternalProcessRunner.kt` (283 lines) | yes | yes | yes |
| `workflow/process/GitProcessInvocation.kt` `GitProcessBoundedLineSession` (L57) and `GitProcessSession` (L201), 411 lines | yes | yes | yes |
| `launcher` `JvmAgentRunProcessRunner` + wait loop | yes | yes | yes |
| `workflow/validation/FileSystemPrCheckProcessRunner.kt` | 30 min | **parent only** (L37) | **no** (L28 `redirectErrorStream(true)`, never read) |
| `host/jvm/GitTrackedFiles.kt` | ineffective (reads to EOF before `waitFor`) | parent only | — |
| `host/jvm/GateJvmResolver.kt` `runGuard` | 60 s | parent only | reads after `waitFor` |

**Prior-fix check.** SKILL-353 subtask 2 recorded "one shared owner across git,
gh, validation, review, and installer seams". The git sessions duplicate the host
runner's state machine because the host runner lacks stdin bytes and a line
sink. `FileSystemPrCheckProcessRunner` arrived with SKILL-364, after that claim.

**Bug.** `FeatureTaskRuntimeReadinessGateCoordinator.kt:435` calls the PR-check
runner in production. Once `bash -lc <command>` writes about 64 KiB, the child
blocks on the full pipe, `waitFor` runs 30 minutes, and the gate records exit
124. `destroyForcibly()` then kills `bash`, and its children survive. The port
returns only exit code and duration. The runner has no test.

**Fix.** Give `BoundedExternalProcessRequest` optional stdin bytes and a
streamed-line output mode. Route git, the PR check, tracked files, and the guard
through it, and delete both git sessions. `JvmAgentRunProcessRunner` stays: its
probes and idle policies are requirements the one-shot runner lacks. Move
`InstallerProcessAdapter` (launcher root, uses only `host.process`) to
`host.process`.

### F-003 (P1) — The infra package-cycle guard cannot fail

`PrincipleEnforcementInventory.packagePrefixForModule`
(`runtime-core/src/test/kotlin/skillbill/architecture/PrincipleEnforcementInventory.kt:44-54`)
returns `"skillbill."` for every infra module. `ArchitectureScanSupport.packageImportEdges`
(L668-682) takes the segment after the prefix as the area. For infra that segment
is always `infrastructure`, so every import is same-area and dropped, and
`ApplicationPackageAcyclicityArchitectureTest` compares an always-empty set with
empty baselines. The scan roots resolve correctly; the prefix is the defect, so
SKILL-371 subtask 1 (missing roots) does not cover it.

With prefix `skillbill.infrastructure.<module>.`, the real graph has:

| Module | Cycle | Forward / back edges (files) | Back-edge source |
| --- | --- | --- | --- |
| sqlite | core ↔ goalrunner | 2 / 2 | legacy migration; `GoalRepositoryIdentity` |
| sqlite | goalrunner ↔ workflow | 14 / 1 | `GoalRunnerControlStore` |
| sqlite | core ↔ telemetry | 25 / 10 | `DatabaseMigrationEntries` imports 5 area migrations; `StaleSessionReconciler` emits telemetry |
| sqlite | core ↔ workflow | 23 / 4 | `FEATURE_VERIFY_WORKFLOW_CONTRACT_VERSION`; `FeatureTaskPhaseSettlementsMigration` |
| sqlite | core ↔ review | 14 / 1 | `migrateLegacyTelemetryOutboxLedger` in `review.stats.health` |
| skills | install ↔ nativeagent | 37 / 1 | `FileSystemNativeAgentPlatformPackLoader` |
| skills | nativeagent ↔ scaffold | 26 / 1 | `FileSystemNativeAgentComposition` → `renderAuthoredContentBody` |
| skills | install ↔ scaffold | 28 / 8 imports in 3 files | `FileSystemScaffoldGateway`, `…InstallLink`, `…Orchestrator` call install operations |

**Fix.** Set the infra prefix to the module's own package root.

- The SQLite cycles close:
  - The goal-runner edges leave with F-001.
  - The five area migrations and `migrateLegacyTelemetryOutboxLedger` move
    into `core.migration`.
  - `StaleSessionReconciler` and its candidate query move to `sqlite.telemetry`.
  - `FEATURE_VERIFY_WORKFLOW_CONTRACT_VERSION` moves to `core.schema`.
- The skills cycles (simulated on the tree after the moves):
  - `install ↔ scaffold` has 8 back-edge imports, all in three files:
    `FileSystemScaffoldGateway`, `FileSystemScaffoldInstallLink`, and
    `FileSystemScaffoldOrchestrator`. These are the adapters that join
    authoring with installation. Nothing in `skills` references them; only
    runtime-core wires them. `FileSystemScaffoldInstallLink` is dead (its port is
    never injected; SKILL-377.3 deletes it at the new path). It still moves,
    because 376.3 lands first and leaving it would keep the scaffold → install
    edge. They move to the existing `skills.install.scaffold`
    package, and the edge becomes one-way.
  - `install ↔ nativeagent`: `InstallNativeAgentPlatformPackLoader` imports only
    nativeagent and `scaffold.platformpack` types, so it moves to
    `skills.nativeagent.platformpack`. The `nativeagent → install` edge goes.
  - `nativeagent ↔ scaffold` remains. `FileSystemNativeAgentComposition`,
    `FileSystemNativeAgentPlannedWorkerValidation`, and the moved pack loader
    need scaffold's platform-pack loading and authored-content rendering.
    Scaffold uses native-agent composition in 26 files. Breaking it means
    pulling platform-pack loading and the scaffold validators it calls into a
    shared kernel. The promotion test above shows that creates a new cycle, so
    it needs a design, not a move.
  - The skills baseline goes from a false empty file to this one true row. That
    corrects a record the broken guard never checked; it adds no tolerated debt,
    and the baseline stays shrink-only. SKILL-373 (no-baseline-growth
    constraint) was told the row and its reason.

### F-004 (P2) — Second copy of the review-accounting encoder in SQLite

`sqlite/review/accounting/ReviewAccountingBoundedSerialization.kt:11`
(`encodeReviewAccountingBoundedPayload`, 93 lines) is identical to
`runtime-domain/…/accounting/ReviewAccountingPayload.kt:5`
(`ReviewAccountingSummary.toBoundedPayload`) apart from the function signature.
Normalized diff: 77 lines each, one line differs.
`sqlite/review/accounting/ReviewAccountingWireExtensions.kt:4` adds a public
`ReviewAccountingSummary.toBoundedPayload()` that shadows the domain name; its
only outside reader is one runtime-core test. SKILL-371 subtask 1 keeps one
serializer for this shape. **Fix:** delete the SQLite copy and the shadow, and
persist through the serializer SKILL-371 keeps. Stored bytes stay identical.

### F-005 (P2) — Ambient wall clock in a lease check

`SqliteExperimentPairStore.kt:245` compares `expires_at` with
`System.currentTimeMillis()`. The rest of SQLite reads the injected `Clock`
(SKILL-356), so lease tests cannot control this one.
`JvmAgentRunProcessOutputDrain.kt:228` uses `currentTimeMillis()` as a change
token that is only compared for inequality. The ambient-clock guard's forms
(`ArchitectureScanGuardSupport.kt:20-29`) do not include `currentTimeMillis`, so
neither site is recorded. **Fix:** inject `Clock` into the pair store; use a
counter for the drain token. If SKILL-378 subtask 1 has already deleted the
experiment store, only the drain change remains. After both fixes, the repository
has no `System.currentTimeMillis()` call in production, so add that form to
`AMBIENT_CLOCK_FORMS` with zero baseline rows. The revised SKILL-373 keeps the
ambient rules on every module (narrowing them is its non-goal), so the form
enforces the criterion. SKILL-373 subtask 2 rewrites that guard's iteration and
baseline key after this lands.

### F-006 (P2) — Repo validation turns discovery failures into empty input

- `RepoValidationRuntimeRepoChecks.kt:156`:
  `runCatching { discoverSkillClasses(root) }.getOrDefault(emptyList())`
- `RepoValidationCollected.kt:31`: native-agent source discovery →
  `emptyList()`
- `RepoValidationRuntimeSkillDiscovery.kt:129`: `loadPlatformManifest` →
  `emptySet()`

A discovery exception makes the dependent checks run on nothing and report clean,
unless another check reports the same file. That contradicts the loud-fail
contract. **Fix:** per site, if a fixture shows the failure is already reported
elsewhere (for example by `validatePlatformPacks`), delete the redundant catch
and let that report stand. Otherwise, append the failure to `issues`.

### F-007 (P2) — Over-engineered carriers and hooks

1. `AgentRunProcessRequest` (`launcher/…/AgentRunProcessRequestFields.kt:55`)
   has six group classes, 25 forwarding getters, and `AgentRunProcessRequestDsl`
   (L120), a 25-`var` builder with one production caller. Tests add
   `testAgentRunProcessRequest`. 61 call sites already read through the groups.
2. `AgentRunAdapter` (`launcher/agentrun/AgentRunAdapters.kt:22`) has one
   implementation and no substitute. Per-agent variation already lives in
   `AgentRunCommandBuilder`.
3. `GhGoalPullRequestPort` holds a test-only mutable hook (see State).
4. Three pass-through typealiases (see Typealiases).

### F-008 (P3) — Dependency and test-ownership hygiene

- The nine unused declarations listed under Build edges.
- Two test-only reverse edges: `contracts` tests → `workflow` for
  `ReviewPacketConsumerContractParityTest`, and `skills` tests → `workflow` for
  `InstallNativeAgentLinkApplyCodexTest`.
- SQLite tests → `runtime-application`:
  `TriageAndLearningsRuntimeTest` and `ReviewStatsRuntimeTest` build their input
  with `learningAppliedSessionWire` and `learningEntryDto`. The adapter's tests
  should build input from port and domain types.

### F-009 (P3) — Package fragmentation in main and tests

SKILL-361 nested flat packages by noun family. In infra it overshot and split
single noun families into one-file packages. The 12-file ceiling does not
require any of these:

| Subtree | Packages → target | Files |
| --- | --- | ---: |
| `contracts.workflow.featuretask.**` | 6 → 1 | 10 |
| `contracts.workflow.goal.**` | 4 → 1 | 4 |
| `contracts.workflow.workflow` | → `contracts.workflow` | 2 |
| `workflow.review.specialists.**` | 4 → 1 | 11 |
| `sqlite.review.stage.**` | 7 → 1 | 10 |
| `sqlite.review.stats.**` | 5 → 1 | 12 |
| `sqlite.workflow.workflow` | → `sqlite.workflow` | 4 |
| `sqlite.decomposition`, `sqlite.featuretask.artifact` | into `sqlite.workflow.*` | 2 |
| `sqlite.telemetry.lifecycle.telemetry.**` | 12 → 2 | 15 |
| `sqlite.core.migration.**` | 8 → 2 | 15 (+ moved migrations) |
| `skills.install.staging.staging.**` | 5 → 2 | 13 |
| `skills.install.nativeagent.install.**` | 4 → 2 | 13 |

SKILL-361's criteria were "not a flat dump of mixed types" and the ceiling. A
`LifecycleTelemetry*` family split into twelve one-file packages named by suffix
meets neither better than two packages do. The targets above are measured at
baseline. Subtask 3 applies them as a rule, measured on the tree at start: the
fewest noun-family packages that keep each package at 12 files or fewer.
SKILL-374 subtask 2 puts its 34 `*SchemaPaths` objects in a new
`skillbill.infrastructure.contracts.schema` package, outside this list. `src/repoTest` suites check repository
content, not a production class. They keep their descriptive packages, and
SKILL-374 moves more of them into infra/contracts. The 83 orphan test files (38 packages)
move to the production package of the class they exercise. The co-location guard
(`PackageSiblingCountArchitectureTest.kt:281`) checks only that each test file's
directory matches its declared package. It does not require the package to
exist in main.

### F-010 (P3) — Architecture documentation drift

`runtime-kotlin/ARCHITECTURE.md` uses "`runtime-infra/host`, `runtime-infra/contracts`,
`runtime-infra/skills`, `runtime-infra/launcher`, and `runtime-infra/workflow`"
20 times where one module owns the thing. It is a find-and-replace remnant of
the old `runtime-infra/fs` module. Also stale: schema validators live in
`contracts` and `skills` only; `skillbill.agentaddon` is
`skillbill.infrastructure.skills.agentaddon`; `WorkflowGoalRunnerOutcomeStoreBridgeBuilder`
is listed as public SQLite surface and was deleted by SKILL-356.

## Over-engineering register

| Item | Cost today | Replacement |
| --- | --- | --- |
| `WorkflowGoalRunnerOutcomeStoreDependencies` | 11-field bag, public getters | constructor parameters |
| `AgentRunProcessRequestDsl` + 25 forwarders | ~130 lines restating one type | named arguments |
| `AgentRunAdapter` | interface with one impl | the class |
| git process sessions | ~350 lines duplicating the host runner | host runner options |
| SQLite accounting encoder copy + shadow extension | 97 lines | the kept serializer |
| three typealiases | second names for one type | the type's name |
| `GhGoalPullRequestPort` mutable hook | production `var` for tests | constructor value |
| one-file packages and orphan test packages | 44 main + 38 test packages | noun-family packages |

## What stays unchanged

- **Seven modules, names, DAG.** Measured split by SKILL-354 (decision
  2026-09-18). Nothing here contradicts it.
- **`host` as the shared JVM kernel.** It is 1,822 lines; splitting it adds edges
  and buys nothing.
- **`launcher` → `skills` and `workflow` → `skills` edges.** They read one-owner
  codecs and inventories.
- **Goal-runner port interfaces.** 30 consumers and their fakes use them as test
  seams. SKILL-378's follow-up collapses the two it names after the move.
- **`UnitOfWork` shape and per-operation connections.** Recorded persistence
  boundary. SKILL-356 rejected per-use-case sessions and connection pools.
- **`ScaffoldAdapterSeams`.** A function-typed seam with one production and one
  test construction. It is justified by the substitute.
- **Real adapters in engine, CLI, MCP, and core integration tests.** They protect
  transactions and contracts. SKILL-373 owns where core's tests live.
- **`JvmAgentRunProcessRunner`, probes, `AgentRunIdlePolicy` constants.** These
  are the injectable-strategy design in CLAUDE.md.
- **`ContentDigest` in `contracts` and the 9-line launcher copy.** Considered
  and rejected: `contracts` uses the helpers in five phase-output files and has
  no main edge to `host`. Moving them adds a module edge to save nine lines.
- **Platform-pack loading under `skills.scaffold`.** Considered and rejected:
  promoting it to `skills.platformpack` creates a new `platformpack ↔ scaffold`
  cycle (10 back-edge files into scaffold validation and content parsing).
- **Breaking `nativeagent ↔ scaffold`.** Considered and rejected for this bundle:
  it runs through platform-pack loading, and promoting that code creates a new
  cycle. It is recorded as the single true row instead (F-003). The other two
  skills cycles are fixed by moving four files.
- **CLI command hints in scaffold and install results.** They are byte-identical
  operator output for both CLI and MCP. SKILL-371 subtask 3 creates the
  command-token owner; revisit then.
- **Enum-typing the decomposition manifest status field.** `DecompositionManifestModels`
  carries status as `String` through the wire codec. Typing the field ripples
  through codecs and schema. The tokens already have an owner:
  `DecompositionStatus.wireValue` (`runtime-domain/…/workflow/model/ClosedStatusTypes.kt:3`).
  Moved code uses it instead of restating literals.
- **Two YAML parsers.** SnakeYAML is Jackson YAML's engine. Its seven direct
  users keep their duplicate-key and scalar semantics.
- **The `readGitTrackedFiles` `null` fallback.** It makes the generated-artifact
  guard validate every file, which is stricter, not a degradation. Only its
  process handling changes.
- **`explicitApi`, inbound use-case interfaces, splitting by file size.** Not
  proposed. Kotlin `internal` plus the existing public-surface census does the job.

## Guard validity

Each guard was opened and its scan root resolved against the tree.

| Guard | Root resolution | Reads infra files? |
| --- | --- | --- |
| `PackageSiblingCountArchitectureTest` (ceiling) | `RuntimeModuleCatalog.runtimeKotlinModuleDirectory` → `runtime-kotlin/runtime-infra/<m>/src/main/kotlin`; asserts every root non-empty | yes |
| `PackageClusteringArchitectureTest` | same roots | yes |
| `PackageSiblingCountArchitectureTest` co-location (L281) | module `src/{test,testFixtures,repoTest}/kotlin` | yes; checks directory = package only |
| `ApplicationPackageAcyclicityArchitectureTest` infra cases | roots resolve; prefix `skillbill.` collapses all areas | **no effective check (F-003)** |
| `RuntimeEngineBoundaryArchitectureTest` public engine declarations (L79) | `runtime-kotlin/runtime-engine/src/main/kotlin` | reads files, but on the real tree it counts only explicit `public` modifiers (`includeDefaultPublic = false`). SKILL-378 subtask 3 turns on default-public checking; SKILL-371 subtask 1 refreshes the pinned list |
| `RuntimeModuleCatalog.moduleEdgeExpectations` / `RuntimeCoreCompositionOnlyTest` | reads each module's `build.gradle.kts` | yes |
| `CommentAndInterfaceKdocArchitectureTest`, `InlineFqnArchitectureTest` | `runtime-kotlin`, `intellij-plugin`, `runtime-kotlin/build-logic` | yes |
| Ambient-clock guard | per-module roots | yes, but forms omit `currentTimeMillis` (F-005) |

## Coordination with concurrent bundles

Verified against every uncommitted bundle on 2026-09-22 at HEAD `85209c086`. The
only commit since baseline is the amended SKILL-368, and the infra production
digest is unchanged.

| Bundle | Overlap | Owner | Sequencing |
| --- | --- | --- | --- |
| SKILL-370 application | Subtask 1 moves busy retry into SQLite. The typed busy classification belongs with it, and engine `[SQLITE_BUSY]` text checks stay (its non-goal). Subtask 2 moves application `withParentStatus` and `intentFor` into domain; `withParentStatus` is an identical twin of the SQLite copy that 376.2 deletes. Its investigation calls this bundle "SKILL-374 (runtime-infra, in preparation)", a stale key. | 370 | 370 before 376.2 |
| SKILL-371 CLI | Subtask 1: missing-root guards, the pinned engine list, and the one accounting serializer (F-004). Subtask 3 edits SQLite `GoalRepositoryIdentity.kt`, which sits inside the package 376.2 moves. | 371 for guards, serializer, identity; 376 for the infra prefix | 371.1 before 376.1 for F-004 (fallback: domain function). 371.3 in either order; if 376.2 lands first, the diverged SQLite `goalRepositoryIdentity` stays private in the moved file until 371.3 replaces it. |
| SKILL-372 domain | Subtask 1 deletes `decodeArtifacts` and types the snapshot. Subtask 2 reconciles the 16 diverged twins and deletes the engine/SQLite `goalContinuation` copies. Subtask 3 changes `packageImportEdges` granularity for runtime-domain only. | 372 for codecs, twins, domain scan; 376 for the move and the infra prefix | 372.1 → 376.2 → 372.2. Subtask 3 and 376.3 both edit `ArchitectureScanSupport`/`PrincipleEnforcementInventory`; the second rebases. |
| SKILL-373 core | Revised: five core bags and `ScaffoldStandaloneEntrypoint` (subtask 1); guard iteration and baseline keys (subtask 2); suite move to runtime-core `src/repoTest` (subtask 3). No new module. Ambient rules keep every module. | 373 | 373.1 in either order with 376.2 (one-binding rule). 373.2 and 373.3 after 376.3. |
| SKILL-374 contracts | Subtask 1 adds a `skillbill.error.` scan to the same cycle test (runtime-contracts case only). Subtask 2 adds a new `skillbill.infrastructure.contracts.schema` package (at most 12 files, outside 376.3's collapse list) for the 34 `*SchemaPaths` objects, moves `logSchemaLoadFailure` and keys objects into infra modules, and places its moved repoTests in existing production packages. | 374 | 374.2 after 376.3; it uses 376.3's collapsed infra/contracts paths, and `contracts.schema` stays outside the collapse list. |
| SKILL-375 MCP | None. | — | Independent |
| SKILL-377 ports | Subtask 1 types `WorkflowGitOperations` results after 376.1 reworks git process code. No experiment work (SKILL-378.1 deletes it). Subtask 3 deletes dead adapters `FileSystemScaffoldGeneratedStaging`, `FileSystemScaffoldInstallLink`, `FileSystemScaffoldManifestPersistence`, `FileSystemInstalledWorkspaceBaselineStatus` (skills), `FileSystemDeclaredReviewSpecialists` (workflow), and `AgentRunReviewIsolationResolver` (launcher), and follows any path 376.3 moved. It keeps goal-runner store signatures. | 377 | 376.1 before 377.1; 377 after 376.3 |
| SKILL-378 engine | Subtask 1 deletes experiment stores, which moots the F-005 pair-store fix. Subtask 3 adds step classes, turns on default-public visibility checking, and moves sequence allocation into the outcome write "wherever the implementation lives". After 376.2 that write is engine code, and the `max + 1` read must be a SQLite repository method. Its follow-up collapses the child-repair and hydrator ports. | 378 | 376.2 before 378.3 (hard, per SKILL-378). 378.1 runs first after SKILL-368, so the F-005 pair-store fix is expected to be moot. |

The order all nine bundles now state (SKILL-372's verified order, adopted by
SKILL-374 and SKILL-376): 374.1, 376.1, 378.1 → 370 → 371 → 372.1 → 373.1 → 376.2 → 372.2, 372.3 → 375 → 376.3 → 374.2 → 373.2 → 377 → 378.2–3 → 373.3. No bundle requires another to run after one it
must precede, so there is no ordering cycle.

## Limits

- No test suite was run. Counts come from scripts at the baseline sha. They are
  evidence about that tree, not permission to delete.
- Compilation alone can confirm:
  - kotlin-inject generation for the moved goal-runner classes
  - whether the two skills back-edge moves compile without a new back-edge
  - whether removed test dependencies were needed at runtime, in which case
    they become `testRuntimeOnly`
  - whether `ReviewPacketConsumerContractParityTest` needs contracts test
    resources after it moves
  - equal test counts after package moves
- "Public with no reader outside the module" is a name-based census, and names
  can collide. Tighten visibility only where a subtask already touches a file.
- Comparisons with company practice are judgment against public engineering
  practice, not certification.
