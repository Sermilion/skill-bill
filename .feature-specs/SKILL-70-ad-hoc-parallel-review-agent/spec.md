# SKILL-70 - ad hoc parallel review agent

Created: 2026-06-06
Status: Draft
Issue key: SKILL-70
Mode: single_spec
Parent: follow-up from discussion on 2026-06-06 about running an
independent parallel code-review pass with an alternative agent for a single
`bill-feature` / `bill-feature-task` run.

## Sources

User intent captured on 2026-06-06:

- The feature should support one-off usage such as:

  ```text
  $bill-feature parallel_review:claude
  ```

- The reviewer should not be called "secondary" because the agent is not
  inherently subordinate. The user switches between Claude and Codex regularly,
  so the concept is an alternative review agent selected for this run.
- Permanent configuration is probably not the right first surface. The option
  should be ad hoc and run-scoped.
- Passing an agent is already supported and should be reused rather than
  reinvented.

Relevant current behavior:

- `bill-feature` is a router: it prepares a governed feature spec first, then
  dispatches single-spec work to `bill-feature-task` or decomposed work to
  `bill-feature-goal`.
- `bill-feature-task` is the canonical runtime-backed workflow and owns
  implementation, review, validation, history, and PR-description behavior.
- The canonical `feature-task` CLI already accepts agent parameters:
  `--agent`, `--agent-override`, and repeatable `--phase-agent phase=agent`.
  `--phase-agent review=claude` is the existing way to choose Claude for the
  review phase.
- `FeatureTaskRuntimeRunRequest` already carries `invokedAgentId` and
  `FeatureTaskRuntimeAgentAssignment`.
- `FeatureTaskRuntimeAgentAssignment` already supports run-owned per-phase
  agent assignment and run-wide override. Existing tests confirm
  `perPhaseAgentIds = mapOf("review" to "claude")` makes the review phase run
  on Claude while other phases use the invoked default, and a run-wide override
  wins over both.
- The current review phase is a single phase record named `review`; it launches
  one resolved review agent, validates one phase-output payload, feeds fix-loop
  behavior, and contributes review child-step telemetry to the feature-task
  finished event.

## Problem

Feature-task runs can already choose which agent executes the review phase, but
they cannot ask for an additional independent review pass for one run. A user
who wants Codex and Claude to review the same implemented diff currently has to
run one reviewer manually or replace the review agent with the alternative
agent. Both options lose useful workflow properties:

1. Replacing the review agent answers "Claude instead of Codex", not "Claude
   and Codex independently".
2. Manual second reviews do not participate in the feature-task phase loop,
   durable state, fix-loop decision, review telemetry, or final workflow status.
3. A permanent config default is too heavy for a decision that is often
   run-specific.
4. Without provenance, merged review output cannot show whether a finding came
   from the default reviewer, the alternative reviewer, or both.
5. The existing agent-selection support must remain the source of truth for
   valid agent ids and launch behavior; this feature should not create a new
   provider registry or hardcoded `codex` / `claude` path.

## Goals

1. Add an ad hoc run modifier for feature-task review:
   `parallel_review:<agent_id>`, with `claude` as the motivating example.
2. Treat the modifier as run-scoped input, not persistent platform-pack or skill
   configuration.
3. Reuse the existing agent id and launch machinery. The normal review agent is
   still resolved through `FeatureTaskRuntimeAgentResolver`; the alternative
   review agent is validated through the same supported-agent path used by
   existing `--agent` / per-phase agent assignment surfaces.
4. During the review phase, run the normal review agent and the requested
   alternative review agent independently against the same review scope,
   accepted implementation output, run invariants, and upstream phase context.
5. Persist enough durable state to resume, inspect, and audit both review lanes
   without conflating them with separate fix-loop attempts.
6. Merge review findings into the existing review-phase result shape so the
   downstream fix loop, audit phase, quality check, history, and PR-description
   behavior continue to work.
7. Preserve finding provenance in the merged review result and telemetry.
8. Make unsupported, duplicate, blank, or malformed run modifiers loud-fail
   before launching review agents.

## Non-Goals

- Do not add permanent config defaults for parallel review in this feature.
  Configured defaults can be a later feature once the ad hoc surface proves out.
- Do not replace existing per-phase agent assignment. `--phase-agent
  review=claude` or its existing equivalent still means "run the review phase
  on Claude", not "also run Claude".
- Do not add provider-specific launch code for Claude, Codex, Opencode, Junie,
  or any future agent. Use the existing agent launch abstraction.
- Do not change platform-pack review routing or specialist review selection.
  Both review lanes use the same routed review skill and specialist policy for
  the same repo state.
- Do not run implementation, audit, quality-check, history, or PR-description
  phases in parallel.
- Do not require `bill-feature-goal` decomposition subtasks to run multiple
  review lanes unless their child `bill-feature-task` invocation receives the
  modifier explicitly.

## Target User Experience

A user can request an extra review lane for one feature run:

```text
$bill-feature SKILL-70 parallel_review:claude
```

For a prepared single-spec run, `bill-feature` passes the modifier through to
`bill-feature-task`. When the runtime reaches review, it launches the default
resolved review agent and Claude as separate review lanes. The user sees
progress for both lanes, then one merged review result:

```text
feature-task-runtime ftr-...: phase review started agent=codex attempt=1 lane=default
feature-task-runtime ftr-...: phase review started agent=claude attempt=1 lane=parallel
feature-task-runtime ftr-...: phase review completed lanes=2 findings=3
```

Merged findings preserve provenance:

```text
F-001 [codex, claude] Major ...
F-002 [claude] Major ...
F-003 [codex] Minor ...
```

If the requested alternative agent is not installed or not supported, the run
fails before the review phase launches:

```text
parallel_review agent 'foo' is not supported. Supported agents: codex, claude,
opencode, junie.
```

## Acceptance Criteria

1. The CLI / skill-run intake accepts an ad hoc modifier with the canonical
   spelling `parallel_review:<agent_id>` for `bill-feature`, plus an explicit
   CLI option for the canonical `feature-task` runtime entry point. The
   explicit CLI option should align with existing naming, for example
   `--parallel-review-agent claude`, and must not replace the existing
   `--phase-agent review=claude` meaning. The parsed modifier is passed as
   run-scoped input into `FeatureTaskRuntimeRunRequest` or an equivalent
   application-layer model.
2. Existing agent assignment remains unchanged:
   - no modifier means one review lane using the existing resolved review
     agent;
   - existing per-phase review assignment still changes the default review
     lane agent;
   - existing run-wide override still wins for the default review lane;
   - `parallel_review:<agent_id>` adds an alternative lane and does not replace
     the default lane.
3. The modifier validates before launching any review lane:
   - blank agent ids are rejected;
   - unknown agent ids are rejected through the existing supported-agent path;
   - duplicate lanes are rejected when the alternative resolves to the same
     agent id as the default review lane;
   - more than one `parallel_review:*` modifier is rejected for the first
     implementation unless the implementation intentionally supports a bounded
     list and tests that list.
4. The review phase launches both lanes independently with the same review
   briefing inputs and the same routed review skill. The alternative lane does
   not receive the default lane's findings as prompt input.
5. The two review lanes run concurrently when the launcher/runtime can support
   concurrent agent processes. If the launcher cannot support concurrency for a
   specific environment, it must fail loudly or record an explicit
   `parallel_review_unavailable` block; it must not silently degrade to a normal
   one-lane review.
6. Durable phase state distinguishes review-lane outputs from fix-loop
   attempts. A two-lane review at attempt 1 is still one review phase attempt
   with two lane outputs, not two sequential review attempts.
7. Resume behavior is deterministic:
   - if both lane outputs were already recorded and schema-valid, resume does
     not relaunch either lane;
   - if one lane completed and the other did not, resume relaunches only the
     missing or invalid lane when that is possible, or blocks with an explicit
     reason if partial-lane resume is not supported;
   - fix-loop retries preserve the same lane set for the next review attempt.
8. The merged review result feeds the existing fix-loop policy exactly as a
   normal review result does. Findings from either lane can trigger fixes; a
   no-finding result from one lane does not suppress findings from the other.
9. Finding provenance is preserved in the merged review output with at least:
   `lane_id`, `agent_id`, and `source_finding_id` or equivalent stable source
   reference. Duplicate or equivalent findings may be coalesced only when the
   merged output records all contributing agents.
10. Review telemetry and feature-task child-step telemetry include parallel
    review dimensions:
    - whether parallel review was requested;
    - default review agent id;
    - alternative review agent id;
    - lane count;
    - per-lane status and finding counts;
    - merged finding count and accepted/rejected/unresolved counts.
11. Workflow status / show output exposes the run-scoped parallel-review
    request and per-lane review status without requiring users to inspect raw
    provider logs.
12. The implementation remains boundary-correct:
    - application/domain models carry run options and lane state;
    - CLI parsing happens at the CLI boundary;
    - provider launch details stay behind the existing agent-run port;
    - no application-layer dependency on concrete provider adapters is added.
13. Regression tests cover:
    - no modifier keeps the current one-lane behavior;
    - `parallel_review:claude` with default Codex launches two lanes;
    - per-phase review assignment plus `parallel_review:codex` launches the
      assigned default lane and Codex as the alternative lane;
    - duplicate default/alternative agents are rejected before launch;
    - one lane finding and one lane no-finding merge into one actionable review
      result;
    - both lanes finding the same issue preserve both-agent provenance;
    - resume does not relaunch already completed lanes;
    - telemetry carries the parallel-review fields.
14. Maintainer validation passes:
    - `skill-bill validate`
    - `(cd runtime-kotlin && ./gradlew check)`
    - `npx --yes agnix --strict .`
    - `scripts/validate_agent_configs`

## Design Notes

- **Name the behavior, not the hierarchy.** Use `parallel_review` for the
  modifier because the important behavior is running an additional independent
  review pass. Avoid `secondary_agent` in contracts and user-facing output.
- **Run option, not config.** The first contract should be ephemeral and
  durable only for the active workflow. Persist it in workflow state because
  resume and telemetry need it, but do not introduce pack manifest defaults.
- **Default lane remains existing resolution.** Resolve the default review
  agent exactly as today. If the user already passed a review phase agent,
  that agent is the default lane. The modifier only adds the alternative lane.
- **Independent prompts.** Both lanes receive the same phase briefing and repo
  state. The alternative lane must not review the default lane's output.
- **Provenance over voting.** Merging should preserve source agents and avoid
  treating consensus as a required truth criterion. A finding from only one
  reviewer is still actionable if it meets the review rubric.
- **Bound the first version.** One alternative lane is enough for SKILL-70.
  Multiple alternatives can be added later after the lane persistence and merge
  semantics are proven.

## Validation Strategy

- CLI parser tests for `parallel_review:<agent_id>` on the feature-task command
  and the `bill-feature` dispatch path, including malformed and duplicate
  modifiers.
- Application tests in `FeatureTaskRuntimeRunnerTest` or a focused companion
  test class using a fake launcher that can record concurrent lane launch
  requests and return deterministic review payloads.
- Persistence/status tests proving lane state survives resume and appears in
  workflow status/show projections.
- Telemetry contract tests proving standalone feature-task finished events and
  review child-step payloads carry the new parallel-review dimensions.
- Architecture tests proving no concrete provider dependency crosses into the
  application/domain layers.
- Full maintainer validation command set from AC14 as the closing gate.

## Open Questions

- Should `parallel_review:<agent_id>` be accepted by the current natural-language
  skill invocation only, the CLI only, or both? Leaning: both, because
  `bill-feature` needs to pass through the same run option that `feature-task`
  can accept directly.
- Should lane ids be stable names (`default`, `parallel`) or include the agent
  id (`default-codex`, `parallel-claude`)? Leaning: stable lane ids plus
  explicit `agent_id` fields, because agents can change across runs.
- If the default and alternative lanes produce conflicting findings, should the
  merge output add a conflict marker or simply preserve both findings with
  provenance? Leaning: preserve both with provenance first; conflict detection
  can be a later improvement.

Run bill-feature-task on .feature-specs/SKILL-70-ad-hoc-parallel-review-agent/spec.md
