# SKILL-361 Subtask 1 - Nest engine feature-task packages and add the sibling-count guard

Parent spec: [.feature-specs/SKILL-361-nest-flat-packages/spec.md](spec.md)
Issue key: SKILL-361

## Scope

Resolve F-001 and F-006 in [investigation.md](investigation.md).

Own `runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/featuretask/` (148 area-root files plus `model/` and `validation/`), the matching engine tests under `src/test` and `src/repoTest`, every production and test import of those types outside the engine, `PackageClusteringArchitectureTest`, `ArchitectureScanSupport`, `PrincipleEnforcementInventory` (clustering roots, generic segments, new sibling-count ceiling and remainder inventory), Package Ownership in `runtime-kotlin/ARCHITECTURE.md`, Module And Package Layout in `docs/code-principles.md`, and one decision entry in `runtime-kotlin/agent/decisions.md` that records the 12 / 20 sibling ceilings.

Move mixed-responsibility types into noun-family packages under `skillbill.engine.featuretask` (`runloop`, `phase`, `review`, `persist`, `lifecycle`, `prepare`, `runner`, keeping `model` and `validation`). Put remaining public inputs and results in `featuretask.model`. Leave at most inbound facade types in the area root. Rewrite `package` lines and imports. Relocate tests to the same packages. Add a sibling-count scanner over every production root including `intellij-plugin`. Point `packageClusteringSourceRoots` at application, domain, ports, engine, infra, cli, contracts, core, and the plugin. The remainder inventory lists the oversized packages subtasks 2 and 3 still own and does not list `featuretask` after this subtask.

## Acceptance Criteria

1. Each non-model package under `skillbill.engine.featuretask` has at most 12 sibling production `.kt` files, and `featuretask.model` has at most 20. Run-loop, phase, review, persist, lifecycle, prepare, and runner types are not in the area root.
2. `featuretask.model` holds public inputs and results for this area, does not import `skillbill.engine.featuretask` except its own subpackages, and contains no `@Inject` service.
3. Matching test sources live in the same packages as the production types they exercise. The engine test suite compiles and the tests that compiled before the move still run against the moved types.
4. A sibling-count architecture test fails a synthetic package above the ceiling and passes this tree for every production package except those named in the remainder inventory. The remainder inventory does not contain `skillbill.engine.featuretask` or its new children that are under the ceiling.
5. `packageClusteringSourceRoots` includes engine, infra, cli, contracts, core, and `intellij-plugin` in addition to application, domain, and ports. The existing loose-file clustering test still fails the synthetic `skillbill.application` / `FeatureTaskRuntimeLeaky` fixture.
6. `ARCHITECTURE.md` and `docs/code-principles.md` state the ceilings and noun-family nesting rule without a file inventory or this issue key. `runtime-kotlin/agent/decisions.md` records the ceiling numbers and the reason.

## Non-goals

No move of `goalrunner` files. No move across Gradle modules. No behaviour change, prefix rename, or file-content split. No emptying of the remainder inventory for packages this subtask does not own.

## Dependency notes

Depends on: nothing in this goal. Starts from `main` at the prepared baseline, or the branch head if later commits landed first.

## Validation strategy

Name the regression before each test: a run-loop type left in the area root, a test still compiled against the old package, a `model` → parent import, the sibling scanner ignoring engine, feature-task remaining on the remainder inventory. Rely on engine compilation and tests, `runtime-core` architecture tests, consumer compilation (`runtime-application`, `runtime-cli`, `runtime-mcp`, `runtime-core`), package-cycle emptiness for the engine, and the pack-declared quality gate. Apply bill-unit-test-value-check only if a test assertion changes.

## Next path

Subtask 2 nests `engine.goalrunner` once this subtask has landed.

## Spec Path

.feature-specs/SKILL-361-nest-flat-packages/spec_subtask_1_nest-engine-feature-task-packages-and-add-the-sibling-count-guard.md
