# SKILL-380 Subtask 1 - Strategy contract, registry, and the two variant slots

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Resolves investigation F-001 (dispatch), F-002 (launch policy), F-003 (quality-gate
mechanics), and the structural half of F-004 and F-005 for code review.

Start on a tree where SKILL-378 subtask 2 has landed. Re-read the investigation's
anchors against that tree first. 378.2 converts the run-loop objects into classes and
moves most cited files.

Before changing code, capture byte fixtures for a standalone run and for a goal
child: workflow snapshot, phase records, ledger, handoff projections, and
`skillbill_feature_task_runtime_finished` payload. Use the existing real-SQLite
run-loop harness. Every later subtask diffs against these fixtures.

**Skeleton (runtime-domain, beside `FeatureTaskRuntimePhaseIds`).**
- `PhaseSlot` is a closed enum with `wireValue`, in this order:

  | Slot | Steps |
  | --- | --- |
  | `preplan` | preplan |
  | `plan` | plan |
  | `implementation` | implement, simplify |
  | `audit` | audit |
  | `code_review` | review, verify_findings, implement_fix |
  | `quality_gate` | build, validate |
  | `write_history` | write_history |
  | `commit_push` | commit_push |
  | `pull_request` | pr |

  Each slot exposes its owned step ids, and a step id maps back to its slot. The
  goal-child rule "no pull_request" is expressed on the skeleton; it replaces the
  `takeWhile { it != PHASE_PR }` in `FeatureTaskRuntimeRunnerPolicies`.
- `PhaseStepPolicy` is a domain value with four fields: mutating, relaunch on invalid
  output, single agent session, read-only idle. Strategies declare it per step. Today's
  values carry over exactly; the investigation lists the mismatches and they stay.

**Contract and registry (runtime-engine, `skillbill.engine.featuretask.slot`).**
- `PhaseStrategy` is one interface, with KDoc. A strategy declares:
  - its slot
  - its strategy id (a wire string)
  - the steps it runs, all owned by its slot
  - its entry step
  - the `PhaseStepPolicy` of each step

  It runs one step, using the existing per-call run facts, and returns the existing
  phase outcome type. Collaborators come through the constructor. Keep the interface
  to what the run loop calls.
- `ResolvedWorkflowProfile` is an in-memory value mapping every slot to a strategy.
  In this subtask it is resolved from the built-in slot defaults plus today's
  quality-gate selection: `BUILD` selects `pack-build`, `VALIDATE` selects
  `agent-validate`. Subtask 3 makes it configurable and durable.
- `PhaseStrategyRegistry` is built by one explicit `@Provides` in runtime-core that
  lists every strategy. It fails with a typed error in the `skillbill.error` taxonomy
  in three cases:
  - two strategies share a `(slot, id)` pair
  - a strategy declares a step its slot does not own
  - a lookup names an unknown pair

  No multibinding, reflection, or classpath scan.

**Dispatch.**
- `FeatureTaskRuntimeRunLoopPlanningBranch.runPreparedPhaseReady` and
  `FeatureTaskRuntimeCurrentPhaseExecutionDeriver` stop switching on phase id. They
  look up the resolved strategy for the step's slot and call it.
- Entering a slot resolves to the selected strategy's entry step. Leaving a slot
  resolves to the next slot's entry step. Transitions, gates, and edges stay declared
  in the domain graph, and ledger and transition bytes stay identical.
- Launch-policy reads (retry budgets, relaunch decisions, mutating reconciliation,
  branch setup, idle policy and 30-minute read-only timeout, generation scoping) come
  from the running strategy's `PhaseStepPolicy`.
- Delete from `FeatureTaskRuntimePhaseWorkflowDefinition`:
  - `MUTATING_PHASES` and `isMutatingPhase`
  - `OUTPUT_RETRY_PHASES` and `retriesOnInvalidOutput`
  - `singleAgentSessionOnly`
  - `GENERATION_SCOPED_PHASE_IDS`

  Delete `NON_FILE_MUTATING_PHASES` from `FeatureTaskRuntimeRunnerPolicies`.
  `PROSE_PHASE_IDS` stays: it is the MCP settlement contract.
- Register strategies for the seven non-variant slots. They call today's step code
  (`runPhaseAttempts`, `runDeclaredCommitPushCycle`, the PR readiness gate and
  attempts) and declare today's policies. Subtask 2 moves that code into them.

**code_review slot: strategy `last-commit-fix`.**
- It owns review, verify_findings, and implement_fix. Move in:
  - the review driver cycle
  - goal review-pass reservation, carry-forward, and caps
  - review-generation invalidation
  - `review_fix` re-entry handling and the remediation checkpoint
  - verify_findings launch specifics (spec-intent section, boundary memory, read-only
    idle)
  - implement_fix coverage relaunch and receipt checks
  - the review parts of output verification
- `FeatureTaskRuntimeReviewDriver` stays as this strategy's collaborator.
  `ApprovingReviewDriverStub` still substitutes it in tests.
- The strategy does not read `CodeReviewExecutionMode` except where today's code does:
  the accounting gate and the resume conflict check.
- Delete the review entries of the phase directive table and
  `FeatureTaskRuntimeReviewExecutionDirective` if a caller census on the post-378.2
  tree shows no production caller.

**quality_gate slot: strategies `pack-build` and `agent-validate`.**
- `pack-build` owns build: `FeatureTaskRuntimeBuildGateCoordinator`, triage, repair
  turns, cache-bypassing verify, `findings_open` persistence, and the absent-gate
  fallback.
- `agent-validate` owns validate: the validation gate coordinator, the shrink loop,
  triage, and the fallback variants.
- Delete `FeatureTaskRuntimeQualityGateRouting`. Projection filtering in
  `FeatureTaskRuntimePhaseWorkflowQueries.phaseDeclarationForQualityGate`, the handoff
  declaration checks, and the status skip rule key on the resolved profile's steps
  instead of the selection enum.
- `FeatureTaskRuntimeQualityGateSelection` stays as the goal-runner and continuation
  wire value. Its `fromWire` fix belongs to subtask 3.

**Docs and decisions.**
- Add a "Phase slots and strategies" section to `runtime-kotlin/ARCHITECTURE.md`: the
  skeleton table, the contract, and how dispatch and policy work.
- Record the decision in `runtime-kotlin/agent/decisions.md`:
  - a fixed slot skeleton with in-process strategies
  - the one strategy interface added on top of SKILL-378.2's step-class rule
  - explicit provider registration
- Update `FeatureTaskRuntimeBoundaryOwnershipArchitectureTest` if it pins the deleted
  sets or routing.

## Acceptance Criteria

1. `PhaseSlot` exists in runtime-domain with the nine wire values in skeleton order. A test proves that every id in `FeatureTaskRuntimePhaseIds.all` belongs to exactly one slot and that every slot owns at least one step.
2. `PhaseStrategy` and `PhaseStrategyRegistry` exist in `skillbill.engine.featuretask.slot`. The runtime-core registry provider lists at least one strategy for every slot, and two for `quality_gate`.
3. Constructing the registry with a duplicate `(slot, id)` pair, or with a strategy that declares a step outside its slot, raises the typed error. Looking up an unknown pair raises the typed error. Each case has a test.
4. `runPreparedPhaseReady` and `FeatureTaskRuntimeCurrentPhaseExecutionDeriver` contain no branch on a phase id, and every step launch goes through a registry lookup.
5. `MUTATING_PHASES`, `isMutatingPhase`, `OUTPUT_RETRY_PHASES`, `retriesOnInvalidOutput`, `singleAgentSessionOnly`, `GENERATION_SCOPED_PHASE_IDS`, `NON_FILE_MUTATING_PHASES`, and `FeatureTaskRuntimeQualityGateRouting` no longer exist, and each strategy's declared `PhaseStepPolicy` equals today's value for its steps.
6. The code-review behaviour listed in Scope lives in the `last-commit-fix` strategy's package, and the build and validate behaviour lives in the `pack-build` and `agent-validate` packages.
7. A goal child whose continuation selects BUILD runs build and not validate, the final goal child runs validate and not build, and a standalone run runs validate. Transitions, ledger entries, and projections match the pre-change fixtures in each case.
8. For the captured standalone and goal-child runs, the workflow snapshot, phase records, ledger, handoff projections, and finished-telemetry payload are byte-identical to the pre-change fixtures.
9. `ARCHITECTURE.md` has the "Phase slots and strategies" section, and `agent/decisions.md` records the decision.

## Non-goals

- Moving the other seven slots' special cases and prompt tables (subtask 2).
- The profile contract, config key, durable freezing, CLI loud-fail, and telemetry field (subtask 3).
- A second review strategy (subtask 4).
- Changing any policy value, loop cap, retry budget, prompt text, or stored byte.
- Goal-runner and goal-planning code.

## Dependency notes

- **Required, in other bundles:** SKILL-378 subtask 1, then SKILL-378 subtask 2. The
  strategies are 378.2's step classes grouped by slot.
- **Recommended first:** SKILL-376.1, SKILL-372.1, SKILL-372.2, and SKILL-377.1. If
  they land after this subtask, they rewrite call sites inside the new slot packages.
- **In flight:** SKILL-379 subtask 2 must be merged. It edits the review-preparation
  files next to the review driver.
- No dependency inside this bundle. Subtasks 2, 3, and 4 depend on it.

## Validation strategy

The regressions to catch:

- a step launched with the wrong policy (idle timeout, relaunch, or mutating
  reconciliation)
- a quality-gate child running both gates or neither
- a `review_fix` re-entry or goal carry-forward lost in the move
- a resumed run whose reconstructed state differs from live state

What covers them:

- The existing run-loop suites over real SQLite cover the loop, gate, and resume
  paths.
- The fixture diff covers bytes.
- New tests: registry construction and lookup failures; the slot-to-step partition; a
  resume-parity check for each migrated slot where the suite has none.
- No structural tests beyond these.

Run `cd runtime-kotlin && ./gradlew check`, plus the engine, core, and infra-sqlite
suites. Run `bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_2_remaining-slots-and-guard.md`.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_1_strategy-contract-and-variant-slots.md
