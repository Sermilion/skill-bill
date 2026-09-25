# SKILL-380 Subtask 7 - Isolated phase:pr

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Adds isolated pull-request execution.

Uncommitted work is not committed. If the current local branch has commits that
are not on the remote, push that branch, then open the PR. Already-pushed
branches skip the push. Protected or default-branch checkouts refuse.

Move the repo-native template search and built-in fallback from
`skills/bill-pr-description/content.md` into the `pr-description` strategy.
Search order stays that file's list. Found template: fill it, keep headings and
section order, omit checklists. None found: coded fallback. Multiple templates
with no default: usage error naming the paths.

No workflow row. Intake optional. No listed-skill deletion yet (subtask 13).

## Acceptance Criteria

1. `skill-bill phase:pr` does not commit uncommitted work. On a local branch whose commits are not on the remote it pushes that branch and then opens the PR. Protected or default-branch checkouts fail with a typed error.
2. When `.github/pull_request_template.md` (or another search-path template) exists, the opened PR summary keeps that template's headings. When none exists, the coded fallback is used.
3. No `feature_task_workflows` or session row. Subtask 5 and 6 fixtures still match.

## Non-goals

- Isolated `commit_push`. Deleting `skills/bill-pr-description` (subtask 13).
- Operations or the dispatcher.

## Dependency notes

- Depends on subtask 5. Recheck git and `gh` call sites at start.

## Validation strategy

Catch: committing dirty files; skipping push when ahead of remote; skipping a
found template. Cover with a dirty-tree non-commit test, an ahead-of-remote push
test, and template present/absent fixtures. Run
`cd runtime-kotlin && ./gradlew check` plus engine, CLI, infra-sqlite.
`bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_13_single-dispatcher-and-catalog.md` after 7 lands.
Subtask 8 may start after 5 in parallel.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_7_isolated-pr.md
