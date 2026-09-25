# SKILL-380 Subtask 12 - operation:release

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Migrates `bill-release` to `operation:release`.

Require `bump:patch|minor|major`. Refuse a missing or unknown bump. Pre-flight:
clean worktree, branch up to date with remote. Produce a curated changelog from
commits since the last release, confirm with the operator, then create and push
an annotated semver tag. Do not guess a bump. Do not tag before confirmation.

No feature-task workflow row. Do not delete `skills/bill-release` yet.

## Acceptance Criteria

1. `skill-bill operation:release` without a bump type is a usage error naming `patch`, `minor`, and `major`.
2. A dirty worktree or a branch behind its remote fails in pre and does not create a tag.
3. After confirmation the operation creates an annotated tag and pushes it. Without confirmation it does not tag.
4. Subtask 8 operations and isolated fixtures from landed wave B subtasks still match.

## Non-goals

- Changing CI that reacts to tags. Deleting `skills/bill-release` (subtask 13).
- The dispatcher.

## Dependency notes

- Depends on subtask 8. Independent of subtasks 9, 10, and 11.

## Validation strategy

Catch: tagging on a dirty tree; tagging without confirmation; missing bump
succeeding. Cover with pre-flight tests and a confirmation gate test. Run
`cd runtime-kotlin && ./gradlew check` plus CLI. `bill-unit-test-value-check`
on changed tests.

## Next path

Continue to `spec_subtask_13_single-dispatcher-and-catalog.md`.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_12_release-operation.md
