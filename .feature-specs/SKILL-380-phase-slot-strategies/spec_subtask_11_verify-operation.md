# SKILL-380 Subtask 11 - operation:verify

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Migrates `bill-feature-verify` to `operation:verify`.

This operation keeps the **feature-verify** durable workflow family
(`feature_verify_workflow_*` tools and tables). It must not write
`feature_task_workflows` rows. Step ids, artifacts, and telemetry ownership
stay as in today's `skills/bill-feature-verify/content.md`, moved into the
operation's pre/run/post and any runtime-owned prompt fragments.

Pre: open verify workflow after the same confirmation today's skill requires.
Post: finish telemetry. Run: in-session orchestration replaced by the
operation's agent session(s) under the existing verify contract.

## Acceptance Criteria

1. `skill-bill operation:verify` opens and updates feature-verify workflow state, not `feature_task_workflows`.
2. Today's verify step ids and artifact names remain the contract; unknown verify contract versions still loud-fail.
3. Subtask 8 operations and isolated fixtures from landed wave B subtasks still match.

## Non-goals

- Rewriting verify into a feature-task skeleton. Deleting
  `skills/bill-feature-verify` (subtask 13). `operation:release`.

## Dependency notes

- Depends on subtask 8. Independent of subtasks 9, 10, and 12. Recheck verify MCP/workflow anchors at start.

## Validation strategy

Catch: a feature-task workflow row; dropped verify step id. Cover with SQLite
family assertions and a contract-version rejection test. Run
`cd runtime-kotlin && ./gradlew check` plus CLI, MCP, infra-sqlite.
`bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_13_single-dispatcher-and-catalog.md` once 6, 7, and
8–12 have landed.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_11_verify-operation.md
