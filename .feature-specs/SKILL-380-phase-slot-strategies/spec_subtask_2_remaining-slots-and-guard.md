# SKILL-380 Subtask 2 - Remaining slots and the no-phase-id guard

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Resolves the rest of investigation F-001 and F-005.

Subtask 1 registered a strategy for every slot. The seven non-variant slots still call
shared step code that branches on phase ids. This subtask moves that code into those
strategies, then enforces that nothing else in the feature-task engine references a
phase id.

**Strategies to fill.** Each strategy's step-specific code moves into its slot package
under `skillbill.engine.featuretask.slot`:

| Slot | Strategy id | What moves in |
| --- | --- | --- |
| preplan | `agent-preplan` | Prose settlement specifics; the ceremony line |
| plan | `agent-plan` | Goal-continuation constraint; `applyPlanningStop` and `FeatureTaskRuntimePlanningStopper` decomposition stop |
| implementation | `implement-then-simplify` | Implement and simplify continuation segments; `implementationContinuationDirective`; simplify scope boundary; `implementation_receipt` / `simplification_receipt` checks |
| audit | `acceptance-audit` | In-phase remaining-criteria retry (`FeatureTaskRuntimeRunLoopAuditRetry`); unchanged-remainder block; audit-to-review checkpoint |
| write_history | `boundary-history` | FINALIZATION briefing field set; `history_result` handling |
| commit_push | `runtime-commit` | `runDeclaredCommitPushCycle`; `FeatureTaskRuntimeCommitPushUpstreamHeadFallback`; commit-push settlement special cases |
| pull_request | `pr-description` | `verifyPrEntryIdentity` readiness gate; `pr_result` handling |

**Prompt tables.**
- Each strategy supplies the task directive for its steps. The composer receives the
  directive as input.
- Delete `phaseDirectives` and every other phase-keyed directive table or `when`
  under `phase/prompt`.
- Shared sections (header, discipline, output contract, retry) stay in the composer.
  They take facts from the strategy's `PhaseStepPolicy` or from explicit parameters,
  not from phase-id comparisons.

**Remaining engine references.**
- Every other phase-id reference under `skillbill.engine.featuretask` moves into the
  owning strategy or becomes a slot or strategy query. This includes:
  - run-state validation and reconstruction special cases
  - output verification and persistence
  - record rejection
  - status-service phase resolution
  - runner launch outcomes and policies
  - continuation outcome projection
  - briefing assembly
  - lifecycle reasons
- Code that needs "is this the review step" asks the resolved profile for the slot
  or strategy that owns the step.

**Goal planning.**
- The goal planning sweep keeps its own flow. It obtains the preplan and plan task
  directives from the default `agent-preplan` and `agent-plan` strategies through the
  registry, not from the deleted table.
- No other goal-runner code changes.

**Guard.**
- Add a rule to `RuntimeEngineBoundaryArchitectureTest`, wherever SKILL-373 has
  placed the suite. Under `runtime-engine/src/main/kotlin/skillbill/engine/featuretask`,
  no file outside the `slot` package tree may reference a `FeatureTaskRuntimePhaseIds`
  constant or a `FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_*` constant.
- The rule asserts it read at least one file. It fails on a synthetic violation
  fixture. It has no baseline and no exemption.
- Update `FeatureTaskAuditRemainingCriteriaPackIndependenceArchitectureTest` for the
  moved audit retry code.

**Docs.**
- Extend the ARCHITECTURE.md "Phase slots and strategies" section with the strategy
  table and the rule the guard enforces.
- Add "Adding a phase strategy" steps: write the class in its slot package, add it to
  the registry provider, then select it in a profile (subtask 3).

## Acceptance Criteria

1. Each of the seven strategies in the Scope table owns the listed behaviour in its `slot` package, and the shared step code it called in subtask 1 no longer contains that behaviour.
2. No phase-keyed directive table or phase-id `when` remains under `skillbill.engine.featuretask.phase.prompt`, and every composed prompt receives its task directive from the running strategy.
3. The goal planning sweep composes preplan and plan prompts from the registered default strategies, and its composed prompt text is byte-identical to the pre-change fixture for the same inputs.
4. No production file under `skillbill.engine.featuretask` outside `skillbill.engine.featuretask.slot` references a phase-id constant.
5. The new guard rule fails on a synthetic file under `featuretask/runloop` that compares a step id to `FeatureTaskRuntimePhaseIds.REVIEW`, reports the number of files it read, and passes on the tree.
6. The subtask 1 byte fixtures for the standalone run and the goal child still match, and composed prompt text for every step matches the pre-change fixtures.
7. ARCHITECTURE.md lists every slot's strategies and states the guard rule and the steps for adding a strategy.

## Non-goals

- Goal-runner code outside the planning-directive call (SKILL-378.3 owns goal-runner shape).
- runtime-domain skeleton data. The graph, projections, transitions, and `PROSE_PHASE_IDS` stay keyed by step id.
- Prompt wording changes, policy value changes, or merging strategies with their step code beyond moving it.
- The profile contract (subtask 3).

## Dependency notes

- Depends on subtask 1.
- If SKILL-378 subtask 3 has landed, its engine visibility rule applies to the new
  packages: strategies are `internal` except what the runtime-core registry provider
  needs.
- If SKILL-373 subtask 3 has moved the architecture suite to `repoTest`, add the rule
  there.

## Validation strategy

The regressions to catch:

- a prompt that loses or reorders a section
- the plan decomposition stop firing for a goal child
- the audit retry losing its unchanged-remainder block
- the commit/push upstream-head fallback not running

What covers them:

- Prompt-text fixtures per step, captured before the change.
- The subtask 1 byte fixtures.
- The existing audit, commit/push, and PR suites.
- The guard's synthetic violation.

Run `cd runtime-kotlin && ./gradlew check`, plus the engine and core suites. Run
`bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_3_workflow-profile-contract.md`.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_2_remaining-slots-and-guard.md
