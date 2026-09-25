# SKILL-380 Subtask 6 - Isolated phase:plan and phase:implement

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Adds the two isolated programs that require intake and a spec.

**`phase:plan`.** Runs `agent-preplan` then `agent-plan` in one invocation, in
memory. If preplan fails, plan does not launch. Writes a spec with the same
governed shape as today's plan phase / `bill-feature-spec`. Intake required
(spec path, issue key, or description). No `feature_task_workflows` row.

**`phase:implement`.** `implement-then-simplify`. Requires an existing spec.
Mutates the current worktree. Refuses without a spec. No workflow row.

Instructions apply to both plan steps unless scoped. No resume. No listed-skill
changes. Do not add `phase:pr` here.

## Acceptance Criteria

1. `skill-bill <intake> phase:plan` writes a spec matching today's plan-phase artifact shape, does not launch plan when preplan fails, and inserts no feature-task workflow or session row. Missing intake is a usage error.
2. `skill-bill phase:implement` refuses without a spec. With a spec it mutates the worktree and still writes no workflow row.
3. Subtask 5 review/validation fixtures and subtask 3 skeleton fixtures still match.

## Non-goals

- `phase:pr` (subtask 7). Operations and catalog (8–13). Isolated preplan as its
  own program.

## Dependency notes

- Depends on subtask 5. Recheck IsolatedPhaseExecutor and CLI parser anchors.

## Validation strategy

Catch: plan after failed preplan; plan without intake; implement without spec;
a workflow row. Cover with CLI usage-error tests and SQLite assertions. Run
`cd runtime-kotlin && ./gradlew check` plus engine, CLI, infra-sqlite.
`bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_7_isolated-pr.md`. Subtask 8 may start after 5 in
parallel. Subtask 13 waits on this subtask.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_6_isolated-plan-and-implement.md
