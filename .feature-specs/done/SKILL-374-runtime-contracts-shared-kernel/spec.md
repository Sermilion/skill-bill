# SKILL-374 - runtime-contracts shared kernel

## Mode

decomposed

## Intended outcome

`runtime-contracts` is the leaf that 14 of the other 15 runtime modules depend on. The
2026-09-22 investigation (`investigation.md`) found its place in the graph right and its
contents wrong:

- Three lazy singletons read YAML from the classpath to deliver values fixed at build
  time.
- Adapter-bound DTOs, 13 `*Keys` objects, and every schema locator have one owner
  elsewhere, once sibling bundles' changes are accounted for.
- Two SQLite-named key objects restate 92 values.
- The error packages form a cycle that the cycle scan cannot see.

150 of the last 897 runtime commits edited the module, and each change to its public
surface recompiles 14 modules.

The outcome: `runtime-contracts` holds the runtime's shared language and nothing else.
That means contract versions and record-identity schema IDs, wire keys and DTOs that two
or more production modules read or a port exposes, `JsonCodec`, and the error kernel. It
performs no I/O. Single-owner content lives with its owner. No module, framework, port,
dependency bag, or architecture-test class is added; no module edge changes; wire output
is byte-identical.

## Findings

Summarized from `investigation.md`, which has the evidence, the SKILL-349 audit, the
checklist, and the guard-validity table.

1. F-001 (P1): the caps, exclusions, and issue-key singletons (468 lines plus 389 test
   lines, three copy tasks, SnakeYAML). They re-implement JSON Schema rules for 30 values
   baked into the jar, while the planning caps for the same fields are already `const val`
   in runtime-ports. This reverses SKILL-349's decision to keep them, on new evidence.
2. F-002 (P2): single-owner DTOs, keys, schema locators, and two helpers.
   `DecompositionPlanningResult` and `ScaffoldPayloadParsing` stay because two or more
   modules read them.
3. F-003 (P2): 92 duplicated values in the two SQLite shadow key objects.
4. F-004 (P2): 15 dead declarations, 43 dead key members, and catch branches for an error
   that is never thrown.
5. F-005 (P1): the error-package cycle (`core`, `shellcontent`, `featuretask`), created by
   SKILL-361 and invisible to the `skillbill.contracts.`-prefixed scan.
6. F-006 (P2): `parseObjectOrNull` catches `Exception`, which SKILL-349 asked to remove.
   There is also a redundant rethrow in `parseValue`, a stale decision about the kotlinx
   `api` edge, and redundant kotlinx declarations in infra:http and runtime-engine (the
   ports copy belongs to SKILL-377).
7. F-007 (P3): documentation drift.
8. F-008 (P3, superseded at planning): `JsonPayloadContract` is not dead. About 30
   signatures in runtime-ports, runtime-application, infra:contracts, and infra:launcher
   use it as the typed carrier that keeps raw maps out of inner-layer signatures, so it
   stays. Likewise, DTOs with a public `toPayload()` map that application or domain code
   builds and CLI or MCP serializes stay in runtime-contracts, because
   `RuntimeRawMapArchitectureTest` forbids public raw maps in application, domain, and
   ports.
9. F-009 (P3): the `skillbill.contracts.workflow.workflow` stutter and the `JsonSupportTest`
   name.

What stays unchanged, with reasons, is in the investigation. In short: the module graph;
contract versions; the error kernel and its names; `JsonCodec`; `Map<String, Any?>` plus
`*Keys` as the wire representation; `DecompositionPlanningResult` and its decoder;
`ScaffoldPayloadParsing`; the issue-key functions; `FailureWireCode`; `JvmSystemClock`;
`issue-key-schema.yaml`.

## Target design

- Placement rule, written into `../../../AGENTS.md` and `runtime-kotlin/ARCHITECTURE.md`: a
  declaration lives in `runtime-contracts` when two or more production modules read or
  write it, or a `runtime-ports` signature exposes it. Otherwise it lives with its one
  owner. A DTO with a public `Map<String, Any?>` member never moves into application,
  domain, or ports. Schema locators belong to `runtime-infra/contracts`, which stages the
  resources. Wire keys are still declared exactly once. `JsonPayloadContract` stays as the
  typed payload carrier.
- `runtime-contracts` main has no classpath, filesystem, or YAML access, and the existing
  purity scan enforces it. Its build file has no task and no SnakeYAML.
- Verification caps are `const val` beside the planning caps in `GoalPlanningContext`.
  Discovery exclusions are a pure runtime-domain value. `MAX_ISSUE_KEY_LENGTH` is
  `const val` 128, pinned to `issue-key-schema.yaml` by a repoTest.
- Error packages depend in one direction: `core` <- `featuretask` <- `shellcontent`. The
  runtime-contracts cycle scan covers every `skillbill.error.*` subpackage.

## Acceptance Criteria

1. The runtime-contracts purity scan in `RuntimeArchitectureTest` bans `org.yaml.`,
   `java.io.`, and `getResourceAsStream` in runtime-contracts main and passes, and
   `runtime-contracts/build.gradle.kts` declares no task and no SnakeYAML dependency.
2. The caps, exclusions, and issue-key loader files, `PackagedContractYamlNumbers.kt`, and
   the caps and exclusions YAML files and their two schemas no longer exist. The effective
   cap values, excluded roots, excluded directory names, and issue-key maximum are
   unchanged.
3. A repoTest fails when `issue-key-schema.yaml` `maxLength` differs from
   `MAX_ISSUE_KEY_LENGTH` or `minLength` is not 1.
4. No declaration listed in investigation F-004 exists, and no production source catches
   `InvalidFeatureTaskRuntimePhaseBriefingFramingError`.
5. The runtime-contracts package-cycle scan covers every `skillbill.contracts.*` and
   `skillbill.error.*` subpackage, its baseline is empty, and it passes.
6. Each item subtask 2 scope item 1, 2, or 4 names as moving is declared in the named
   target module. Every remaining runtime-contracts main declaration outside
   `skillbill.error`, `skillbill.contracts.experiment`, and `*_CONTRACT_VERSION` meets the
   placement rule.
7. `SqliteLifecycleTelemetryMaterializationPayloadKeys` and
   `SqliteReviewTelemetryPayloadKeys` declare no value that a shared owner named in
   investigation F-003 declares. The governed-key set for each SQLite seam in
   `WireVocabularyGovernedSeamInventory` is unchanged.
8. `JsonPayloadContract` still exists in runtime-contracts, and no declaration moved into
   application, domain, or ports main has a public `Map<String, Any?>` member.
9. No package named `skillbill.contracts.workflow.workflow` exists.
10. `../../../AGENTS.md`, `runtime-kotlin/ARCHITECTURE.md`, and `runtime-kotlin/agent/decisions.md`
    state the placement rule and describe `runtime-contracts` as it now is.
    `decisions.md` supersedes the 2026-05-28 "`*SchemaPaths` stay in runtime-contracts"
    clause for locators, the 2026-09-06 (e) kotlinx clause, and the SKILL-174
    packaged-YAML pattern, and records why `JsonPayloadContract` and the `toPayload()`
    DTOs stay.

## Executable scope

Two subtasks, split to keep review readable.

- **Subtask 1 is semantic.** It replaces runtime reads with constants, deletes four
  orchestration contract files and dead catch paths, narrows a catch, and fixes the cycle
  and its guard. Review asks whether values and error paths are preserved.
- **Subtask 2 is a mechanical relocation** across about ten modules with no behaviour
  change. Review asks whether each item landed with its owner and whether the rule text
  matches.

Mixing them would bury the semantic changes in hundreds of import edits. Subtask 2 also
needs subtask 1's deletions first, or it would move code that is about to be deleted.

1. Remove ambient loaders, dead code, and the error-package cycle
   (`spec_subtask_1_remove-ambient-loaders-dead-code-and-error-cycle.md`).
2. Move single-owner declarations to their owners
   (`spec_subtask_2_move-single-owner-declarations-to-owners.md`), after 1.

## Dependency notes

This bundle runs on the current tree. It does not wait for a subtask of another issue.

- Do the work in these acceptance criteria here. Move the four experiment `*SchemaPaths`
  locators with the other locators; leave every other experiment declaration for SKILL-378
  subtask 1.
- Edit the architecture suite where it lives. If a scanner this bundle relies on still reads zero files, repair that scanner here.
- The cycle-guard fix uses exact-package granularity when that mode exists, and a second prefix scan when it does not. Add the mode here if the criterion requires it and it is missing.
- Keep `UpdateCheckContract` in runtime-contracts. CLI and MCP both consume it from there if they do not already.
- If the discarded statement at `RuntimeGoalPlanningProvides.kt` is already gone, skip it. If it is still there, delete it here.
- Place MCP-only output keys with the placement rule in force. If they sit in runtime-contracts and belong in runtime-mcp, move them here.
- Read `../../../runtime-kotlin/ARCHITECTURE.md` Design Principles and `docs/code-principles.md`
  before changing Kotlin. No `//` comments, KDoc only on interfaces, 500-line production
  ceiling, no inline FQNs, and package sibling limits.

This bundle does not wait for SKILL-380. If SKILL-380 has added `WorkflowProfilePayloadKeys`, subtask 2's census keeps them.

## Non-goals

- `@Serializable` DTOs or any change to the `Map<String, Any?>` wire representation.
- Moving, renaming, or regrouping error classes beyond the three moves in F-005.
- Consolidating map-reader helpers, including `DecompositionPlanningResult`'s decoder.
- Changing any wire value, contract version, or remaining schema file.
- Deleting `JsonPayloadContract` or redesigning payload types across modules.
- Repairing vacuous guards that this bundle's criteria do not depend on.
- Adding fallback records at the 59 `parseObjectOrNull` call sites.
- Typing the `IllegalArgumentException` that `normalizeIssueKey` throws.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check` runs every module gate, repoTests, and the
  runtime-core architecture tests.
- Goal-planning discovery tests in infra:workflow and engine pass with unchanged
  expectations, which shows the constants equal the deleted YAML values.
- CLI and MCP output tests for learning, review, triage, doctor, version, update check,
  and lifecycle telemetry pass unchanged, which shows the relocated DTOs emit the same
  maps.
- The investigation's census scripts, re-run on the result, report no single-owner item
  left in runtime-contracts.

## References

- `runtime-kotlin/runtime-contracts/**`, `../../../runtime-kotlin/runtime-contracts/agent/history.md`
- `../SKILL-349-runtime-contracts-boundaries-and-simplicity`
- `../../../runtime-kotlin/runtime-ports/src/main/kotlin/skillbill/ports/goalrunner/planning/model/GoalPlanningContext.kt`
- `runtime-kotlin/runtime-core/src/test/kotlin/skillbill/architecture/{RuntimeArchitectureTest,RuntimeArchitectureTestSupport,ApplicationPackageAcyclicityArchitectureTest,PrincipleEnforcementInventory,WireVocabularyGovernedSeamInventory,FailureCodeTotalityArchitectureTest}.kt`
- `orchestration/contracts/{goal-verification-boundary-caps,goal-planning-discovery-exclusions,issue-key-schema}*.yaml`
- `../../../runtime-kotlin/agent/decisions.md`, `runtime-kotlin/agent/history.md`, `AGENTS.md`,
  `runtime-kotlin/ARCHITECTURE.md`

## SKILL-380 coordination

No subtask of this bundle is a SKILL-380 prerequisite, and this bundle may land before
or after SKILL-380. SKILL-380 adds `WorkflowProfilePayloadKeys` and
`FeatureTaskRuntimeRunInvariantsPayloadKeys` to runtime-contracts. Two or more
production modules read each, so both satisfy this bundle's placement rule, and subtask
2's census keeps them. SKILL-380 also adds a runtime contract,
`workflow-profile-schema.yaml`, whose locator follows this bundle's rule if subtask 2
lands first.

## Next path

Run `skill-bill goal SKILL-374`.
