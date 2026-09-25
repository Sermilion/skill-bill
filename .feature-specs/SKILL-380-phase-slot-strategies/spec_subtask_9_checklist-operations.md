# SKILL-380 Subtask 9 - Checklist operations

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Moves three in-session rubric skills into operations. No GitHub, no tags, no
feature-verify workflow.

- `operation:unit-test-value-check` — today's `bill-unit-test-value-check`
  value test, same default scope (staged/unstaged).
- `operation:feature-guard` — today's `bill-feature-guard` rollout rules.
- `operation:feature-guard-cleanup` — today's `bill-feature-guard-cleanup`.

Each has pre/run/post. Authored rules move into the operation classes (or
runtime-owned prompt fragments they load). Do not delete `skills/` trees yet
(subtask 13). Do not add pr-review-fix, verify, or release here.

## Acceptance Criteria

1. The three operations are registered and runnable via `skill-bill operation:<name>`. Unknown names still fail as in subtask 8.
2. `operation:unit-test-value-check` with no scope reviews current staged and unstaged changes; with no unit tests in scope it says so and stops.
3. `operation:feature-guard` and `operation:feature-guard-cleanup` apply the same checklists as today's skills (single switch point, rollback when flag off, cleanup removes the flag).
4. Subtask 8 update-check and isolated fixtures still match.

## Non-goals

- Deleting the `skills/bill-unit-test-value-check` and guard directories
  (subtask 13). Remote operations (10–12).

## Dependency notes

- Depends on subtask 8.

## Validation strategy

Catch: default scope walking the whole repo; a guard operation skipping the
single-switch-point rule. Cover with a no-tests-in-scope test and one fixture
per guard operation. Run `cd runtime-kotlin && ./gradlew check` plus CLI.
`bill-unit-test-value-check` on changed tests until subtask 13.

## Next path

Continue to `spec_subtask_13_single-dispatcher-and-catalog.md` once 6, 7, and
8–12 have landed. Subtasks 10–12 do not depend on this subtask.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_9_checklist-operations.md
