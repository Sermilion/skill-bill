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
- the census of phase-id references, launch shapes, and the 2026-09-11 review replacement

After this bundle:

- Each slot's behaviour lives in strategy classes behind one contract.
- One `PhaseRunner` executes a single step. Skeleton strategies and isolated
  programs both call it.
- The run loop asks the registry for the strategy and never branches on a phase id.
- Adding a strategy means one class, one registry entry, and one profile value.
- The default profile reproduces today's skeleton runs byte for byte.
- Isolated programs run outside the skeleton: no workflow row, no resume. Operators
  invoke them as `/skill-bill … phase:<name>` (or the CLI equivalent). Closed
  program ids: `plan`, `review`, `validation`, `implement`, `pr`. `phase:plan`
  writes a spec as today; other isolated phases print in the current session except
  implement (worktree) and pr (push + GitHub PR). Isolated `phase:review` is today's
  standalone code-review driver, not the skeleton `last-commit-fix` strategy.
- After this bundle the installed listed skill catalog is exactly `skill-bill`.
  Former listed skills are phases, operations, an unlisted inline sidecar, or
  deleted (`bill-monitor`).
- Only variants that exist today ship:
  - build and validate as two quality-gate strategies
  - the specialist findings review as a second code-review strategy (subtask 4)

Out of scope: IDE plugin UI, a generic workflow framework, and new step ids.

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
| F-008 | High | No way to run a phase program without opening a skeleton workflow | 1, 5–7 |
| F-009 | High | Many listed skills are prompt files; operators need one `/skill-bill` dispatcher, phases, and runtime operations. `bill-monitor` is unused | 8–13 |

## Acceptance Criteria

1. runtime-domain declares a closed `PhaseSlot` enum with wire values `preplan`, `plan`, `implementation`, `audit`, `code_review`, `quality_gate`, `write_history`, `commit_push`, and `pull_request`, in that order. Each slot names the existing step ids it owns, and every step id belongs to exactly one slot.
2. runtime-engine declares one `PhaseStrategy` contract and one `PhaseRunner` contract. Every slot has at least one registered strategy, a strategy runs only step ids its slot owns, and every strategy executes those steps through `PhaseRunner`.
3. The feature-task run loop selects step behaviour by asking the registry for the strategy of the step's slot. No production file under `skillbill.engine.featuretask` outside `skillbill.engine.featuretask.slot` references a phase-id constant, and an architecture rule enforces this.
4. Launch policy per step (mutating, relaunch on invalid output, single session, read-only idle) comes from the running strategy. The phase-id sets in `FeatureTaskRuntimePhaseWorkflowDefinition` and `FeatureTaskRuntimeRunnerPolicies` no longer exist.
5. Build and validate are two strategies of the `quality_gate` slot. `FeatureTaskRuntimeQualityGateRouting` no longer exists, and goal children keep today's rule: build for every subtask except the last non-skipped one, validate for the last.
6. A governed `workflow-profile` contract (schema, Kotlin version constant, parity test, `InvalidWorkflowProfileSchemaError`) defines the slot-to-strategy map. `.skill-bill/config.yaml` may set it under `workflow_profile`.
7. The resolved profile is validated against the registry and frozen into run invariants at preparation. An unknown slot or strategy fails with a typed error before any phase launches. Resume uses the frozen profile, and an explicit override that conflicts with it blocks.
8. An unknown quality-gate selection value fails as a usage error instead of becoming VALIDATE.
9. `skillbill_feature_task_runtime_finished` reports the run's slot-to-strategy map.
10. Under the default profile, workflow snapshots, phase records, ledger entries, handoff projections, and telemetry payloads other than the new profile field are byte-identical to pre-change fixtures for standalone runs and goal children.
11. A `specialist-findings` code-review strategy reviews through `ParallelCodeReviewRunner`, hands its findings to verify_findings and implement_fix, honours `CodeReviewExecutionMode`, and a profile can select it.
12. `runtime-kotlin/ARCHITECTURE.md` and `AGENTS.md` describe the slot skeleton, the strategy contract, `PhaseRunner`, isolated `phase:` programs versus the skeleton, operations, the single `/skill-bill` dispatcher, the profile, and how to add a strategy or operation. `runtime-kotlin/agent/decisions.md` records the slot-strategy contract, that isolated execution shares `PhaseRunner` without a workflow row, and that listed skills other than `skill-bill` are retired.
13. Isolated execution uses `IsolatedPhaseRequest` / `IsolatedPhaseResult`, not `FeatureTaskRuntimeRunRequest`. It creates no `feature_task_workflows` row, no runtime session row, no phase records, no ledger, and no frozen run invariants.
14. `/skill-bill <intake>` with no `phase:` and no `operation:` is the skeleton (today's `bill-feature`). `/skill-bill … phase:plan` runs preplan then plan and writes a spec. Isolated `phase:review`, `phase:validation`, `phase:implement`, and `phase:pr` follow the isolated product rules in criteria 15–17. Intake is required for plan and implement; omitted for review and validation.
15. Isolated `phase:commit_push` is refused. Isolated `phase:implement` requires an existing spec. Isolated `phase:pr` does not commit uncommitted work. If the current local branch has commits that are not on the remote, it pushes that branch, then opens the PR. Before writing the PR summary it searches the repo for a pull-request template using the same locations `bill-pr-description` uses today; when one exists it fills that template, otherwise it uses the built-in fallback now owned in code.
16. Isolated `phase:review` is today's standalone `bill-code-review` driver (`ParallelCodeReviewRunner` plus `mode:auto|inline|delegated`, omit and `auto` resolve to inline). It does not run `last-commit-fix`. Target is `HEAD`, `uncommitted`, or a commit sha/name. Omitted: `uncommitted` when the worktree is dirty, otherwise `HEAD`. Every dirty path is owned. Isolated `phase:validation` is today's `bill-code-check` repair window: the dominant pack `validation_gate` collect-all, fix every finding in that session, one cache-bypassing collect-all.
17. Isolated runs do not resume. Custom instructions prepend to the phase prompt, apply to every step in the program unless the user scopes them, and have no size cap. Profile selection is read from repo config at invocation and is not frozen. A skeleton `code_review` profile value does not redirect isolated `phase:review` onto `last-commit-fix`.
18. After install, the listed skill catalog is exactly `skill-bill`. `skills/bill-feature` and `skills/bill-monitor` do not exist. `bill-monitor` is not an operation. Remaining former listed capabilities are `operation:<name>` jobs owned by the runtime with pre/post hooks, or they are gone. `bill-code-review-inline` remains an unlisted `internal-for: skill-bill` sidecar. Pack specialist sources stay unlisted native-agent inputs, not slash commands.

## Executable scope

Thirteen subtasks, one commit each. Four waves: skeleton (1–4), isolated phases
(5–7), operations (8–12), catalog (13). Subtask 4 is skippable. Later waves do
not wait on it.

Closed isolated program ids (not slot wire values): `plan`, `review`,
`validation`, `implement`, `pr`. Closed operation ids: `update-check`,
`unit-test-value-check`, `feature-guard`, `feature-guard-cleanup`,
`pr-review-fix`, `verify`, `release`.

Listed-skill destinations (every tree under `skills/` today):

| Today's listed skill | Destination | Subtask |
| --- | --- | --- |
| `bill-feature` | `/skill-bill` skeleton dispatcher | 13 |
| `bill-feature-spec` | `phase:plan` | 6, 13 |
| `bill-code-review` | `phase:review` | 5, 13 |
| `bill-code-review-inline` | unlisted `internal-for: skill-bill` | 13 |
| `bill-code-check` | `phase:validation` | 5, 13 |
| `bill-pr-description` | `phase:pr` / `pr-description` strategy | 7, 13 |
| `bill-boundary-history` | `boundary-history` strategy | 13 |
| `bill-boundary-decisions` | `boundary-history` strategy prompt fragments | 13 |
| `bill-update-check` | `operation:update-check` | 8, 13 |
| `bill-unit-test-value-check` | `operation:unit-test-value-check` | 9, 13 |
| `bill-feature-guard` | `operation:feature-guard` | 9, 13 |
| `bill-feature-guard-cleanup` | `operation:feature-guard-cleanup` | 9, 13 |
| `bill-pr-review-fix` | `operation:pr-review-fix` | 10, 13 |
| `bill-feature-verify` | `operation:verify` | 11, 13 |
| `bill-release` | `operation:release` | 12, 13 |
| `bill-monitor` | deleted | 13 |

### Wave A — skeleton behaviour

1. **Strategy contract, registry, and the two variant slots**
   (`spec_subtask_1_strategy-contract-and-variant-slots.md`).
   - Adds `PhaseSlot`, `PhaseStepPolicy`, `PhaseStrategy`, `PhaseRunner`, the
     registry, and dispatch. Migrates `code_review` and `quality_gate`.
   - Split condition: the contract must exist before any other slot or isolated
     program can use it.
2. **Remaining slots and the no-phase-id guard**
   (`spec_subtask_2_remaining-slots-and-guard.md`).
   - Migrates the other seven slots, removes phase-keyed prompt tables, turns on
     the no-phase-id guard.
   - Split condition: mechanical and large; the guard can only go live at zero
     remaining references.
3. **Workflow profile contract and durable selection**
   (`spec_subtask_3_workflow-profile-contract.md`).
   - Schema, config, freeze, resume pin, loud quality-gate override, telemetry.
   - Split condition: new governed contract; needs every slot's strategy ids.
4. **Specialist findings review strategy**
   (`spec_subtask_4_specialist-review-strategy.md`).
   - Second `code_review` strategy. Reproduce and fix the 2026-09-11 hang first.
   - Split condition: ships separately. Waves B–D are complete without it.

### Wave B — isolated phases

5. **Isolated engine, `phase:review`, `phase:validation`**
   (`spec_subtask_5_isolated-engine-review-validation.md`).
   - `IsolatedPhaseRequest` / `Result` / `Executor`, no workflow rows,
     `invocation_id` telemetry, CLI `skill-bill [<intake>] phase:<name>`.
   - Ships print-only programs: review (standalone `ParallelCodeReviewRunner`,
     not `last-commit-fix`) and validation (today's `bill-code-check` /
     pack `validation_gate` collect-all).
   - Split condition: the isolated persistence and CLI grammar must land before
     mutating programs. Review and validation share optional intake and no
     skill-bill artifacts, so they prove the engine together.
6. **Isolated `phase:plan` and `phase:implement`**
   (`spec_subtask_6_isolated-plan-and-implement.md`).
   - Plan: transient preplan then plan, writes a spec, intake required.
   - Implement: spec required, mutates the worktree.
   - Split condition: both need intake/spec; neither is specified until the
     isolated engine exists. They ship together because they share that gate.
7. **Isolated `phase:pr`**
   (`spec_subtask_7_isolated-pr.md`).
   - Push local branch if ahead of remote; fill repo PR template or coded
     fallback; do not commit uncommitted work.
   - Split condition: GitHub + git push + template discovery is its own
     failure surface. Move `bill-pr-description` template rules into the
     `pr-description` strategy here.

### Wave C — operations

8. **Operation contract and `operation:update-check`**
   (`spec_subtask_8_operation-contract-and-update-check.md`).
   - `Operation` / `OperationRegistry`, CLI `operation:<name>`, pre/run/post.
   - Proves the contract with `update-check` (already a typed runtime check).
   - Split condition: later operations cannot be specified until this contract
     exists. Unknown `operation:` fails loudly. `phase:` + `operation:` is a
     usage error. Starts after 5; may run in parallel with 6 and 7.
9. **Checklist operations**
   (`spec_subtask_9_checklist-operations.md`).
   - `unit-test-value-check`, `feature-guard`, `feature-guard-cleanup`.
   - Split condition: in-session rubric jobs with no remote side effects.
     Prompt trees move into operation classes.
10. **`operation:pr-review-fix`**
    (`spec_subtask_10_pr-review-fix-operation.md`).
    - Analysis then execution; no mutate/reply/push before explicit selection.
    - Split condition: large gated GitHub loop; ships as its own product.
11. **`operation:verify`**
    (`spec_subtask_11_verify-operation.md`).
    - Today's `bill-feature-verify` workflow family, not `feature_task_workflows`.
    - Split condition: different durable contract; must not be mixed with
      feature-task rows.
12. **`operation:release`**
    (`spec_subtask_12_release-operation.md`).
    - Semver changelog, confirm, annotated tag.
    - Split condition: release tagging is its own safety review.

9–12 each depend only on 8. They do not depend on each other. Combining them
would make a commit no single reviewer can own. Subtask 13 depends on all four
so the catalog cannot land with a missing program.

### Wave D — catalog

13. **`/skill-bill` dispatcher, retire listed skills, delete `bill-monitor`**
    (`spec_subtask_13_single-dispatcher-and-catalog.md`).
    - One listed skill: `skill-bill`. Dispatcher keeps today's `bill-feature`
      confirmation ceremony and routes `phase:` / `operation:` to the CLI.
      Delete remaining `skills/bill-*` listed trees including `bill-feature`
      and `bill-monitor`. Retarget `bill-code-review-inline` to
      `internal-for: skill-bill`. Install catalog test. Docs.
    - Split condition: cannot run until every `phase:` and `operation:` the
      dispatcher names already exists. Last commit so install is not half-migrated.

## Self-sufficient execution

This bundle runs on the current tree. It does not wait for a subtask of another issue.

Build the slot strategies from the feature-task run loop that exists. If that loop is already step classes, group those classes by slot. If it is still objects and bags, turn the cited behaviour into strategy classes in this bundle.

Do the same for git results, typed artifacts, and the profile field: use the typed API when it exists, and the current API when it does not. Learnings delivery uses whatever resolver is on the tree. SKILL-379 is already complete and is not a start gate.

Inside this bundle the order is subtask id order: 1 through 13, except 4 may be
skipped. Dependencies: 2→1, 3→2, 4→3, 5→3, 6→5, 7→5, 8→5, 9→8, 10→8, 11→8,
12→8, 13→6, 13→7, 13→9, 13→10, 13→11, 13→12.

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
  skeleton bytes under the default profile stay identical. The only skeleton additions
  are the frozen profile in run invariants and the telemetry profile field. Isolated
  runs add no workflow, session, or phase-record rows.
- **Isolated versus skeleton.** Isolated programs never drive the slot order, never
  resume, and never share `FeatureTaskRuntimeRunRequest`. `commit_push` is not an
  isolated program. Isolated `phase:review` is the standalone code-review driver
  and does not follow the skeleton `code_review` profile. Plugin hosts call the
  same isolated engine types. The only listed skill this bundle ships is
  `skill-bill`. `bill-code-review-inline` stays unlisted.
- **Operations.** Standalone jobs that are not skeleton slots. The runtime owns
  pre, run, and post. They are not listed skills. `bill-monitor` is deleted, not
  migrated.
- **Loud failure.** Missing or unknown profile entries fail loudly with typed errors.
  Every fallback emits a record.
- **No speculation.** No speculative strategies, config knobs, or extension points
  beyond this spec.
- **Anchors.** Recheck every file anchor at the start of each subtask against the current tree.
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
- IntelliJ or VS Code plugin UI, worktree locks, or isolated `commit_push`.
- Turning pack specialist native-agents into operations, or deleting platform-pack
  `content.md` that native-agent generation still reads.

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
  slot, an unknown strategy, a wrong contract version, a conflicting resume
  override, isolated `commit_push`, isolated implement without a spec, and isolated
  review with an unknown target.
- **Test review.** Changed tests go through `skill-bill operation:unit-test-value-check`
  (today's `bill-unit-test-value-check`). The validate phase runs the pack-declared
  gate.
- No tests ran during preparation.

## Next path

```bash
skill-bill goal SKILL-380
```
