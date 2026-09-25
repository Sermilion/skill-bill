# SKILL-380 Subtask 8 - Operation contract and operation:update-check

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Resolves the contract half of F-009. Proves it with the smallest existing
runtime job.

**Contract.** `Operation` (KDoc on the interface): wire id, `pre`, `run`,
`post`. Pre and post are in-process. Run may launch an agent session; it does
not open a feature-task skeleton workflow. `OperationRegistry` is one explicit
`@Provides` list. Duplicate ids or unknown lookup fail with a typed error.
No multibinding or classpath scan.

**CLI.** `skill-bill operation:<name>`. Combined `phase:` and `operation:` is a
usage error. Telemetry uses `invocation_id`, not a feature-task `workflow_id`.

**`operation:update-check`.** Same behaviour as today's `bill-update-check` /
`mcp__skill-bill__update_check`: report installed vs latest, do not mutate.
Pre: resolve repo/runtime version sources. Post: emit telemetry.

No listed-skill deletion. Other operations are later subtasks.

## Acceptance Criteria

1. `Operation` and `OperationRegistry` exist. Duplicate registration and unknown id each have a typed-error test.
2. `skill-bill operation:update-check` matches today's update-check outcomes (`up_to_date`, `update_available`, `ahead_of_release`, `unknown`) and writes no `feature_task_workflows` row.
3. `skill-bill phase:review operation:update-check` (both tokens) is a usage error.
4. Isolated phase fixtures from subtask 5 still match. Do not require subtask 6 or 7 if those commits have not landed.

## Non-goals

- Other operations (9–12). Dispatcher and skill retirement (13).

## Dependency notes

- Depends on subtask 5 so the CLI already parses `phase:` and can reject both
  tokens. Does not depend on 6 or 7.

## Validation strategy

Catch: unknown operation succeeding; update-check inserting a workflow row;
phase+operation accepted. Run `cd runtime-kotlin && ./gradlew check` plus CLI
and engine. `bill-unit-test-value-check` on changed tests.

## Next path

Subtasks 9, 10, 11, and 12 may start. Each is independent of the others.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_8_operation-contract-and-update-check.md
