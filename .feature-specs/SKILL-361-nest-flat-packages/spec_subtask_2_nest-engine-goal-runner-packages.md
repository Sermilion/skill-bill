# SKILL-361 Subtask 2 - Nest engine goal-runner packages

Parent spec: [.feature-specs/SKILL-361-nest-flat-packages/spec.md](spec.md)
Issue key: SKILL-361

## Scope

Resolve F-002 in [investigation.md](investigation.md).

Own `runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/goalrunner/` (59 area-root files plus `planning/` at 32 files, and existing `model/`, `findings/`, `agent/`), the matching engine tests, and every production and test import of those types outside the engine. Do not reopen `featuretask` packages except import lines that still name old `goalrunner` paths.

Move mixed-responsibility types into noun-family packages under `skillbill.engine.goalrunner`. Split `planning/` by noun family so it meets the non-model ceiling of 12. Put remaining public inputs and results in `goalrunner.model`. Relocate tests with the production types. Remove `skillbill.engine.goalrunner` and `skillbill.engine.goalrunner.planning` from the remainder inventory once those packages and their new children are under the ceilings.

## Acceptance Criteria

1. Each non-model package under `skillbill.engine.goalrunner` has at most 12 sibling production `.kt` files, and `goalrunner.model` has at most 20. Status, preflight, child repair, launch, and manifest types are not mixed in the area root.
2. `goalrunner.planning` no longer holds 32 siblings; its children meet the non-model ceiling.
3. `goalrunner.model` holds public inputs and results for this area, does not import `skillbill.engine.goalrunner` except its own subpackages, and contains no `@Inject` service.
4. Matching tests live in the same packages as the production types they exercise. Engine tests that compiled before the move still run against the moved types.
5. The remainder inventory no longer lists `skillbill.engine.goalrunner` or `skillbill.engine.goalrunner.planning`. Feature-task packages from subtask 1 stay under the ceiling.

## Non-goals

No move of domain, application, infra, or cli packages. No behaviour change in the goal runner, planning sweep, or status projection. No file-content split to satisfy a count.

## Dependency notes

Depends on: subtask 1, because the sibling-count scanner, remainder inventory, and engine clustering roots land there, and because both subtasks rewrite engine imports.

## Validation strategy

Name the regression before each test: a status type left in the area root, planning still at 32 siblings, a remainder row that still names goalrunner, a feature-task package regressed above the ceiling. Rely on engine compilation and tests, `runtime-core` architecture tests, consumer compilation, and the pack-declared quality gate. Apply bill-unit-test-value-check only if a test assertion changes.

## Next path

Subtask 3 nests the remaining modules once the engine packages are under the ceiling.

## Spec Path

.feature-specs/SKILL-361-nest-flat-packages/spec_subtask_2_nest-engine-goal-runner-packages.md
