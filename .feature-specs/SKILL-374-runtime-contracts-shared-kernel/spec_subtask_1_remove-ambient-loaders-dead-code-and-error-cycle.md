# SKILL-374 subtask 1 - Remove ambient loaders, dead code, and the error-package cycle

Parent: `spec.md`. Evidence: `investigation.md` F-001, F-004, F-005, F-006.

## Scope

1. **Packaged YAML loaders (F-001).**
   - Add the verification caps to runtime-ports `GoalPlanningContext` as `const val`, using
     the current YAML values: discovery file count 16, headings per file 50, catalog
     headings 128, history recency days 30, selected bodies 12, body bytes 4096, total
     body bytes 32768, boundary file bytes 131072. Name them so they read as the
     verification group beside the planning group.
     `GoalPlanningBoundaryBodyResolutionCaps.VERIFICATION` and
     `FileSystemGoalPlanningContextDiscovery` read them.
   - Add a pure runtime-domain value in `skillbill.goalrunner.planning` that holds the
     excluded roots (`platform-packs/`) and excluded directory names (the 18 current
     entries, same order) and exposes `isExcluded(relativePath)` with the current
     normalization: backslashes become `/`, `.` and empty segments drop, `..` pops a
     segment, and escaping the root counts as excluded. Engine
     `GoalPlanningSharedContextPacket`, infra:workflow `GoalPlanningRepositoryScopeWalk`
     and `FileSystemGoalPlanningContextDiscovery`, runtime-core
     `RuntimeGoalPlanningProvides`, and `ExcludedRootAgentTreeAbsenceTest` use it. Delete
     the discarded `excludedRoots` statement in `RuntimeGoalPlanningProvides`.
   - In `skillbill.contracts.issuekey`, `MAX_ISSUE_KEY_LENGTH` becomes
     `const val MAX_ISSUE_KEY_LENGTH: Int = 128`. Add one repoTest in
     `runtime-infra/contracts` that reads `orchestration/contracts/issue-key-schema.yaml`
     and asserts `minLength == 1` and `maxLength == MAX_ISSUE_KEY_LENGTH`.
   - Delete `GoalVerificationBoundaryCaps.kt`, `GoalPlanningDiscoveryExclusions.kt`,
     `IssueKeyShape.kt`, `packaged/PackagedContractYamlNumbers.kt`, their tests
     (`GoalVerificationBoundaryCapsTest`, `IssueKeyShapeTest`, the YAML-shape cases of
     `GoalPlanningDiscoveryExclusionsTest`), and `GoalVerificationBoundaryCapsParityTest` in
     infra:workflow. Keep the normalization cases of `GoalPlanningDiscoveryExclusionsTest`
     as tests of the domain value.
   - Delete the three copy tasks, the `sourceSets` resource wiring, the task `dependsOn`
     loop, and `implementation(libs.snakeyaml)` from `runtime-contracts/build.gradle.kts`.
   - Delete `orchestration/contracts/goal-verification-boundary-caps.yaml`,
     `goal-verification-boundary-caps-schema.yaml`,
     `goal-planning-discovery-exclusions.yaml`, and
     `goal-planning-discovery-exclusions-schema.yaml`, plus any validator or parity test
     that loads them. Update the `agent/` guidance and skill text that cite these files by
     name, if any (repository grep).
   - Delete `InvalidGoalPlanningDiscoveryExclusionsSchemaError`,
     `InvalidGoalVerificationBoundaryCapsSchemaError`, and `InvalidIssueKeySchemaError`, and
     the three `ParseBoundarySite` entries for the deleted files in
     `PrincipleEnforcementInventory`.
   - Extend the existing runtime-contracts purity lists in
     `RuntimeArchitectureTestSupport.kt` (L777-789): add `org.yaml.` and `java.io.` to
     `contractsForbiddenImports`, and `org.yaml.`, `java.io.`, and `getResourceAsStream` to
     `contractsForbiddenSourceReferences`. Extend the synthetic-fixture test beside it so
     that one fixture uses `getResourceAsStream` and must be rejected.
2. **Dead code (F-004).** Delete `REVIEW_CONTEXT_SCHEMA_RESOURCE`,
   `FEATURE_TASK_RUNTIME_REPAIR_PLAN_CONTRACT_VERSION`,
   `DECOMPOSITION_PLANNING_CONTRACT_VERSION`, `FeatureSpecPreparationModeConflictError`,
   `InvalidValidatorWireInputError`, `JsonIntegerOutOfRangeError`,
   `FeatureTaskRuntimeSubtaskCommitReconciliationError`,
   `InvalidFeatureTaskRuntimeRepairPlanError`, `InvalidDescriptorSectionError`,
   `InvalidExecutionSectionError`, `InvalidCeremonySectionError`,
   `MissingShellCeremonyFileError`, `ScaffoldValidatorError`, and
   `JsonCodec.parseArrayOrEmpty` (migrate its one runtime-cli test to the strict parser or
   delete that case). Delete `InvalidFeatureTaskRuntimePhaseBriefingFramingError`, its two
   catch branches (`FeatureTaskRuntimeRunLoopLaunch.kt`,
   `FeatureTaskRuntimeRunLoopOutputVerification.kt`), and `rejectedBriefingLaunch` if
   nothing else calls it. Delete the 43 unreferenced members of non-reflected `*Keys`
   objects that investigation F-004 counts. Re-run the census first and delete only
   members that still have no reference. Make `TRACKER_STYLE_ISSUE_KEY` and
   `ISSUE_AND_FEATURE_DIRECTORY` private.
3. **Error-package cycle (F-005).** Move `ShellContentContractException` to
   `skillbill.error.core`. Move `FeatureTaskRuntimePhaseOutputFailureKind`,
   `FeatureTaskRuntimeHandoffProjectionFailureKind`, and
   `coarseFailureKindForPhaseOutputWireCode` to `skillbill.error.featuretask`. Update
   imports repository-wide (about 65 import lines, plus `FailureCodeTotalityArchitectureTest`).
   Make the runtime-contracts cycle scan see `skillbill.error.*` subpackages. If SKILL-372
   subtask 3's exact-package granularity has landed, enable it for the runtime-contracts
   case with prefix `skillbill.`. Otherwise keep `skillbill.contracts.` and add a second
   `ArchitectureScanSupport.packageCycles` call with prefix `skillbill.error.` on the same
   main root. Either way the baseline stays empty. SKILL-373 does not restructure this
   test class. A bare `skillbill.` prefix does not work:
   `packageImportEdges` keys areas by the first segment after the prefix, so every
   `error.*` package would collapse into one area.
4. **JsonCodec and dependency hygiene (F-006).** `parseObjectOrNull` catches only
   `SerializationException` and `IllegalArgumentException`. It must still return `null`
   for every malformed line and every non-object root. `McpStdioServer` (two call sites)
   and `GovernedReviewEvidenceBridge` depend on that `null` to answer with JSON-RPC parse
   error `-32700`. kotlinx reports malformed text as `JsonDecodingException`, a
   `SerializationException`, so the narrowed catch keeps that behaviour. `parseValue` keeps a single
   conversion path through `parseJsonElementStrict`. Remove
   `implementation(libs.kotlinx.serialization.json)` from `runtime-infra/http` and
   `runtime-engine` if they still import nothing from `kotlinx.serialization` (SKILL-378
   assigns the engine copy here). Leave `runtime-ports` to SKILL-377. Keep `api` in
   `runtime-contracts`.
5. **Records.** Append to `runtime-kotlin/agent/decisions.md`: build-time constants are
   Kotlin constants, not packaged YAML read at runtime (supersedes the SKILL-174 pattern
   in `agent/history.md` 2026-08-09); the kotlinx edge is `api` because `JsonCodec`
   exposes kotlinx types (supersedes 2026-09-06 (e)); the error-package dependency order.
   Update the `skillbill.contracts.*` paragraph of `runtime-kotlin/ARCHITECTURE.md`
   Package Ownership so it no longer describes packaged YAML resources or a split package.

## Acceptance Criteria

1. `runtime-contracts/src/main` contains no `getResourceAsStream`, `getResource`,
   `org.yaml`, or `java.io` reference, and `runtime-contracts/build.gradle.kts` declares no
   task, no `sourceSets` block, and no SnakeYAML dependency.
2. `GoalVerificationBoundaryCaps.kt`, `GoalPlanningDiscoveryExclusions.kt`,
   `IssueKeyShape.kt`, `PackagedContractYamlNumbers.kt`, and the four orchestration YAML
   files named in scope item 1 no longer exist.
3. `GoalPlanningBoundaryBodyResolutionCaps.VERIFICATION` and the discovery caps used by
   `FileSystemGoalPlanningContextDiscovery` equal the values listed in scope item 1, and
   no production file under `runtime-ports`, `runtime-engine`, or `runtime-infra/workflow`
   reads a caps or exclusions value through an `object` that loads resources.
4. The domain exclusions value returns the same `isExcluded` result as the deleted object
   for every case in the retained normalization tests, including `..` escape, backslash
   separators, nested `build/` segments, and the `platform-packs/` root.
5. A repoTest in `runtime-infra/contracts` fails when `issue-key-schema.yaml` `maxLength`
   differs from `MAX_ISSUE_KEY_LENGTH` or `minLength` is not 1.
6. None of the declarations listed in scope item 2 exists, and no production source
   references `InvalidFeatureTaskRuntimePhaseBriefingFramingError`.
7. No file in `skillbill.error.core` imports another `skillbill.error` package, and no
   file in `skillbill.error.featuretask` imports `skillbill.error.shellcontent`.
8. The runtime-contracts package-cycle scan covers every `skillbill.error.*` and
   `skillbill.contracts.*` subpackage at subpackage granularity, and passes against an
   empty baseline. Re-adding an import of
   `skillbill.error.shellcontent` to an `error.core` file makes it fail.
9. The runtime-contracts purity scan rejects a runtime-contracts main file that calls
   `getResourceAsStream` or imports `org.yaml.` or `java.io.`, and passes on the current
   tree.
10. `JsonCodec.parseObjectOrNull` has no `catch (_: Exception)` or `catch (error: Exception)`
    clause, and still returns `null` for malformed JSON text and for a valid non-object
    root.
11. `runtime-kotlin/agent/decisions.md` has entries for the three decisions in scope item 5.
    The packaged-YAML entry names SKILL-349's retention decision and the evidence that
    reverses it.

## Non-goals

- Moving DTOs, `*Keys` objects, `*SchemaPaths`, or helpers to other modules (subtask 2).
- Deduplicating the SQLite shadow key objects (subtask 2).
- Renaming error classes or the `skillbill.error.shellcontent` package; moving any error
  class other than the three named in scope item 3.
- Changing `issue-key-schema.yaml` or any other remaining schema.
- Adding fallback records at `parseObjectOrNull` call sites.

## Dependency notes

- Run on the current tree. It does not wait for another issue.
- If the discarded statement in `RuntimeGoalPlanningProvides.kt` is already gone, skip that line. If it is still there, delete it here.
- `ExcludedRootAgentTreeAbsenceTest` lives in `runtime-contracts/src/repoTest`, which cannot see runtime-domain. Move it to `runtime-infra/contracts/src/repoTest`, which depends on runtime-domain and already runs repository-tree repoTests. Put it, and the new issue-key repoTest, in a package that exists in runtime-infra/contracts main.
- Remove the unused kotlinx declaration from runtime-ports and from runtime-engine in this subtask when those declarations are still unused.
- Edit the architecture suite where it lives.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check` passes, including repoTests and the runtime-core
  architecture tests.
- `:runtime-infra:workflow:test` goal-planning discovery tests and engine
  `FeatureTaskRuntimeFindingVerificationBoundaryMemoryTest` pass with unchanged
  expectations. That is the evidence the constants equal the deleted YAML values.
- `rg -n 'goal-verification-boundary-caps|goal-planning-discovery-exclusions' --glob '!.feature-specs/**' --glob '!**/build/**'`
  returns only `agent/history.md` entries.
- `McpProtocolFramingTest` and `McpStdioServerTest` pass unchanged: a malformed stdio
  or evidence-bridge line still yields a `-32700` response.
- Tests name these bugs: an exclusions path escaping the repository root is treated as
  included; a nested `runtime-kotlin/x/build/` path is walked; the Kotlin issue-key
  maximum drifts from the schema.

## Next path

After this subtask commits, the runtime continues with subtask 2
(`spec_subtask_2_move-single-owner-declarations-to-owners.md`).
