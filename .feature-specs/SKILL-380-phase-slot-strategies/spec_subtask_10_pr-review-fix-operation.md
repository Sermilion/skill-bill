# SKILL-380 Subtask 10 - operation:pr-review-fix

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Migrates `bill-pr-review-fix` to `operation:pr-review-fix`.

Keep the two gated stages: analysis (fetch unresolved threads, classify,
propose, wait for selection) then execution (fix, reply, learnings, optional
follow-up spec, quality gate, push). Never mutate, post replies, or push
without explicit user selection from analysis.

Inputs stay PR number, URL, or current-branch PR. GraphQL for thread-aware
comments, not flat `gh pr view --comments`.

Pre: resolve PR, refuse if none. Post: telemetry. No feature-task workflow row.

## Acceptance Criteria

1. `skill-bill operation:pr-review-fix` with no selection performs analysis only and does not change the worktree, post replies, or push.
2. Execution runs only after an explicit selection of the same shape as today's skill (`analyze-only` | `analyze+fix-selected` | `fix-all-unresolved`).
3. Unresolved/outdated thread flags come from GraphQL, not the flat comments list.
4. Subtask 8 operations still register. Isolated fixtures from landed wave B subtasks still match.

## Non-goals

- Deleting `skills/bill-pr-review-fix` (subtask 13). `operation:verify` or
  `operation:release`. Changing the GitHub reply/learnings product.

## Dependency notes

- Depends on subtask 8. Independent of subtasks 9, 11, and 12.

## Validation strategy

Catch: mutate-before-select; using flat comments. Cover with a selection-gate
test and a fixture that unresolved threads stay unresolved when analysis-only.
Run `cd runtime-kotlin && ./gradlew check` plus CLI. `bill-unit-test-value-check`
on changed tests.

## Next path

Continue to `spec_subtask_13_single-dispatcher-and-catalog.md` once 6, 7, and
8–12 have landed.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_10_pr-review-fix-operation.md
