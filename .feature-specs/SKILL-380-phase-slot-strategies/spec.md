# SKILL-380 - Phase slot strategies

## Mode

decomposed

## Intended outcome

The feature-task runtime runs a fixed skeleton of phase slots, in this order:

1. pre-plan
2. plan
3. implementation
4. audit
5. code review
6. quality gate
7. write history
8. commit/push
9. pull request

A swappable phase strategy fills each slot. A workflow profile, validated against a
governed schema and frozen into the run, picks one strategy per slot.

Today how a phase runs is decided by comparing phase ids in shared code:

- the run loop's `when (run.phaseId)`
- domain sets such as `MUTATING_PHASES`
- phase-keyed prompt tables

Two real strategy switches exist, but neither is modelled as a strategy. Quality-gate
selection rewrites transitions. Review is a DI-global driver.
[investigation.md](investigation.md) has the census:

- 505 phase-id references in 83 production files
- per-phase launch shapes
- the existing switches
- the 2026-09-11 review replacement
- which prepared bundles must land first

After this bundle:

- Each slot's behaviour lives in strategy classes behind one contract.
- The run loop asks the registry for the strategy and never branches on a phase id.
- Adding a strategy means one class, one registry entry, and one profile value.
- The default profile reproduces today's runs byte for byte.
- Only variants that exist today ship:
  - build and validate as two quality-gate strategies
  - the specialist findings review as a second code-review strategy (last subtask)

Out of scope: plugin loading, a generic workflow framework, and new step ids.

## Findings

| Finding | Priority | Summary | Subtask |
| --- | --- | --- | --- |
| F-001 | High | Phase behaviour dispatched by identity in the run loop (`when (run.phaseId)`, 250 engine references) | 1, 2 |
| F-002 | High | Launch rules in domain phase-id sets, drifted from behaviour | 1, 2 |
| F-003 | High | Quality-gate selection implemented as transition rewriting; unknown values silently become VALIDATE | 1, 3 |
| F-004 | Medium | Review seam global and not durable; `CodeReviewExecutionMode` inert for the wired driver | 1, 4 |
| F-005 | Medium | Phase-keyed prompt tables; review directives likely unreachable | 1, 2 |
| F-006 | Medium | No durable or telemetry record of how a slot ran | 3 |
| F-007 | Low | `AGENTS.md` build paragraph disagrees with the three repair turns in code | Open question, not changed |

## Acceptance Criteria

1. runtime-domain declares a closed `PhaseSlot` enum with wire values `preplan`, `plan`, `implementation`, `audit`, `code_review`, `quality_gate`, `write_history`, `commit_push`, and `pull_request`, in that order. Each slot names the existing step ids it owns, and every step id belongs to exactly one slot.
2. runtime-engine declares one `PhaseStrategy` contract. Every slot has at least one registered strategy, and a strategy runs only step ids its slot owns.
3. The feature-task run loop selects step behaviour by asking the registry for the strategy of the step's slot. No production file under `skillbill.engine.featuretask` outside `skillbill.engine.featuretask.slot` references a phase-id constant, and an architecture rule enforces this.
4. Launch policy per step (mutating, relaunch on invalid output, single session, read-only idle) comes from the running strategy. The phase-id sets in `FeatureTaskRuntimePhaseWorkflowDefinition` and `FeatureTaskRuntimeRunnerPolicies` no longer exist.
5. Build and validate are two strategies of the `quality_gate` slot. `FeatureTaskRuntimeQualityGateRouting` no longer exists, and goal children keep today's rule: build for every subtask except the last non-skipped one, validate for the last.
6. A governed `workflow-profile` contract (schema, Kotlin version constant, parity test, `InvalidWorkflowProfileSchemaError`) defines the slot-to-strategy map. `.skill-bill/config.yaml` may set it under `workflow_profile`.
7. The resolved profile is validated against the registry and frozen into run invariants at preparation. An unknown slot or strategy fails with a typed error before any phase launches. Resume uses the frozen profile, and an explicit override that conflicts with it blocks.
8. An unknown quality-gate selection value fails as a usage error instead of becoming VALIDATE.
9. `skillbill_feature_task_runtime_finished` reports the run's slot-to-strategy map.
10. Under the default profile, workflow snapshots, phase records, ledger entries, handoff projections, and telemetry payloads other than the new profile field are byte-identical to pre-change fixtures for standalone runs and goal children.
11. A `specialist-findings` code-review strategy reviews through `ParallelCodeReviewRunner`, hands its findings to verify_findings and implement_fix, honours `CodeReviewExecutionMode`, and a profile can select it.
12. `runtime-kotlin/ARCHITECTURE.md` and `AGENTS.md` describe the slot skeleton, the strategy contract, the profile, and how to add a strategy. `runtime-kotlin/agent/decisions.md` records the decision that adds the slot-strategy contract on top of SKILL-378's step-class rule.

## Executable scope

Four subtasks, one commit each.

1. **Strategy contract, registry, and the two variant slots**
   (`spec_subtask_1_strategy-contract-and-variant-slots.md`).
   - Adds `PhaseSlot`, the step launch policy, the `PhaseStrategy` contract, the
     registry, and registry dispatch for every step.
   - Replaces the domain phase-id launch sets with per-step policy.
   - Fully migrates `code_review` (a single last-commit strategy owning the
     `review_fix` loop) and `quality_gate` (`pack-build` and `agent-validate`).
   - These two slots carry the most special-casing (176 references) and prove the
     contract on a loop-owning slot and a two-strategy slot.
   - The other seven slots get registered strategies that still call today's step
     code, so the commit stands alone.
2. **Remaining slots and the no-phase-id guard**
   (`spec_subtask_2_remaining-slots-and-guard.md`).
   - Migrates preplan, plan, implementation, audit, write_history, commit_push, and
     pull_request.
   - Removes the phase-keyed prompt tables and domain sets.
   - Turns on the guard.
   - Split condition: applying the contract to seven slots after it is fixed. It is
     large and mostly mechanical, and the guard can only go live when zero references
     remain.
3. **Workflow profile contract and durable selection**
   (`spec_subtask_3_workflow-profile-contract.md`).
   - A new runtime contract: schema, config key, run-invariant freezing, resume
     pinning, the loud quality-gate override, and the telemetry field.
   - Split condition: a new governed contract and durable-state change. It needs the
     contract and persistence reviewer, and it cannot be specified until every slot's
     strategy ids exist.
4. **Specialist findings review strategy**
   (`spec_subtask_4_specialist-review-strategy.md`).
   - A second `code_review` strategy over `ParallelCodeReviewRunner`.
   - Split condition: a user-visible feature that ships separately. It must first
     reproduce and fix the 2026-09-11 findings-only hang. Subtasks 1–3 are complete
     without it.

## Prerequisites in other bundles

From [investigation.md](investigation.md), "Which prepared bundles to implement before
SKILL-380":

- **Required:**
  - SKILL-378 subtask 1 (experiment removal)
  - then SKILL-378 subtask 2 (feature-task step classes). The strategies are 378.2's
    step classes, grouped by slot.
- **Recommended first, because they rewrite the same feature-task call sites:**
  - SKILL-376 subtask 1 (required by 377.1)
  - SKILL-372 subtasks 1 and 2
  - SKILL-377 subtask 1
- **In flight:** SKILL-379 subtask 2 finishes and merges first.
- **Not needed:** every other subtask of SKILL-371 to SKILL-378. Each bundle's
  "SKILL-380 coordination" section states its rebase note.

Order:

1. SKILL-379.2
2. SKILL-378.1
3. SKILL-376.1
4. SKILL-372.1
5. SKILL-372.2
6. SKILL-377.1
7. SKILL-378.2
8. SKILL-380.1
9. SKILL-380.2
10. SKILL-380.3
11. SKILL-380.4

## Constraints

- **Read first:**
  - `runtime-kotlin/ARCHITECTURE.md` Design Principles
  - `docs/code-principles.md`
  - `docs/observability-policy.md`
  - `AGENTS.md`
  - `docs/skill-source-generation.md`, only if prose outside runtime-kotlin changes
- **Kotlin style:**
  - no `//` comments
  - KDoc only on interfaces
  - 1,200-line and 40-function ceilings
  - package sibling ceilings
  - no inline FQNs
  - wire keys from owning `*Keys` objects
  - enum wire tokens via `wireValue`
- **SKILL-378.2's rules stay in force:**
  - no top-level function objects in `featuretask/runloop`
  - no collaborator-carrying `*Args`/`*Inputs`/`*Context` classes
  - at most six parameters per function
  - private inject properties

  Strategies take collaborators through constructors and per-call facts as
  parameters.
- **Skeleton limits.** The skeleton is fixed. Strategies are in-process classes
  registered by one explicit `@Provides`. There is no reflection, classpath scan, or
  multibinding.
- **Durable bytes.** No new step id, transition edge, or projection contract. Stored
  bytes under the default profile stay identical. The only additions are the frozen
  profile in run invariants and the telemetry profile field.
- **Loud failure.** Missing or unknown profile entries fail loudly with typed errors.
  Every fallback emits a record.
- **No speculation.** No speculative strategies, config knobs, or extension points
  beyond this spec.
- **Anchors.** Recheck every file anchor at the start of each subtask. SKILL-378.2
  moves most of the cited engine files.
- Use a local clone, not a linked worktree, for Spotless.

## Non-goals

- Making goal-level planning (the goal planning sweep) swappable, or giving goal
  children a planning strategy other than the imported records.
- Restructuring goal-runner code that reads phase records by step id (SKILL-378.3
  territory).
- Correcting today's mutation flags for audit, validate, write_history, or review, or
  resolving the build repair-turn doc drift (F-007).
- External or pack-provided strategies, per-subtask profile switching, a generic
  per-slot CLI override, and custom slots or slot order.
- New strategies beyond the specialist findings review.
- A YAML schema for the whole run-invariants artifact (only its new field and its key
  ownership change here).

## Validation strategy

- **Behaviour baseline.**
  - The existing run-loop and goal-runner suites over real SQLite: phase order,
    backward edges, checkpoint identity, resume from durable records, review and gate
    settlement, commit finalization, status projection.
  - Before subtask 1, capture byte fixtures of snapshots, phase records, ledger, and
    telemetry for a standalone run and a goal child. Every subtask diffs against them.
- **Per subtask:** `cd runtime-kotlin && ./gradlew check`, plus the engine, core,
  CLI, MCP, and infra suites.
- **Guards.** New architecture rules get a synthetic violation that must fail, and
  must assert they read at least one file per scanned root.
- **Contract tests.** Parity for the new contract. Rejection tests for an unknown
  slot, an unknown strategy, a wrong contract version, and a conflicting resume
  override.
- **Test review.** Changed tests go through `bill-unit-test-value-check`. The
  validate phase runs the pack-declared gate.
- No tests ran during preparation.

## Next path

```bash
skill-bill goal SKILL-380
```
