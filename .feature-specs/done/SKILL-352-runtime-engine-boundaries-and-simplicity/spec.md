# SKILL-352 - runtime-engine-boundaries-and-simplicity

## Mode

decomposed

## Intended outcome

Make the feature-task run loop's helpers take the facts and ports they use instead of the whole loop, replace the module's parameter bags with named collaborators or plain parameters, delete the role interfaces nobody is typed by, give the `artifactsJson` column one engine-side owner with keys declared once, route every durable decode and every fallback through typed failures or a bounded record, return the thirty engine-only application types to the engine, and make the module internal by default, while keeping the module graph, the pinned inbound API, phase order, review policy, and successful durable reads and writes unchanged.

## Scope

The investigation covers all 298 production Kotlin files in runtime-engine, its tests and fixtures, its build script, the architecture guards and baselines that scan it, the recorded decisions that govern its shape, and the consumers named per finding. See [investigation.md](investigation.md) for thirteen findings, the principles assessment, rejected refactors, public engineering references, the test baseline, the census methods, and limits.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-001, F-002, F-007 | Helpers take narrow inputs, bags dissolve into parameters or collaborators, bundles leave `model`, the census is complete and guarded | 1 |
| F-003, F-004, F-009, F-010 | Role interfaces deleted, one artifact-column owner, typed failures at every durable decode seam, keys declared once | 2 |
| F-005, F-006, F-008, F-011, F-012 | Every fallback recorded or failed, one best-effort write path, engine-only types moved home, internal by default, closed values typed at cited seams | 3 |

Three subtasks ship independently. The first changes how the run loop is wired without changing what it persists. The second changes persistence ownership and failure identity without changing successful wire bytes. The third records, merges, moves, and narrows without changing behaviour. Each carries its own tests and documentation. They touch overlapping files under `featuretask/`, so each subtask rebases on the branch head before it starts; subtask 3's census runs after 1 and 2 land.

Prepared in local mode on 2026-09-16. SKILL-352 follows SKILL-351, the highest existing local spec key; the user authorised selecting the next available key. Baseline HEAD is `3f6aaff18318483771900c96b5e7cd854e7e8259` with only the SKILL-351 spec directory untracked; the sorted production-file digest is `31fd93acf09e4e6aee2f62414a956689034a0168d5417b182599de6d870bba30`. This bundle prepares work only; all subtasks start pending.

## Acceptance Criteria

1. The count of `FeatureTaskRuntimeRunLoopContext` extension functions across every `FeatureTaskRuntimeRunLoop*.kt` file falls from 85 to the retained orchestration seams (forward drive loop, review-generation invalidation, launch capture, gate-cycle and fix-loop orchestration, review driver execution), and every other helper takes request, state, recorder, goal-recorder, diagnostics, clock, or session as explicit parameters. An architecture test owns the per-file census and asserts the total does not grow. `ARCHITECTURE.md` states the helper-input principle only.
2. `phaseTokenAccumulator` is no longer a `MutableMap` passed through the context or any bag; `FeatureTaskRuntimeRunState` owns phase-token accounting behind a named transition and a read-only view. `ValidationSettlementState` no longer exposes `MutableSet` fields. `attempted` in the goal runner is a read-only list or owned by one loop state.
3. `*Args` classes constructed at one site and carrying only facts are dissolved into parameters; those carrying ports or services become named collaborators with those ports as constructor dependencies; `BuildDeclaredGoalProgressEventArgs` is deleted. `GoalRunnerLedgerContext` is a sealed hierarchy of ledger actions. The surviving bag count and the `LongParameterList.functionThreshold` value are set by one recorded decision in `../../../runtime-kotlin/agent/decisions.md`, not by adding suppressions or baselines.
4. No `@Inject data class` of services or ports lives under a `model` package; `GoalRunnerDeps` and the six `get()` forwarders in `GoalRunner` are deleted; `featuretask.model → featuretask` and `goalrunner.model → goalrunner` import edges are zero and the acyclicity scan covers the engine at sub-area granularity.
5. The ten `FeatureTaskRuntimePhase*Api` interfaces and `FeatureTaskRuntimePhaseRecorderParts` are deleted. Every current call on `FeatureTaskRuntimePhaseRecorder` compiles unchanged or against a named concrete recorder member. No new interface with one implementor is introduced.
6. `record.artifactsJson` is decoded in one engine file. The other 18 files read and write artifacts through typed operations on the persistence owner that decode once and patch once. `state.patch["…"] = …` raw-map mutation in the child repair loop is replaced by a typed write. Existing rows decode to the same artifacts, proven by round-trip fixtures per artifact family.
7. `GoalPlanningSharedContextPacket.migrate`/`validate`, `GoalPlanningSharedContextPacketValidation`, and `decodeGoalAgentAddonSelection` report malformed durable input through their families' typed errors; `require`, `error`, `check`, and `IllegalArgumentException` no longer escape those seams. `FeatureTaskRuntimeWireMapping.kt` and the three `catch (IllegalArgumentException)` recoveries are deleted in favour of the shared reader from SKILL-351 or, if that has not landed, an `internal` engine reader marked for replacement. The 12 `!!` after `decode*FromArtifact` are removed through non-null decoder overloads. `RuntimeOwnedFactUnavailable`, `MissingCarriedForwardGoalReviewResultException`, and `GoalRunnerExecutionAlreadyRunningException` extend the runtime exception taxonomy, not `IllegalStateException`.
8. Every engine encode or decode of a durable artifact references an owning `*Keys` object for each governed key; `PACKET_FIELDS`, `isImplementationReturnContract`, and `GoalContinuationArtifactCodec` no longer inline keys another owner declares; the governed-seam inventory scans these seams. Prose, prompt text, and pack-authored payloads remain open.
9. The attempt-ledger read, the two execution-liveness resolutions, and the repair coordinator's ownership read either fail typed at their projection or emit one bounded record naming seam, value expected, and value used, and the status projection reflects the degraded read rather than zero counts. The diagnostics-port guard (`runCatching { diagnostics.warning(…) }`) has one owner that emitters and coordinators call.
10. `GoalRunnerProgressEventEmitter` and `GoalRunnerObservabilityEmitter` share one best-effort recorder with one cancellation and interruption rule and one message cap. `topLevelJsonObjectCandidates` is deleted or delegated to the adapter that owns agent-output parsing; no fourth brace scanner exists.
11. The 30 application types with no production consumer outside the engine live in the engine area that produces them; the CLI and MCP still reach IDE status through the pinned inbound API. An architecture test owns the remaining shared application edge; `ARCHITECTURE.md` does not inventory those types.
12. After a re-run census, same-file-only public declarations are `private`, module-only declarations are `internal`, unreferenced declarations are deleted, and an architecture check fails a new public engine declaration outside the pinned inbound API and `model` packages. All modules compile and their tests pass.
13. At the cited seams, lease and ownership timestamps are parsed once at the port boundary with a typed failure, and liveness class, worker role, and planning disposition are enums with `wireValue` converted only at the wire seam. Snapshot and observability payloads are byte-identical for supported values.
14. `ARCHITECTURE.md` states helper-input, bag, persistence, key-ownership, shared-edge, and scanner principles without file inventories or spec issue keys. Architecture tests own the corresponding censuses and scanner coverage. Existing architecture guards keep their protection with no new baseline rows, exemptions, or suppressions.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles, docs/code-principles.md, docs/observability-policy.md, and AGENTS.md. Keep the eleven Gradle modules, the composition root, the pinned engine inbound API, the `api(...)` exposure through `runtime-core`, and the engine → application, ports, domain, contracts edges.
- Respect recorded decisions: the `*Boundaries` collapse (revisit condition unchanged), the 2026-09-04 guard recalibration (ceilings move by decision, never by baseline), and the 2026-09-15 SKILL-247 subtask 3 helper-input rule. Any threshold change lands as a decision entry with its reason.
- No new module, DI graph, generic workflow or state-machine framework, result framework, port per artifact key, or composite port. One production adapter or one test substitute justifies an interface; nothing less.
- Preserve phase order, review policy, checkpoint and commit semantics, telemetry semantics, and successful durable reads and writes byte for byte. Only malformed input changes its failure identity; quarantine and regeneration paths keep working and gain coverage for the newly typed failures.
- Keep `require` on constructor invariants of typed value classes; only decode seams translate them.
- Do not split files by count, add `*Helpers`-style siblings under other names, or hide dependencies in bags to satisfy a threshold. Ceilings are 1,200 lines and 40 functions; merge or split by responsibility under them.
- Deletion follows a fresh reference census plus compilation and the full runtime-kotlin test suite; the recorded census is evidence, not authority. Generated or reflective consumers are invisible to text matching.
- Re-read the owning documents and current source hashes before each subtask. Concurrent SKILL-351 work in the shared checkout is outside this spec; subtask 2 names the seam where it depends on SKILL-351 subtask 1.

## Non-goals

- Splitting `runtime-engine` into per-area modules, renaming the `FeatureTaskRuntime*` prefix, or renaming the 22 run-loop files.
- A rewrite of the run loop, goal runner, planning sweep, or status projection; new product behaviour; changes to the phase graph, backward edges, or commit structure.
- Moving the ten recorders behind `runtime-ports` or replacing SQLite unit-of-work callbacks with a new repository layer.
- Replacing every `Map<String, Any?>` in the module with typed models in one pass. Subtask 2 fixes the column owner and the reading seam; typed replacement continues only where a finding names the map.
- Splitting or rewriting the test suites beyond the classes a subtask touches.
- Certification against private Reddit, Microsoft, or Meta standards.

## Validation strategy

For helper narrowing, assert the extension census through its architecture test and drive the run loop end to end over real SQLite with the existing runner test support so phase order, checkpoint identity, and resume agree with durable state before and after. For bag dissolution, rely on compilation plus the run-loop and goal-runner suites; name the regression each changed test guards. For persistence ownership, round-trip every artifact family through the new owner and diff encoded bytes; exercise malformed durable fixtures against the two goal-planning decoders and assert the typed error reaches the quarantine or block path. For fallbacks, assert the diagnostic record fields or the typed failure at each cited seam and that the status projection shows the degraded read. For ownership moves, assert relocated behaviour through the new owner and the absence of the old import edge at sub-area granularity. For the surface reduction, rely on compilation of all modules plus `./gradlew check` on runtime-kotlin rather than reference greps. Run the engine suite, affected consumer suites (`runtime-core` architecture guards, `runtime-cli`, `runtime-mcp`, `runtime-application`), the pack-declared quality gate during implementation, and bill-unit-test-value-check for changed tests. The preparation baseline (938 tests passing) is evidence about `main`, not a future review receipt.

## Next path

Run `skill-bill goal SKILL-352` when implementation is intended. The prepared manifest is the goal runner's input.
