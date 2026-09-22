# SKILL-374 - runtime-contracts architectural investigation

## Judgment

Keep the module and its place in the graph. `runtime-contracts` is a true leaf: it
imports no runtime module, 14 of the other 15 modules depend on it, and nothing outside
`runtime-kotlin` consumes it. Its shared core is sound: contract versions, record-identity
schema IDs, shared wire keys, `JsonCodec`, and the error kernel that CLI and MCP classify
failures by.

What is wrong is what else it holds. Three lazy singletons read YAML from the classpath to
deliver values fixed at build time. Much of the rest has one owner elsewhere: 29 DTO
classes, 13 `*Keys` objects, and every schema locator, once the sibling bundles' changes
are accounted for. Two SQLite-named key objects
restate 92 values that other owners already declare. The error packages form a cycle that
the cycle scan cannot see. A marker interface has 20 implementations and no caller.
Two project rules pushed content in, and nothing pushed it out: "declare every wire key in
runtime-contracts" and "`*SchemaPaths` stay in runtime-contracts". 150 of the last 897
runtime commits edited this module, and each change to its public surface recompiles 14
modules.

The fix is deletion and relocation to owners that already exist. No module, framework,
port, dependency bag, or architecture-test class is added.

## Method and baseline

- Baseline: `feat/SKILL-368-build-logic-architecture-cleanup` at
  `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b`, 2026-09-22. Other sessions commit
  concurrently; re-run the censuses at implementation time.
- Context read: `AGENTS.md`; `runtime-kotlin/ARCHITECTURE.md` (Design Principles, Gradle
  Modules, Package Ownership, boundary rules, Runtime Contract And Schema Seams, Wire
  vocabulary); `docs/code-principles.md`; `docs/observability-policy.md`;
  `runtime-kotlin/runtime-contracts/agent/history.md` (the module has no `decisions.md`);
  `runtime-kotlin/agent/decisions.md` entries for 2026-05-28, 2026-09-06, and 2026-09
  (`JvmSystemClock`); `runtime-kotlin/agent/history.md` 2026-08-09 (SKILL-174).
- Prior work: SKILL-349 (the last investigation of this module) read in full; SKILL-52.3,
  SKILL-233, SKILL-351, and SKILL-361 checked for the decisions they recorded here.
- Censuses: Python and `grep` over `runtime-kotlin/**/src/**/*.kt`, excluding `build/` and
  `build-logic`, done directly with no delegated review. Symbol consumers counted by
  import, qualified reference, `import ... as` alias, member import, and same-package
  simple name. Each item a finding lists was verified by hand.

## Prior work: what SKILL-349 fixed and whether it held

| SKILL-349 finding | Status at baseline |
| --- | --- |
| F-001 exact numbers in packaged YAML readers | Landed as `PackagedContractYamlNumbers.kt`. F-001 below removes the readers. |
| F-002 wrong-type planning and scaffold fields | Landed. `requireStringOrDefault` and `optionalString` reject present wrong types. |
| F-003 strict array parse; "remove broad catches that could swallow cancellation" | Partly held. `parseJsonArrayStrict` landed and consumers migrated. `parseArrayOrEmpty` is left with one test caller, and `parseObjectOrNull` still catches `Exception` (F-006). |
| F-004 lossless numbers | Landed (`BigInteger`, `BigDecimal`, finite checks, typed unsupported-value errors). |
| F-005 live clock | Landed: `Clock.tickMillis(ZoneOffset.UTC)`. |
| F-006 fold `WorkflowContracts` | Landed; the file is gone. |
| F-007 one owner for phase-output failure tokens | Landed as `FeatureTaskRuntimePhaseOutputFailureCode`. SKILL-361 (2026-09-19) later split `skillbill.error` into subpackages, which created the cycle in F-005. |
| F-008 dead code | Landed; `RecordingNullObjectDiagnostics`, `requireScalar`, and `failureWireByValueOrNull` are gone. New dead code has accumulated since (F-004). |

SKILL-349 retained two things this investigation revisits:

- It kept the packaged-YAML loaders and rejected "moving every loader behind a new port"
  as a large dependency change. F-001 proposes neither a port nor a move. It replaces the
  loaders with constants, based on evidence SKILL-349 did not weigh. Planning caps for the
  same seven fields are already `const val` in runtime-ports. Two of the three YAML files
  have no reader but the loaders. The values are baked into the jar. SKILL-349's own
  hardening (87 lines plus tests) is part of what the design costs.
- It called both interfaces narrow and justified. That holds for `FailureWireCode`, which
  a generic decoder and the totality test use as a type. It does not hold for
  `JsonPayloadContract`, which nothing uses as a type (F-008).

SKILL-349's other retention decisions stand: typed errors, the `JsonCodec` design,
`JvmSystemClock`, DTOs over raw maps, and no serialization framework.

## Census

### Size

| Source set | Files | Lines |
| --- | ---: | ---: |
| main | 112 | 4,691 |
| main: `skillbill.contracts` | 85 | 3,329 |
| main: `skillbill.error` | 27 | 1,362 |
| test | 10 | 707 |
| repoTest | 3 | 140 |
| testFixtures | 0 | 0 |

Largest main packages: `contracts.experiment` 12 files (at the 12-file sibling limit),
`error.shellcontent` 11, `error.core` 11, `contracts.review` 9. Every test package has a
main counterpart. `JsonSupportTest` tests `JsonCodec`, a name left over from the SKILL-233
rename.

### Build edges

| Module | Configuration | Justified |
| --- | --- | --- |
| runtime-contracts itself | `api` kotlinx-serialization-json; `implementation` SnakeYAML | `api` yes: `JsonCodec` exposes `Json`, `JsonObject`, and `JsonElement`. SnakeYAML only serves F-001. |
| runtime-ports, runtime-application, runtime-engine | `api` | Ports and application signatures expose contract DTOs and errors. The engine `api` edge was not checked symbol by symbol; compiling confirms it. |
| runtime-domain, runtime-core, runtime-cli, runtime-mcp, all seven infra modules | `implementation` | Yes. |
| runtime-infra/sqlite | `testFixturesImplementation` | Yes (fixtures build contract payloads). |
| runtime-engine, runtime-ports, runtime-infra/http | redundant `implementation(kotlinx-serialization-json)` | No. None imports `kotlinx.serialization`; the `api` edge supplies the types `JsonCodec` exposes. |

### Declarations and consumers

| Kind | Count | Single production owner outside this module | No production reader |
| --- | ---: | ---: | ---: |
| DTO classes | 44 | 29 moving (below). `UpdateCheckContract` stays because SKILL-371 makes CLI a second consumer. Also single-owner but staying: `VersionContract` (shared extension), 3 planning wire types (travel with `DecompositionPlanningResult`), 4 port-exposed contracts | 0 |
| `*Keys` objects | 41 (914 members) | 13 moving. SKILL-378 deletes the 4 experiment objects; `UpdateCheckPayloadKeys` stays with its DTO | 43 members in non-reflected objects |
| `*SchemaPaths` objects | 34 | locators: infra:contracts owns them; infra:skills reads them through its edge | - |
| Other objects and top-level members | 79 | `DecompositionManifestProjectionOperations` (application), `logSchemaLoadFailure` (infra:contracts, infra:skills) | 3 dead (F-004); others are read only inside their own file |
| Error classes | 151 | 84 used by one module; they stay (What stays) | 12 |
| Interfaces | 2 | - | `JsonPayloadContract`: 20 implementations, no use as a type |
| typealiases | 0 | - | - |
| `@Inject` classes | 0 | - | - |
| Mutable `var` | 0 | - | - |

`Map<String, Any?>` appears 74 times in this module's main source. It is the wire
representation and stays (What stays). Three DTO files build maps with 30 inline string
keys (`McpAdapterContracts.kt`, `SystemContracts.kt`, `ReviewContracts.kt`). These seams
are not governed, and the literals move unchanged with the DTOs that own them.

Churn: `git log --since=2026-06-22 main -- runtime-kotlin/runtime-contracts/src/main`
returns 150 commits, against 897 for `runtime-kotlin` as a whole.

## Checklist

| # | Question | Answer |
| --- | --- | --- |
| 1 | Dependency direction; `api` vs `implementation` | Clean: no project dependencies. `api` kotlinx is justified by `JsonCodec`'s signatures; decision 2026-09-06 (e) says otherwise and needs superseding (F-006). Three redundant kotlinx declarations (F-006). |
| 2 | Inbound: adapters call use cases; getters | Not applicable to a leaf. Adapters use DTOs directly. No getters or dependency bags (0 `@Inject`). |
| 3 | Outbound: vendor protocol in inner layer | Two SQLite-named key objects live in the shared leaf (F-002, F-003). `DatabaseAccessError` and the telemetry HTTP errors sit in the kernel; they stay with the kernel (What stays). No ports are declared here. |
| 4 | Domain rules outside domain; duplicated rules | Discovery exclusion `isExcluded` is a pure rule inside a classpath-loading singleton (F-001). Verification caps duplicate the planning-cap mechanism (F-001). Issue-key syntax rules stay here (What stays). |
| 5 | Second composition root, bag, or locator | The three lazy singletons act as service locators: callers reach global state statically (F-001). |
| 6 | Entry-point names in use cases | MCP-only DTOs (`McpAdapterContracts.kt`, including the default `telemetrySkill = "bill-code-review"`) live in the shared leaf (F-002). |
| 7 | Ambient effects | Classpath reads (F-001). `JvmSystemClock` is the recorded ambient clock seam and stays. |
| 8 | State and transactions | No `var`. The lazy singletons hold process-global state initialized by whichever thread first touches them (F-001). No transactions. |
| 9 | Error model | Typed throughout. `parseObjectOrNull` catches `Exception` (F-006). `normalizeIssueKey` throws `IllegalArgumentException` via `require`; no caller catches it (What stays). Loader YAML catches rethrow cancellation (SKILL-349 held). |
| 10 | Cohesion and ownership | F-002. |
| 11 | YAGNI | F-001, F-004, F-008. |
| 12 | Naming and packages | `skillbill.contracts.workflow.workflow` stutters (18 importing files) (F-009). `JsonSupportTest` name (F-009). `skillbill.error.shellcontent` is misnamed and stays (SKILL-372). Sibling limits hold. |
| 13 | Guard validity | See the table below. |

### Guards this spec cites or extends

| Guard | Root and evidence | Valid |
| --- | --- | --- |
| `RuntimeArchitectureTest` "runtime contracts main source is free of networknt jackson and nio files" | `sourceFiles()` over `runtime-kotlin/<module>/src/main/kotlin`; filters on `runtime-kotlin/runtime-contracts/...` and asserts non-empty. | Yes. Subtask 1 extends its two ban lists (`RuntimeArchitectureTestSupport.kt` L777-789). |
| `ApplicationPackageAcyclicityArchitectureTest` runtime-contracts case | `mainScanRoot = runtime-kotlin/runtime-contracts/src/main/kotlin`; prefix `skillbill.contracts.` (`PrincipleEnforcementInventory.kt` L52). | Reads files but cannot see `skillbill.error` (F-005). A `skillbill.` prefix would not fix it: `packageImportEdges` takes one segment after the prefix, so all `error.*` packages collapse into one area. |
| `RuntimeEnforcementHardeningArchitectureTest` concrete-validator scan | Own root is the `runtime-kotlin` directory; asserts non-empty. | Yes. |
| `RuntimeArchitectureTest.assertContractsSchemaPathFilesPresent` | Pins four `*SchemaPaths.kt` files under runtime-contracts. | Valid; it encodes the 2026-05-28 decision that F-002 supersedes, so subtask 2 updates it. |
| `RuntimeArchitectureTest` telemetry test | `sourcePath("skillbill/contracts/telemetry/TelemetryProxyContracts.kt")` asserts `data class TelemetryProxyBatchEvent`. | Valid; subtask 2 repoints it to infra:http. |
| `RuntimeArchitectureTest` "cli and mcp learning payloads use contract DTO mappers" | Pins `LearningContracts.kt` and `SystemContracts.kt` paths and asserts the mapper imports `skillbill.contracts.learning`. | Valid, but a source-text pin. SKILL-373 subtask 2 and SKILL-375 delete it; subtask 2 here deletes it if it still exists. |
| `RuntimeArchitectureTest` "review and telemetry domain models do not own json payload contracts" | Filters `runtime-domain/src/main/...` without the `runtime-kotlin/` prefix. | Vacuous: scans zero files. SKILL-371 subtask 1 owns the repair. Once repaired it finds one file, `ReviewAccountingPayload.kt`, via its raw-map regex. |
| `WireVocabularyGovernedSeamInventory` | Reflects over key objects by class and reads schemas by repo path. | Valid; moving the objects changes imports only. |
| `RuntimeCoreSerializationDependencyArchitectureTest` | Checks `runtime-core/build.gradle.kts` only. | Unaffected by F-006's edits. |

## Findings

Priority: P1 changes how the module is understood or hides a defect; P2 is a measured
maintenance cost; P3 is hygiene.

### F-001 P1 - Ambient YAML loaders re-implement JSON Schema for build-time constants

Evidence:

- `runtime-contracts/build.gradle.kts` L15-89: three `Copy` tasks stage
  `goal-verification-boundary-caps.yaml`, `goal-planning-discovery-exclusions.yaml`, and
  `issue-key-schema.yaml` under `skillbill/infrastructure/contracts/`, a namespace named
  for another module.
- `GoalVerificationBoundaryCaps.kt` (117 lines), `GoalPlanningDiscoveryExclusions.kt`
  (144), and `IssueKeyShape.kt` (120) each hold `private val contract by lazy { parse(readContract()) }`
  over `javaClass.classLoader.getResourceAsStream`. `PackagedContractYamlNumbers.kt` (87)
  restates `type: integer`, `minimum`, and `uniqueItems`. Together they are 468 lines, and
  their tests are 389.
- They deliver 8 integers, 1 root, 18 directory names, and `maxLength` 128.
  `IssueKeyShape.jsonSchemaPattern` has no reader.
- Static callers: runtime-ports `GoalPlanningBoundaryBodyResolutionCaps.kt` L12-17 (a
  companion `val`, so loading the class reads the classpath), engine
  `GoalPlanningSharedContextPacket.kt` L176, infra:workflow
  `GoalPlanningRepositoryScopeWalk.kt` L17/L25/L71 and
  `FileSystemGoalPlanningContextDiscovery.kt` L36/L206-210, runtime-core
  `RuntimeGoalPlanningProvides.kt` L45 (a discarded statement that forces the load; also
  SKILL-373 F-012).
- runtime-ports `GoalPlanningContext.kt` L28-40 declares the planning caps for the same
  seven fields as `const val`.
- A repository-wide grep finds no reader of the caps or exclusions YAML besides these
  loaders. Three schemas `$ref` `issue-key-schema.yaml`.

Fix:

- Verification caps become `const val` beside the planning caps in `GoalPlanningContext`.
- Exclusions become a pure runtime-domain value in `skillbill.goalrunner.planning` (ports
  forbids top-level objects). It holds the two lists and `isExcluded`, uses string
  operations only, and so is compatible with domain's `java.nio` ban.
- `MAX_ISSUE_KEY_LENGTH` becomes `const val` 128, pinned to the schema by one repoTest in
  runtime-infra/contracts.
- Delete the loaders, the helper, the copy tasks, the SnakeYAML dependency, the two YAML
  files, their two schemas, the three loader-only error types, and the three
  `ParseBoundarySite` entries.
- Extend the existing contracts purity ban lists with `org.yaml.`, `java.io.`, and
  `getResourceAsStream`.

Rejected: move the loaders to infra:contracts, validate with networknt, and inject values.
That keeps a runtime read of build-time values and threads 30 constants through DI in
four modules.

### F-002 P2 - The shared leaf holds single-owner content

Placement rule used: a declaration belongs in `runtime-contracts` when two or more
production modules read or write it, or a `runtime-ports` signature exposes it. Otherwise
it belongs with its one owner. Resources that runtime-infra/contracts stages are owned by
runtime-infra/contracts; infra:skills already depends on it.

Moving (verified against import rules and module edges):

| Target | Items | Feasibility |
| --- | --- | --- |
| runtime-application | 19 DTOs: `LearningEntryDto`, `LearningSummaryWire`, `LearningAppliedSessionWire`, `LearningListContract`, `LearningRecordContract`, `LearningResolveContract`, `LearningDeleteContract`, `ReviewPreviewContract`, `ImportedReviewContract`, `ReviewFeedbackContract`, `NumberedFindingContract`, `TriageDecisionContract`, `TriageListContract`, `TriageRecordedContract`, five `Lifecycle*Contract`, `DoctorContract`; also `LearningPayloadKeys` and `DecompositionManifestProjectionOperations` | Imports are contracts keys and errors only. cli and mcp already depend on application. |
| runtime-mcp | `McpReviewImportSkippedContract`, `McpTriageSkippedContract`, `McpLearningsSkippedContract`, `McpOrchestratedPayloadContract` | mcp-only readers. |
| runtime-infra/http | `TelemetryProxyBatchEvent`, `TelemetryProxyBatchPayload`, `RemoteStatsQueryPayload` | `TelemetryProxyPayloadKeys` stays (cli, domain, http, mcp read it). |
| runtime-infra/launcher | `GovernedReviewWirePayload`, `GovernedReviewToolSpecList`, `GovernedReviewEvidencePayloadKeys` | `GovernedReviewEvidenceContracts` (the constants object) stays: runtime-mcp main and an infra:skills repoTest read it. The file splits. |
| runtime-domain | `InstallPlanContract`, `InstallPlanPayloadKeys`, `ReadinessEvidencePayloadKeys` | Domain may declare objects. The infra:contracts and infra:skills tests that read them depend on domain. |
| runtime-engine | `GoalPlanningSharedContextPacketPayloadKeys`, `GoalSubtaskReviewInputPayloadKeys`, `ImplementationReturnContractPayloadKeys` | Engine-only production readers; core tests depend on engine. |
| runtime-cli | `GoalRunnerPurgePayloadKeys`, `GoalRunnerResetPayloadKeys` | cli-only. |
| runtime-infra/sqlite | `GoalTelemetryPayloadKeys`, `SqliteLifecycleTelemetryMaterializationPayloadKeys`, `SqliteReviewTelemetryPayloadKeys` | The infra:contracts repoTest imports only `LifecycleTelemetryPayloadKeys`, which stays. The reflecting inventory is a runtime-core test, and core depends on sqlite. |
| runtime-infra/workflow | `DecompositionManifestBundleJournalPayloadKeys` | Only two workflow files and the core inventory read it. |
| runtime-infra/contracts | the 30 non-experiment `*SchemaPaths` objects, except the two record-identity IDs, into one `skillbill.infrastructure.contracts.schema` package of at most 12 files; `logSchemaLoadFailure` | Production readers are infra:contracts and infra:skills. Test readers in core, mcp, engine, and sqlite all have a test edge to infra:contracts. A dedicated package keeps SKILL-376's collapsed validator packages under their 12-file ceiling. |

Staying, and why:

- `DecompositionPlanningResult` and its wire types: five production modules read it. Its
  13 private map readers produce indexed messages (`subtasks[2].dependencies[0].subtask_id`)
  and accept a bare-integer dependency shorthand. Domain's `DurableArtifactMapReader`
  hard-codes "Feature-task-runtime artifact field '<key>'" messages and cannot reproduce
  them, so reusing it would change error output. Consolidating the 17 map-reader helper
  sets across the runtime is out of scope.
- `ScaffoldPayloadParsing.kt`: two production modules (cli, mcp) read it.
- `UpdateCheckContract` and `UpdateCheckPayloadKeys`: MCP-only today, but SKILL-371 subtask 2 renders CLI update-check output through the contract too (and adds `release_url`), which makes them shared.
- Everything under `contracts/experiment/**`, the four experiment `*SchemaPaths` objects, and the `Experiment*` error classes. SKILL-378 subtask 1 deletes experiment support, so this spec neither moves nor edits them.
- `VersionContract`, `RuntimeProvenanceContract`, `toRuntimeProvenance`: application and
  cli read `RuntimeProvenanceContract`, and the shared extension takes `VersionContract`.
- `GoalPlanningPreparationSchemaPaths.EXPECTED_SCHEMA_ID` and
  `FeatureTaskRuntimePhaseOutputSchemaPaths.EXPECTED_SCHEMA_ID`: ports, engine, and sqlite
  store them as record identities. They become top-level constants beside their versions.
- The port-exposed contracts `RepoValidationReportContract` and
  `ReleaseRefMetadataContract` with their same-file keys.
- Not in scope: `FeatureImplementSessionSummaryContract`, `FeatureVerifySessionSummaryContract`,
  and `WorkflowSessionSummaryPayloadKeys`. They are exposed only through two unused
  `toContract` mappers in runtime-ports, and SKILL-377 subtask 2 deletes the mappers, both
  contracts, and their keys.

### F-003 P2 - Two SQLite shadow key objects restate 92 values

`SqliteLifecycleTelemetryMaterializationPayloadKeys` has 89 members. 67 of their values
are already declared by `SharedPayloadKeys`, `LifecycleTelemetryPayloadKeys`, or
`GoalTelemetryPayloadKeys`, and 71 members have no reader. `SqliteReviewTelemetryPayloadKeys`
has 44 members; 25 restate `ReviewFindingPayloadKeys`, `ReviewFinishedTelemetryPayloadKeys`,
`ReviewVerificationSignalKeys`, or `SharedPayloadKeys`, and 16 have no reader.
`WireVocabularyGovernedSeamInventory` L244-259 already builds each SQLite seam's allowlist
as the union of these owners, so the duplicates add nothing to enforcement and double the
cost of a rename. `WireVocabularyArchitectureSupport.duplicateDeclarations` (L276) groups by
(category, value, owning object), so cross-object duplicates pass today. The guard does
not constrain where a `*Keys` object lives: runtime-domain and runtime-infra/host already
declare their own. Fix: the two objects keep only values no shared owner declares, and the
union each seam builds stays identical.

### F-004 P2 - Dead code

- Declarations: `REVIEW_CONTEXT_SCHEMA_RESOURCE`, `FEATURE_TASK_RUNTIME_REPAIR_PLAN_CONTRACT_VERSION`,
  `DECOMPOSITION_PLANNING_CONTRACT_VERSION`, `IssueKeyShape.jsonSchemaPattern`,
  `JsonCodec.parseArrayOrEmpty` (one runtime-cli test caller).
- Error classes: `FeatureSpecPreparationModeConflictError`, `InvalidValidatorWireInputError`,
  `JsonIntegerOutOfRangeError`, `FeatureTaskRuntimeSubtaskCommitReconciliationError`,
  `InvalidFeatureTaskRuntimeRepairPlanError`, `InvalidDescriptorSectionError`,
  `InvalidExecutionSectionError`, `InvalidCeremonySectionError`,
  `MissingShellCeremonyFileError`, `ScaffoldValidatorError`.
- `InvalidFeatureTaskRuntimePhaseBriefingFramingError` is constructed nowhere but caught in
  `FeatureTaskRuntimeRunLoopLaunch.kt` L541 and `FeatureTaskRuntimeRunLoopOutputVerification.kt`
  L203, with a handler at L576. The branches cannot run.
- 43 unreferenced members of non-reflected `*Keys` objects: `WorkflowWirePayloadKeys` 11,
  `GovernedReviewEvidencePayloadKeys` 9, `DecompositionPlanningPayloadKeys` 6,
  `ReviewAccountingPayloadKeys` 5, `McpToolPayloadKeys` 5, `DecompositionManifestPayloadKeys`
  3, and one or two each in five others.
- `TRACKER_STYLE_ISSUE_KEY` and `ISSUE_AND_FEATURE_DIRECTORY` are public but read only in
  their own file.

### F-005 P1 - The error packages form a cycle that the scan cannot see

Edges: `core -> shellcontent` (`ShellContentContractException` in five files, a failure
kind in `FailureWireCodeContract.kt`), `core -> featuretask` (`FailureWireCodeContract.kt`),
`shellcontent -> core`, `shellcontent -> featuretask`, `featuretask -> core, shellcontent`.
SKILL-361 introduced the subpackages. The scan prefix `skillbill.contracts.` excludes them,
and the baseline is empty.

Fix:

- Move `ShellContentContractException` to `error.core`.
- Move both failure-kind enums and `coarseFailureKindForPhaseOutputWireCode` to
  `error.featuretask`. The resulting order is `core` <- `featuretask` <- `shellcontent`.
  `error.core` goes to 12 files, at the limit.
- The runtime-contracts package-cycle scan must see `skillbill.error.*` subpackages. If
  SKILL-372 subtask 3's exact-package granularity has landed, enable it for the
  runtime-contracts case with prefix `skillbill.`. Otherwise add a second scan with prefix
  `skillbill.error.` through the existing `ArchitectureScanSupport.packageCycles`. Either
  way the baseline stays empty. SKILL-373 leaves this test class's shape unchanged.

No class or package is renamed.

### F-006 P2 - JsonCodec and dependency hygiene

- `parseObjectOrNull` (L32-40) catches `Exception`, including `CancellationException`, on
  59 production call sites. Narrow the catch to `SerializationException` and
  `IllegalArgumentException`, the exceptions `parseJsonElementStrict` maps. Adding a
  fallback record at each call site belongs to that caller's area.
- `parseValue` (L118-129) re-catches what `parseJsonElementStrict` already converted.
- Record that `api` kotlinx is correct and supersede decision 2026-09-06 (e), which
  SKILL-351 (`58b235671`) contradicted without a new entry.
- Delete the redundant kotlinx declarations in runtime-infra/http and runtime-engine.
  SKILL-378 subtask 1 assigns the engine copy to this spec. SKILL-377 subtask 3 removes
  runtime-ports' copy, so this spec leaves that file alone.

### F-007 P3 - Documentation drift

`ARCHITECTURE.md` Package Ownership says `skillbill.contracts` spans two modules; infra
has no such package. It presents the packaged YAML and the key rule as the design. The
Gradle Modules bullet lists `*SchemaPaths` as a contracts responsibility.

### F-008 P3 - `JsonPayloadContract` is a marker with no consumer

20 classes implement it (12 in contracts, 6 in application, 1 in launcher, 1 in ports), and
no code uses it as a parameter, collection, or return type. Its only other mention is the
vacuous domain guard's text match. That guard also matches any
`fun ...(): Map<String, Any?>`, so it keeps working without the interface. Fix: delete the
interface and the `override` modifiers. `toPayload()` stays on every class.

### F-009 P3 - Naming

- `skillbill.contracts.workflow.workflow` stutters. After F-002 moves
  `WorkflowStateSchemaPaths`, the package holds `WorkflowWirePayloadKeys` and
  `WORKFLOW_STATE_CONTRACT_VERSION`; they move up to `skillbill.contracts.workflow`, which
  has no files of its own. 18 files import the package.
- `JsonSupportTest` merges into `JsonCodecTest`.

## Over-engineering register

| Item | Shape | Action | Estimated net lines |
| --- | --- | --- | ---: |
| Packaged YAML loaders plus helper plus copy tasks | Runtime I/O and a JSON Schema subset for build-time constants | Delete; use constants | -560 main and build, -389 test, -4 YAML files |
| SQLite shadow key objects | Second owner for 92 values | Delete the duplicates | -92 |
| Dead declarations and key members | Unreachable code | Delete | about -200 |
| Unreachable catch branches and handler | Handling for an error never thrown | Delete | about -30 |
| `JsonPayloadContract` | Interface with no consumer | Delete | -6, and 20 `override` modifiers |
| Redundant kotlinx declarations (http, engine; ports belongs to SKILL-377) | Build noise | Delete | -2 |

Relocations (F-002, F-009) change where code lives, not how much there is.

## What stays unchanged

| Item | Reason |
| --- | --- |
| Module graph and `RuntimeModuleCatalog` edges | The direction is right; every move uses an existing edge. |
| `*_CONTRACT_VERSION` constants, including single-consumer ones | Each is the published identity of a schema that agents also write; the parity tests key on this location. |
| Error kernel, class names, `error.shellcontent` name, the 84 single-module errors | CLI and MCP classify by kernel base types; tests in five modules assert them; SKILL-372 decided the same. |
| `DatabaseAccessError` and the telemetry HTTP errors in the kernel | CLI catches `DatabaseAccessError`; the HTTP errors belong to the same kernel decision. |
| `JsonCodec` and its stdlib facade | One codec; SKILL-349 hardened it. |
| `Map<String, Any?>` plus `*Keys` as the wire representation | About 1,500 sites and the governed-seam inventory depend on it. A typed-DTO migration is its own program, and SKILL-372 rejected it for domain. |
| `DecompositionPlanningResult` and its decoder | Shared by five modules; the domain reader cannot keep its messages (F-002). |
| `ScaffoldPayloadParsing` | Two production modules read it. |
| Issue-key functions (`normalizeIssueKey`, `issueAndFeature`) | Five modules read them. They define an identifier's syntax, which belongs with the issue-key schema. `require` throws an untyped exception but no caller depends on its type; changing it is an error-model change for another spec. |
| `FailureWireCode` | Used as a type by `failureWireByValue` and the totality test. |
| `JvmSystemClock` | Recorded decision; one line. |
| `issue-key-schema.yaml` | Three schemas `$ref` it. |
| Considered and rejected: moving the 61 adapter-only errors; splitting the module per area; a consumer-count guard; injecting caps through DI; enum-typing `DecompositionPlanningResult.mode`; `explicitApi()` | Churn without behavioural payoff; more machinery than the problem; the enum ripples through the wire codec; SKILL-372 rejected `explicitApi()`. |

## Coordination with concurrent bundles

Checked on 2026-09-22 against every uncommitted bundle: SKILL-368 and SKILL-370
through SKILL-378.

| Bundle | Overlap with SKILL-374 | Owner | Sequencing |
| --- | --- | --- | --- |
| SKILL-368 build-logic (in progress) | Edits `runtime-contracts/build.gradle.kts` plugin wiring; subtask 1 here deletes that file's copy tasks | 368 | 374 starts after 368 merges |
| SKILL-370 runtime-application | Receives 19 DTOs, `LearningPayloadKeys`, and `DecompositionManifestProjectionOperations` from subtask 2. 370 moves and renames application packages and moves `UpdateCheckService` protocol code to infra:http, touching none of those DTOs | 370 for its packages, 374 for the DTO moves | Subtask 1 has no hard dependency on 370 (the verified order below runs it first; the second to land rebases import lines). Subtask 2 runs after 370 and targets the package names 370 leaves |
| SKILL-371 runtime-cli | Subtask 1 repairs vacuous guards, including the `RuntimeArchitectureTest` domain filters; subtask 2 routes CLI update-check output through `UpdateCheckContract`; subtask 3 adds a shared goal-child protocol owner to runtime-contracts | 371 | 374 subtask 2 after 371 subtask 1. `UpdateCheckContract` stays in runtime-contracts, so the order of 371 subtask 2 does not matter. The protocol owner fits the placement rule. |
| SKILL-372 runtime-domain | Keeps the domain -> contracts edge, the error taxonomy, and `DurableArtifactMapReader`; receives `InstallPlanContract`, `InstallPlanPayloadKeys`, `ReadinessEvidencePayloadKeys`, and the exclusions value; subtask 3 adds exact-package cycle detection for domain | 372 for the algorithm; 374 for what the contracts scan covers | Either order. 372.3 adds an opt-in exact-package mode; F-005's fix may use it, and the default-granularity second scan works either way. 372.1 deletes three lenient `parseObjectOrNull` callers |
| SKILL-373 runtime-core | Deletes the same discarded statement in `RuntimeGoalPlanningProvides`; subtasks 2 and 3 restructure and move the architecture suite that 374 edits | 373 for the suite shape | 374 (both subtasks) before 373 subtasks 2 and 3, as 373 states |
| SKILL-375 runtime-mcp | Adopts 374's placement rule for MCP output keys, and relies on `parseObjectOrNull` returning null for malformed lines (now in 374 subtask 1). It deletes the "cli and mcp learning payloads" pin test, and agrees `UpdateCheckContract` and its keys stay in runtime-contracts | 375 for MCP files; 374 for the rule | Either order |
| SKILL-376 runtime-infra | Subtask 3 changes infra scan prefixes in the same `packagePrefixForModule`, collapses infra:contracts packages under a 12-file ceiling, and requires every infra test package to exist in production | 376 for infra layout | 374.2 after 376.3 in the global order, although either order would work. The new `skillbill.infrastructure.contracts.schema` package is outside 376.3's collapse list, and the moved repoTests use existing production packages (376.3's rule exempts `src/repoTest`). 376's row says "no shared files", which is no longer true |
| SKILL-377 runtime-ports | Removes runtime-ports' kotlinx declaration. Subtask 2 deletes the two unused `toContract` mappers, `FeatureImplementSessionSummaryContract`, `FeatureVerifySessionSummaryContract`, and their keys | 377 | No conflict; 374 neither moves nor keeps those two contracts and no longer edits the ports build file |
| SKILL-378 runtime-engine | Subtask 1 deletes experiment support, including `contracts/experiment/**`, experiment schema paths, and errors. It assigns the engine kotlinx removal to 374 subtask 1. Subtask 2 adds a typed SQLite busy error to `error.core` | 378 for experiments and the busy error; 374 for the engine build file | 374 subtask 2 after 378 subtask 1, which 378 also states; 374 excludes every experiment item. `error.core` already exists (SKILL-361), and 374 subtask 1 only moves three declarations into it and `error.featuretask` |

Global order: SKILL-374 adopts the acyclic order the SKILL-372 session verified across all
nine bundles (after SKILL-368):

374.1, 376.1, 378.1 → 370 → 371 → 372.1 → 373.1 → 376.2 → 372.2, 372.3 → 375 → 376.3 →
374.2 → 373.2 → 377 → 378.2–3 → 373.3

SKILL-374's own constraints all hold in it:
- 374.1 runs after 368.
- 374.2 runs after 374.1, 370, 371.1, and 378.1, and before 373.2 and 373.3.
- 374.2 also runs after 376.3, as SKILL-372 and SKILL-376 recorded. That is not required,
  but it is harmless.
- 374.1 runs before 372.3, so F-005's cycle fix uses the second-prefix scan, not the
  exact-package mode.

Stale statements in sibling bundles (not edited here; their owners should update them):

- SKILL-370 investigation calls SKILL-374 "runtime-infra, in preparation".
- SKILL-376 investigation says it shares no files with SKILL-374.
- SKILL-378 investigation says SKILL-374 subtask 2 moves `Experiment*PayloadKeys` into
  engine. It does not; they are left for SKILL-378 to delete (SKILL-378's session agrees
  the move is moot).
  SKILL-378 subtask 2 says "if SKILL-374 subtask 1 has split `skillbill.error`": the
  split already exists.

## Limits

- Only compiling confirms: that each moved item has no reference the censuses missed (for
  example through reflection or string-built class names); that the engine and application
  `api` edges still need `api` after the moves; that no test in a module lacking the target
  edge references a moved key.
- The 59 `parseObjectOrNull` callers were not audited for fallback records (SKILL-372.1
  deletes three of them).
- No full `./gradlew check`, install, or runtime launch ran for this investigation.
