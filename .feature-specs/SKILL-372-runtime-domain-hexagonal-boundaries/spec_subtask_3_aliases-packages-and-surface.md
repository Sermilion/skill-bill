# SKILL-372 Subtask 3 - Acyclic packages, vocabulary, aliases, and surface

Parent spec: [.feature-specs/SKILL-372-runtime-domain-hexagonal-boundaries/spec.md](spec.md)
Issue key: SKILL-372

## Scope

Resolve F-004, F-010, F-011, F-012, F-013, and F-014 in [investigation.md](investigation.md). This subtask is mostly mechanical moves and renames, kept separate so subtasks 1 and 2 stay readable.

**Cycles (F-004).**

- Move the shared vocabulary between `workflow.goal` and `workflow.taskruntime` (the durable artifact reader, `toStringKeyedArtifactMap`, and the goal-review state types) to `workflow.model` or whichever lower sub-area both use, so only one direction remains.
- Merge `review.finding` into `review.parsing`.
- Resolve the `review.attribution`/`review.model`/`review.context.model.*` and `experiment` cycles the same way.
- Remove the 24 model → logic edges.
- Add an opt-in exact-package mode with strongly-connected-component detection to `ArchitectureScanSupport.packageImportEdges`/`packageCycles`, and use it for runtime-domain against the existing empty baseline. The mode is selected per scan case. The default stays first-segment and mutual-pair, so other modules' scans keep their current granularity. Add the mode in `ApplicationPackageAcyclicityArchitectureTest` wherever that class lives. If the `experiment` cycle is still present, break it in this commit so the empty baseline holds.

**Skill kind (F-010).**

- Add `enum class SkillKind(val wireValue: String)` in `scaffold.model` with one `fromWire`, replacing `SKILL_KIND_*` and `SUPPORTED_SKILL_KINDS`.
- Migrate the literals in infra/skills and CLI; CLI alias normalisation returns the enum.
- Apply the same treatment to `APPROVED_CODE_REVIEW_AREAS` while it stays closed.

**Aliases (F-011).** Replace the remaining runtime-domain typealiases and engine's `GoalContinuation` alias with the real types. Delete the alias-only files.

**Packages (F-012).**

- Merge into `workflow.taskruntime.model.persistence`, `…model.handoff`, and `…model.repair`.
- Move `skillbill.domain.skillremove` to `skillbill.skillremove`.
- Move the 9 orphan test files into their subject's package.
- Rename `review/attribution/ReviewIssueCategory.kt` and `learnings/LearningEntry.kt` by content.
- Update guard inventories and docs.

**Surface (F-013).**

- Re-run the FQN census.
- Delete unreferenced declarations, keeping domain `normalizedBlockedReason`.
- Make same-file-only declarations `private` and module-only ones `internal`.
- Move test-only external declarations into `testFixtures`, or note why they stay public.
- Narrow the catch in `GoalSubtaskReviewStructuredFindingsParse.kt:123` to the path-validation exception.

**Resource (F-014).**

- Delete `runtime-domain/src/main/resources/skillbill/version.properties` and the `processResources` block.
- Pass the version from `RuntimeBootstrapBindings` into `SQLiteDatabaseSessionFactory` and on to `TelemetryOutboxStore`, using SKILL-373's single-field version type in runtime-ports `skillbill.model` if it has landed, otherwise `String` (373 converts it).
- Delete `SkillBillRuntimeVersion` and the SQLite resource copy. SKILL-376.3 moves `ReviewTelemetryState.kt` and `LifecycleTelemetryEmit.kt`; follow their new location.

## Acceptance Criteria

1. runtime-domain has no package cycle at exact package granularity, and no `model` package imports a non-model package.
2. The acyclicity scanner fails on a synthetic three-package cycle in the runtime-domain exact-package mode, passes on the real tree with an empty runtime-domain baseline, and leaves every other module's cycle results unchanged.
3. Skill kind is an enum with one `fromWire`. No main source outside runtime-domain restates a skill-kind literal except CLI user-alias input, and skill-kind wire strings are unchanged.
4. No typealias in runtime-domain main source, and no engine typealias targets a runtime-domain type.
5. No two runtime-domain production files share a basename, no package path repeats a segment, `skillbill.domain.skillremove` does not exist, and every domain test file's package exists in main.
6. The re-run census reports zero unreferenced (other than `normalizedBlockedReason` if SKILL-370 has not landed), zero same-file-only, and zero module-only public declarations in runtime-domain.
7. runtime-domain has no version resource or `processResources` block. infra-sqlite has no version resource or reader, and outbox rows record the injected version.
8. Sibling-count, clustering, and line and function ceiling guards pass with no new exemption or baseline row. All runtime-kotlin modules compile and the full suite passes.
9. `ARCHITECTURE.md` and area docs name the new package paths and state the cycle scan's granularity.

## Non-goals

No behaviour change beyond compile-time typing of skill kind. No alias removal for port or application types. No exact-granularity cycle baselines for other modules. No change to sibling-count thresholds. No restructuring of the acyclicity test class (SKILL-373).

## Dependency notes

Depends on subtasks 1 and 2, which edit the same files and packages. Rebase on this bundle's branch head. Use the acyclicity test class where it lives. Do not wait for another issue.

## Validation strategy

- Compile every module and run the full runtime-kotlin suite.
- Run the corrected cycle scanner on the real tree and on a synthetic three-package cycle.
- Round-trip every skill-kind wire value through `fromWire`, and assert outbox rows carry the injected version.
- Run the guards named in criterion 8 and the governed quality gate.
- Delete tests whose only subject was deleted, and apply `bill-unit-test-value-check` to changed tests.

## Next path

Final subtask. Completion closes SKILL-372 through the goal runtime.

## Spec Path

.feature-specs/SKILL-372-runtime-domain-hexagonal-boundaries/spec_subtask_3_aliases-packages-and-surface.md
