# SKILL-356 Subtask 2 - Goal-runner stores implement their ports directly

Parent spec: [.feature-specs/SKILL-356-runtime-infra-sqlite-boundaries-and-simplicity/spec.md](./spec.md)
Issue key: SKILL-356

## Scope

Resolve F-001 in [investigation.md](investigation.md).

Own `runtime-kotlin/runtime-infra-sqlite/src/main/kotlin/skillbill/infrastructure/sqlite/goalrunner/` (`WorkflowGoalRunnerManifestStore.kt`, `WorkflowGoalRunnerManifestStoreContext.kt`, `WorkflowGoalRunnerManifestOps.kt`, `WorkflowGoalRunnerManifestLookupOps.kt`, `WorkflowGoalRunnerManifestPauseOpsImpl.kt`, `WorkflowGoalRunnerManifestLeaseOpsImpl.kt`, `WorkflowGoalRunnerManifestControlOpsImpl.kt`, `WorkflowGoalRunnerManifestWriteOpsImpl.kt`, `WorkflowGoalRunnerManifestPurgeOpsImpl.kt`, `WorkflowGoalRunnerManifestReviewOpsImpl.kt`, `WorkflowGoalRunnerOutcomeStore.kt`, `WorkflowGoalRunnerOutcomeStoreBridges.kt`, and the collaborators they construct), `RuntimeGoalRunnerStoreProvides` in `runtime-core` `skillbill.di`, the goal-runner tests in this module, `runtime-engine`, and `runtime-core` that construct or fake these stores, `runtime-kotlin/ARCHITECTURE.md` (goal-runner execution lifetime section), and `runtime-kotlin/agent/decisions.md`.

Rebuild `WorkflowGoalRunnerManifestStore` as one class implementing `GoalRunnerManifestStore` with the `GoalRunnerControlCoordinator`, `WorkflowGoalRunnerManifestLoader`, `WorkflowGoalRunnerManifestProjectionPersistence`, `WorkflowGoalRunnerChildWorkflowPersistence`, and `WorkflowGoalRunnerScopedReplanPersistence` as private fields and `writeProjectionFile` as a private method, moving each method body from its `*OpsImpl` into the class unchanged. Rebuild `WorkflowGoalRunnerOutcomeStore` as one class implementing `GoalRunnerWorkflowOutcomeStore` whose `@Inject` constructor builds the terminal, review, reconcile, block, child-repair, and progress collaborators from the injected validators, git operations, worker supervisor, database, clock, and diagnostics; the collaborator classes keep their behaviour and gain nothing. Make the decomposition-manifest validator non-null. Delete the seven internal interfaces, six `*OpsImpl` classes, the delegate, the factory, the context bag, the bridge builder, the bridges data class, the workflow bridge, and the two bridge interfaces.

## Acceptance Criteria

1. `WorkflowGoalRunnerManifestStore` is one class implementing `GoalRunnerManifestStore` directly with no `by` clause; `WorkflowGoalRunnerManifestOps.kt`, `WorkflowGoalRunnerManifestStoreContext.kt`, `WorkflowGoalRunnerManifestLookupOps.kt`, and the six `WorkflowGoalRunnerManifest*OpsImpl.kt` files are deleted; the module declares no interface whose method set is a regrouping of a `runtime-ports` role interface.
2. `WorkflowGoalRunnerOutcomeStore` is one class implementing `GoalRunnerWorkflowOutcomeStore` with a single `@Inject` constructor; `WorkflowGoalRunnerOutcomeStoreBridges.kt`, `WorkflowGoalRunnerOutcomeStoreBridgeBuilder`, `WorkflowGoalRunnerOutcomeWorkflowBridge`, `WorkflowGoalRunnerReconcileOutcomeStore`, and `WorkflowGoalRunnerBlockOutcomeStore` are deleted; the collaborators that hold behaviour (`WorkflowGoalRunnerTerminalBridge`, `WorkflowGoalRunnerReviewBridge`, `WorkflowGoalRunnerReconcileBridge`, `WorkflowGoalRunnerBlockBridge`, `WorkflowGoalRunnerChildRepairBridge`, `WorkflowGoalRunnerProgressRecording`) remain `internal` classes with unchanged bodies, renamed without the `Bridge` suffix only if the rename touches no test assertion.
3. Every constructor and function parameter typed `DecompositionManifestValidator?` in the goal-runner package is non-null and the `?: return@let` branch at the former `WorkflowGoalRunnerOutcomeStoreBridges.kt` L146-147 is gone; the projection write after a child-wedge repair always runs.
4. `RuntimeGoalRunnerStoreProvides` binds `WorkflowGoalRunnerManifestStore` to `GoalRunnerManifestStore` and `WorkflowGoalRunnerOutcomeStore` to `GoalRunnerWorkflowOutcomeStore` and provides nothing else from this package; the two stores are the only public types in `skillbill.infrastructure.sqlite.goalrunner`.
5. `GoalRunnerControlStoreTest`, `GoalRunnerPurgePersistenceTest`, `UnaddressedFindingsRuntimeTest`, `GoalPlanningPreparationStoreTest`, and every `runtime-engine` and `runtime-core` goal-runner test pass with unchanged assertions; the goal-runner package loses at least 500 production lines and 12 files against the subtask-1 tree; no request, projection file, or SQL statement changes for well-formed input.

## Non-goals

No change to `GoalRunnerControlCoordinator`, the loader, the persistence classes, or their SQL beyond receiving fields instead of a context bag. No port signature change. No change to the legacy control migration call sites; subtask 3 owns those.

## Dependency notes

Depends on: subtask 1. It gives these files their `RuntimeDiagnostics` and `Clock` parameters and their `internal` visibility, so this rewrite starts from that shape. Rebase on the branch head and re-census before editing.

## Validation strategy

Name the regression before each test: a lease heartbeat or pause request reaching a different coordinator instance than the one that acquired the lease, a review-policy write skipping the parent projection rewrite, a child-wedge repair whose manifest projection is not written, a purge that no longer lists owned child workflow ids. Drive the goal-runner suites end to end over real SQLite through the existing runner test support so lease, pause, resume, hard reset, scoped replan, and child workflow creation agree with durable state before and after. Run the module suite, `runtime-engine`, `runtime-core` (including `RuntimeAdapterDependencyAllowlistTest`), and `./gradlew check` on runtime-kotlin; run the pack-declared quality gate and bill-unit-test-value-check for changed tests.

## Next path

Continue to `spec_subtask_3_ledger-owned-schema-evolution.md` through the goal runtime after this subtask settles, unless it has already run.

## Spec Path

.feature-specs/SKILL-356-runtime-infra-sqlite-boundaries-and-simplicity/spec_subtask_2_goal-runner-stores-implement-their-ports-directly.md
