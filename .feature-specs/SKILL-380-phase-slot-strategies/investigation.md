# Phase slot strategies investigation

## Execution rule

This bundle runs on the current tree. It does not wait for a subtask of another issue. Ordering notes later in this file are overlap context. If a change this bundle's acceptance criteria need is missing, make it here. If it is already present, keep it.

## Judgment

The feature-task runtime already has a data skeleton: a workflow graph with step ids,
required artifacts, resume actions, transitions, and versioned handoff projection
contracts per consumer. It does not have a behaviour skeleton. How each phase runs is
decided by comparing phase ids in shared code. The engine dispatches with
`when (run.phaseId)`. Domain sets keyed by phase id decide retry, mutation, and session
rules. Prompt tables are keyed by phase id. Two strategy switches already exist, but
neither is modelled as one. Quality-gate selection rewrites transitions and filters
projections. Review is a DI-global driver with one production binding.

So a phase cannot be swapped without editing the run loop. This contradicts the
Design Principles rule "Extend manifest-driven packs and injected process strategies
instead of adding identity branches to shared runners"
(`runtime-kotlin/ARCHITECTURE.md`, Dependencies And Responsibilities).

The fix gives the existing skeleton a behaviour side:

- a fixed, ordered set of phase slots
- one strategy contract that every slot's implementation satisfies
- a registry keyed by slot and strategy id
- a workflow profile, validated against a schema and frozen into the run, that picks
  one strategy per slot

Existing behaviour moves behind the contract unchanged. The default profile reproduces
today's runs byte for byte. Only variants that exist today ship: two quality-gate
strategies and, as a separable last step, the specialist review that feature-task used
before 2026-09-11. The seam makes the next strategy one class, one registry line, and
one profile value.

## Method and baseline

- **Tree.** main at `5e94a211b` (SKILL-370 landed), read through the checkout on
  `feat/SKILL-379-review-learnings-loop` at `61d0360c7`. SKILL-379 subtask 2 was
  running in that checkout. The runtime-kotlin files cited here are identical on both
  trees except the SKILL-379 review-learnings files.
- **Read first:**
  - `AGENTS.md`
  - `runtime-kotlin/ARCHITECTURE.md` Design Principles
  - `docs/code-principles.md`
  - `docs/observability-policy.md`
  - all nine sibling bundles `SKILL-371`..`SKILL-379`: every `spec.md`, every
    subtask, and the investigation coordination sections
- **How the censuses ran.** Scripted grep over `runtime-kotlin/*/src/main`, excluding
  `build/`. Three read-only investigation agents traced launch shape, persistence and
  contracts, and sibling overlap. I re-verified the load-bearing claims myself: the
  dispatch `when`, the review driver binding, `FeatureTaskRuntimeQualityGateSelection`,
  the review-driver history, and the per-phase sets in
  `FeatureTaskRuntimePhaseWorkflowDefinition`.
- No tests or quality gates ran.

## Census

### Phase-id references in production code

Pattern: `PHASE_<ID>` constants of `FeatureTaskRuntimePhaseWorkflowDefinition` and
`FeatureTaskRuntimePhaseIds.<ID>`. There are 505 references in 83 files.

| Area | Files | Refs | Nature |
| --- | ---: | ---: | --- |
| runtime-domain `workflow/taskruntime/phase/task` (graph, definition, projections, transitions, queries) | 6 | 213 | Skeleton data, plus behaviour sets (F-002) |
| runtime-domain, other (quality-gate routing, prose synthesizer, artifact presence, handoff checks, execution matrix) | 7 | 48 | Behaviour keyed by phase id |
| runtime-engine `featuretask/runloop` | 22 | 85 | Dispatch and special cases (F-001) |
| runtime-engine `featuretask/phase` (prompt directives, briefing, projection shapes, records) | 16 | 74 | Phase-keyed prompt tables (F-005) |
| runtime-engine `featuretask/runner`, `lifecycle`, `review`, `validation`, `persist` | 21 | 59 | Policies, status, commit/push fallbacks |
| runtime-engine `goalrunner` | 13 | 29 | Goal planning runs preplan/plan; status and repair read records by step id |
| runtime-infra (`contracts`, `sqlite`) | 3 | 5 | Schema validator leniency; duplicated goal-continuation codec |

References by phase id: review 70, validate 55, audit 54, build 51, plan 47,
implement 40, commit_push 35, preplan 33, verify_findings 30, write_history 27,
simplify 24, implement_fix 23, pr 16.

### How each phase runs

| Phase | Launch shape | Prompt source | Settles through | Loops |
| --- | --- | --- | --- | --- |
| preplan | 1 agent session, 1 relaunch on invalid output, read-only; goal children receive it from goal planning | Kotlin `phaseDirectives` + ceremony | MCP prose tools or stdout prose; `PHASE_PROSE` | `regenerate_preplan` declared, dormant (empty maps, `FeatureTaskRuntimePhaseWorkflowDefinition.kt:40-42`) |
| plan | same; goal children get it from goal planning waves | Kotlin + goal-continuation constraint | prose; `PHASE_PROSE`, `VALIDATION_REQUEST`; standalone can stop as Decomposed | decomposition stop |
| implement | agent session(s) plus continuation segments, mutating | Kotlin | prose with `implementation_receipt` | continuation |
| simplify | single session only, mutating, always runs | Kotlin | prose with `simplification_receipt` | continuation |
| audit | single session per attempt, fresh session per remaining-criteria retry; repairs code but not marked mutating | Kotlin | prose; `[]` or verdict satisfied gates review | in-phase audit retry (`audit_gap` retired) |
| review | runtime driver launches 1 mutating session (`FeatureTaskLastCommitReviewDriver`, `readOnlyPhase = false`); bypasses the composer | Kotlin in the driver | runtime-assembled envelope from the `verdict:` line | anchor of `review_fix` |
| verify_findings | 1 read-only session, 30-minute progress idle | Kotlin + spec intent + boundary memory | stdout JSON, lenient schema; `REVIEW_REPAIR_REQUEST` | source of `review_fix` |
| implement_fix | agent session(s), coverage relaunch, mutating, loop-only | Kotlin | stdout `repair_receipt` + `reconciled_state` | target of `review_fix` (cap 1, no re-review) |
| build | runtime runs pack `build_command`; up to 1 triage and 3 repair sessions; runtime cache-bypassing verify | Kotlin + pack argv | runtime-minted `BUILD_RECEIPT` | repair turns |
| validate | 1 session, relaunched while failures shrink; malformed cap 2 | Kotlin | stdout JSON; `VALIDATION_RECEIPT` | shrink loop |
| write_history | 1 session, no relaunch; writes `agent/history.md` but not marked mutating | Kotlin line invoking `bill-boundary-history` | stdout `history_result`; `HISTORY_RECEIPT` | none |
| commit_push | runtime only, no agent | none | runtime `COMMIT_RECEIPT` | none |
| pr | readiness gate + 1 session; standalone only (`FeatureTaskRuntimeRunnerPolicies.kt:61-64`) | Kotlin line invoking `bill-pr-description` | stdout `pr_result` | none |

Every phase prompt is assembled in Kotlin under
`runtime-engine/.../featuretask/phase/prompt/`. Authored content enters through
agent add-ons, spec criteria, pack gate argv, boundary headings, and the two installed
skills that write_history and pr invoke.

### Strategy switches that already exist

| Switch | Values | Selected | Stored | Main files | What it changes |
| --- | --- | --- | --- | ---: | --- |
| `FeatureTaskRuntimeQualityGateSelection` | BUILD, VALIDATE | goal runner: every subtask but the last non-skipped one gets BUILD (`GoalRunnerPolicy.kt:23-37`); CLI `--quality-gate-selection` / env; standalone default VALIDATE | goal-continuation artifact only | 27 | rewrites transitions (`FeatureTaskRuntimeQualityGateRouting.kt:13-44`, applied at `FeatureTaskRuntimeRunLoopDrive.kt:237-245`); filters projections (`FeatureTaskRuntimePhaseWorkflowQueries.kt:55-89`); status skip rules |
| `FeatureTaskRuntimeReviewDriver` binding | `FeatureTaskLastCommitReviewDriver` (production), `ApprovingReviewDriverStub` (test fixture) | DI, global (`RuntimeFeatureTaskProvides.kt:23-24`) | not stored | 3 | the whole review pass |
| `CodeReviewExecutionMode` | AUTO, INLINE, DELEGATED | CLI / goal request | run invariants and goal continuation; resume blocks a change | 35 | with the wired driver: only gates `commit_focused_accounting`; AUTO always resolves to INLINE (`ReviewExecutionModePolicy.kt:29-38`) |
| standalone vs goal child | `goalContinuation != null` | runtime | goal continuation | 11 | drops pr, suppresses decomposition, enables goal review passes, hydrates planning |
| feature-size ceremony | SMALL / MEDIUM / LARGE | spec | run invariants | 19 | preplan/audit ceremony, review diff scope |
| `ExecutionMatrix` | tier and model per phase | machine config | config | several | model per phase; keyed by step id |

### Review history

Commit `d6651d94b` (2026-09-11) replaced feature-task review. The old review was the
specialist `ParallelCodeReviewRunner` pass followed by verify_findings and
implement_fix. The new one is a single session that reviews the last commit and fixes
Blocker and Major findings in place. The commit message gives the reason:
"Isolated findings-only review buffered until exit and hung; standalone code-review
still uses the existing runner." `ParallelCodeReviewRunner` is still live for
`skill-bill code-review` (`runtime-cli/.../codereview/CodeReviewCommand.kt`), and
`FeatureTaskRuntimeReviewDriver.run` still takes `ParallelCodeReviewRequest` and returns
`ParallelCodeReviewResult`. The two review designs are the concrete case for swappable
review.

### Durable state and configuration relevant to a profile

- **Run invariants** (`FeatureTaskRuntimeRunInvariantsPersistence.kt`, contract
  `0.1`) are frozen once at preparation (`FeatureTaskRuntimeRunPreparation.kt:261`).
  They already pin `code_review_mode`, and a resume that asks for a different mode
  blocks ("pinned to", `:211-220`). The keys are inline literals, and no YAML schema
  exists.
- **Workflow-state schema** `0.3` restricts `step_id` to the 13 phase ids. Adding a
  step id is a contract change.
- **Phase records** gained `launched_model` / `launched_effort` as additive optional
  fields (SKILL-183). `skillbill_feature_task_runtime_finished` carries
  `resolved_agent_ids` and `launched_models`, built from phase records
  (`FeatureTaskRuntimeAgentContextTelemetry.kt`) through a `feature_task_runtime_sessions`
  column.
- **Repo-local `.skill-bill/config.yaml`** (`RepoLocalConfig`) fails loudly with
  `MalformedRepoLocalConfigError`. Machine config holds `execution_matrix`. Platform
  packs apply only when they win dominant-stack routing.
- **DI.** One kotlin-inject `RuntimeComponent`. No `@IntoSet` / `@IntoMap` anywhere.
  Explicit `@Provides` lists are the house style.

## Principles

| Principle (ARCHITECTURE.md) | State |
| --- | --- |
| Injected strategies, not identity branches in shared runners | Violated: 250 engine phase-id references, dispatch `when` |
| One owner for coupled state; named transitions | Partly: quality-gate routing mutates transitions from outside the graph |
| Contract ownership: schema, version, typed error | Violated at `QualityGateSelection.fromWire` (unknown to VALIDATE, no record) and run-invariant inline keys |
| Observability: every fallback emits a record | Violated at `fromWire`; `orLegacyValidate()` readers default silently (`FeatureTaskRuntimeStatusService.kt:81-83`, goal-runner repair) |
| Simplicity: abstraction only for a current consumer or test substitute | Satisfied by the design: the strategy contract has 9 or more production implementations and existing test substitutes; the registry replaces the dispatch `when`; no plugin loading |

## Findings

| ID | Priority | Finding | Subtask |
| --- | --- | --- | --- |
| F-001 | High | Phase behaviour is dispatched by identity: `when (run.phaseId)` in `FeatureTaskRuntimeRunLoopPlanningBranch.runPreparedPhaseReady` (`:192-220`), a parallel `when` in `FeatureTaskRuntimeCurrentPhaseExecutionDeriver` (`:27-38`), and 250 engine phase-id references outside any per-phase owner | 1, 2 |
| F-002 | High | Launch rules live in domain sets keyed by phase id, far from the behaviour: `MUTATING_PHASES`, `OUTPUT_RETRY_PHASES`, `singleAgentSessionOnly`, `GENERATION_SCOPED_PHASE_IDS` (`FeatureTaskRuntimePhaseWorkflowDefinition.kt:44-69`), `NON_FILE_MUTATING_PHASES` (`FeatureTaskRuntimeRunnerPolicies.kt:23`), `PROSE_PHASE_IDS` (`ProsePhaseOutputSynthesizer.kt:15-16`), and the read-only idle choice (`FeatureTaskRuntimeRunLoopLaunch.kt:283-312, 368-372`). They have drifted from the code: review, audit, validate, and write_history all edit files, but none is marked mutating | 1, 2 |
| F-003 | High | Quality-gate selection is a strategy implemented as transition rewriting, projection filtering, and status skip rules across 27 files. `fromWire` silently maps unknown values to VALIDATE, and the CLI option is free-form | 1, 3 |
| F-004 | Medium | The review seam exists but is global and not durable. `CodeReviewExecutionMode` is validated and pinned in 35 files but has no effect on the wired driver beyond accounting. The specialist review was removed from feature-task over a liveness bug, not a design decision against it | 1, 4 |
| F-005 | Medium | Prompt task text is chosen from phase-keyed tables (`phaseDirectives`, `phaseTaskDirective`). The review entries and `reviewExecutionDirective` look unreachable because review bypasses the composer | 1, 2 |
| F-006 | Medium | Nothing durable records how a phase ran, so resume cannot detect a strategy change, and telemetry cannot compare two strategies for the same slot | 3 |
| F-007 | Low | `AGENTS.md` says the build gate starts no repair agents. `FeatureTaskRuntimeBuildGateCoordinator` runs up to one triage and three repair sessions (`:59-218, 328`), and the build prompt agrees with the code | Open question (see Limits) |

## Target design

- **Slots.** A closed, ordered `PhaseSlot` enum in runtime-domain with wire values:
  `preplan`, `plan`, `implementation`, `audit`, `code_review`, `quality_gate`,
  `write_history`, `commit_push`, `pull_request`. Each slot owns a fixed set of
  today's step ids:
  - implementation: implement, simplify
  - code_review: review, verify_findings, implement_fix
  - quality_gate: build, validate
  - every other slot: its one step

  Slot order and the goal-child rule (no `pull_request`) belong to the skeleton, not
  to any strategy.
- **Strategy contract.** One `PhaseStrategy` interface in
  `skillbill.engine.featuretask.slot`. Each strategy declares:
  - its slot and its strategy id
  - the step ids it runs, all drawn from its slot
  - a launch policy per step (mutating, relaunch on invalid output, single session,
    read-only idle), as a domain value
  - its task directive per step

  It executes one step at a time and returns the existing phase outcome type.
  Collaborators arrive through the constructor. Per-call facts (the step run, state,
  observability) arrive as parameters. This matches SKILL-378 subtask 2's
  step-class rule.
- **Strategies shipped:**
  - one per slot, wrapping today's behaviour
  - two for quality_gate: `pack-build` and `agent-validate`, replacing transition
    rewriting
  - optionally, a second code_review strategy, `specialist-findings`, over
    `ParallelCodeReviewRunner`

  Strategy ids are open vocabulary declared by the strategy class. Slot keys are
  closed.
- **Registry.** One explicit `@Provides` in runtime-core lists every strategy. A lookup
  of an unknown `(slot, id)` pair raises a typed error.
- **Workflow profile.** A slot-to-strategy-id map with a governed schema, set under
  `workflow_profile` in `.skill-bill/config.yaml`. Slots the profile omits use each
  slot's declared default.
  - Precedence: per-run quality-gate override (CLI, env, or goal-runner stamp), then
    repo profile, then defaults.
  - The resolved map is validated against the registry and frozen into run
    invariants at preparation.
  - Resume uses the frozen map and blocks an explicit override that conflicts with it.
  - The run's finished telemetry reports the map.
- **What stays in the domain graph.** Transitions and handoff projections stay
  declared there. A strategy may run only the steps and edges the graph declares for
  its slot. A strategy that needs a new step id or edge changes the workflow-state and
  projection contracts through the normal contract path. That limit is deliberate:
  durable bytes stay governed.

## Overlap with other bundles

The notes below describe files other bundles also touch. They are not a start gate. SKILL-380 does the strategy split on the run loop that exists. It does not wait for SKILL-378, SKILL-376, SKILL-372, SKILL-377, or SKILL-379.

| Overlap | What SKILL-380 does on the current tree |
| --- | --- |
| Feature-task run loop still uses objects and bags | This bundle turns the cited behaviour into strategy classes. |
| Run loop is already step classes | This bundle groups those classes by slot. |
| Typed artifacts, git results, or a stderr channel already exist | This bundle uses them. |
| Those typed APIs are absent | This bundle uses the current call shape and diagnostic channel. |

## Coordination with prepared bundles

| Bundle | Overlap | Resolution |
| --- | --- | --- |
| SKILL-378.2 | Its non-goal "Interfaces for step classes, a step framework, or per-run DI subcomponents"; its step classes are regrouped here | 378.2 stays as written. It should group phase-specific behaviour by the SKILL-380 slot where grouping is otherwise free. SKILL-380 records a decision that adds the one slot-strategy contract on top of the step-class rule. Its guards bind SKILL-380: no top-level function objects in `featuretask/runloop`, no collaborator-carrying `*Args`/`*Inputs`/`*Context`, at most six parameters, private inject properties |
| SKILL-378.3 | Goal-runner step classes and the engine visibility pass | Independent. If it lands after SKILL-380, the `slot` packages follow its visibility rule: strategies are internal to runtime-engine except the contract the runtime-core registry needs |
| SKILL-372.1 / 372.2 | Always-on update validation; typed artifact accessors; validators move to ports | SKILL-380 reads the frozen profile through a typed accessor when one exists, and through the current artifact API when it does not. |
| SKILL-377.1 | Typed git results in commit_push and checkpoint code | SKILL-380 uses typed git results when they exist, and the current decode when they do not. |
| SKILL-377.2 | Deletes `ReviewFactPorts` and the review-preparation interfaces, and changes `AgentRunLaunchFacts` termination | Independent of SKILL-380.1–3. If it lands before SKILL-380.4, the specialist strategy uses its preparation-facts value. It also conflicts with SKILL-379.1, which injected a real learnings resolver where 377.2 assumes a stub; 377.2 must keep that resolver |
| SKILL-373.1–3 | DI provider style (`@JvmSynthetic` removal); architecture suite moves to `repoTest` | Independent. The new registry provider follows whatever provider style is current. The new guard rule moves with its host test |
| SKILL-374.2 | Placement rule may move single-owner keys | `WorkflowProfilePayloadKeys` is read by infra host and engine, so it satisfies the rule in runtime-contracts |
| SKILL-375 | MCP settlement tools accept only prose phases | Independent. The settlement contract is unchanged |
| SKILL-371, 376.2, 376.3, 372.3, 377.3 | CLI contracts, SQLite goal-runner move, package layout, aliases | Independent; rebase only |

Architecture tests no bundle owns that pin today's phase structure:
`FeatureTaskRuntimeBoundaryOwnershipArchitectureTest` and
`FeatureTaskAuditRemainingCriteriaPackIndependenceArchitectureTest`. SKILL-380
subtask 1 or 2 updates them, whichever first touches their subject.

## What stays unchanged

| Item | Reason |
| --- | --- |
| The 13 step ids, workflow-state schema 0.3, phase-output 0.6, persistence 0.2, handoff contracts | Durable bytes. A strategy maps onto existing steps |
| Transition graph, `review_fix` edge and cap, in-phase audit retry, build repair turns, validate shrink loop | Behaviour preservation. Each moves inside its strategy as-is |
| Goal planning running preplan/plan outside the run loop and hydrating children | Separate workflow. It obtains the preplan/plan task directives from the default strategies but keeps its own sweep |
| Goal-runner reads of phase records by step id (status, repair, stop reports) | Data access, not dispatch. SKILL-378.3 owns goal-runner shape |
| MCP settlement surface (`feature_task_phase_complete` / `block`) | Settlement contract, not strategy |
| `ExecutionMatrix` tier and model per step | Model choice is orthogonal to strategy. It stays keyed by step id |
| Mismatched mutation flags (audit, validate, write_history, review) | Declared per step policy with today's values. Correcting them changes idle and checkpoint behaviour and needs its own decision |

## Over-engineering register (considered, rejected)

| Idea | Why not |
| --- | --- |
| Loading strategies from external jars or packs | No consumer. In-process classes plus a manifest cover today's variants |
| Per-subtask or per-phase profile switching inside a run | No consumer, and it breaks resume invariants |
| kotlin-inject `@IntoMap` multibinding | First use in the repo; an explicit provider list matches house style and reads as the catalog |
| Generic user-defined slot graph (custom slots, custom order) | Contradicts "no generic workflow framework". The skeleton is fixed; only fillers vary |
| Strategy A/B through the experiment framework | SKILL-378.1 deletes it; the finished-telemetry field is enough to compare |
| A second strategy for every slot | Only quality_gate and code_review have real second variants |
| Per-phase-record `strategy_id` | The frozen run-level map already names every step's strategy |
| Generalized `--phase-strategy slot=id` CLI override | Only the quality gate has a per-run override today |

## Limits

- Grep censuses miss multi-line headers and reflective uses. Compilation decides the
  deletions.
- "Review directives unreachable" (F-005) is plausible. It needs a caller census after
  SKILL-378.2.
- The specialist review hang from `d6651d94b` was not reproduced here. Subtask 4
  starts by reproducing it.
- **Open question for the owner (F-007):** whether `AGENTS.md`'s build paragraph or
  the build coordinator's three repair turns is the intended behaviour. SKILL-380
  preserves the code's behaviour and changes neither.
- Line numbers were read on 2026-09-23. SKILL-378.2 moves most of the engine files, so
  every subtask rechecks anchors at start.
