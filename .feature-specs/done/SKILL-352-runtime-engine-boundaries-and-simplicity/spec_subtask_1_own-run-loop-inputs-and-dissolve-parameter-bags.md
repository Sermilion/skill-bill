# SKILL-352 Subtask 1 - Own run-loop inputs and dissolve parameter bags

Parent spec: [.feature-specs/SKILL-352-runtime-engine-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-352

## Scope

Resolve F-001, F-002, and F-007 in [investigation.md](investigation.md).

Own the 22 `FeatureTaskRuntimeRunLoop*.kt` files, `FeatureTaskRuntimeRunState.kt`, `FeatureTaskRuntimeRunStateValidation.kt`, `FeatureTaskRuntimeRunnerExecute.kt`, `GoalRunnerSharedArgs.kt`, `GoalRunnerLoopModels.kt`, `GoalRunnerGoalLoop.kt`, `GoalRunner.kt`, `GoalRunnerPerRunLoopAssembler.kt`, `GoalRunnerLedgerRecorder.kt`, the `@Inject data class` bundles under `featuretask/model`, `goalrunner/model`, and `goalrunner/planning/model`, the helper-input section of `../../../runtime-kotlin/ARCHITECTURE.md`, and the detekt configuration and decision log where the function parameter threshold is recorded.

Narrow the nine uncounted helper families (CheckpointRemediation, Checkpoint, OutputPersistence, Launch, BackwardEdge, RecordRejection, OutputVerification, Transitions, RepairReceipt, AuditRetry) by the rule the counted six already follow. Move phase-token accounting into `FeatureTaskRuntimeRunState` behind a named transition and delete the shared `MutableMap`; give `ValidationSettlementState` read-only views. Dissolve single-site fact-only `*Args` bags into parameters; convert bags carrying ports into named collaborators; delete `BuildDeclaredGoalProgressEventArgs`; model `GoalRunnerLedgerContext` as sealed ledger actions. Move the `*Boundaries` bundles beside their orchestrators, delete `GoalRunnerDeps` and the six forwarders, and add the sub-area acyclicity check for the engine. Record the `LongParameterList.functionThreshold` decision and the complete census.

## Acceptance Criteria

1. `FeatureTaskRuntimeRunLoopContext` extension functions total the retained orchestration seams (forward drive loop, review-generation invalidation, launch capture, gate-cycle and fix-loop orchestration, review driver execution) and every other helper in the 22 files takes explicit request, state, recorder, goal-recorder, diagnostics, clock, or session parameters. An architecture test owns the per-file census and fails when the total grows. `ARCHITECTURE.md` states that principle without a file table.
2. `phaseTokenAccumulator` no longer appears as a `MutableMap` in the context, any bag, or `FeatureTaskRuntimeRunnerExecute`; `FeatureTaskRuntimeRunState` records phase tokens through one named transition and exposes a read-only view. `ValidationSettlementState` exposes no `MutableSet`. `attempted` in the goal loop is read-only outside its owning state.
3. Every `*Args` class constructed at one site with fact-only fields is gone; every bag that carried a recorder, validator, gate, session, or port is a named collaborator taking those as constructor dependencies. `GoalRunnerLedgerContext` is a sealed hierarchy with one alternative per ledger action and no accidental nullable precedence. The count of surviving `Args`, `Context`, `Inputs`, `Deps`, and `Boundaries` classes is recorded in the decision entry together with the chosen `LongParameterList.functionThreshold`.
4. No `@Inject data class` lives under a `model` package. `GoalRunnerDeps` and the `get()` forwarders on `GoalRunner` are deleted; `GoalRunner` takes its bundles and collaborators directly under the constructor threshold. `featuretask.model → featuretask`, `goalrunner.model → goalrunner`, and `goalrunner.planning.model → goalrunner.planning` import edges are zero, and `ApplicationPackageAcyclicityArchitectureTest` scans the engine at sub-area granularity with an empty baseline.
5. Phase order, backward edges, checkpoint identity, resume from durable records, and commit finalisation are unchanged, proven by the existing run-loop and goal-runner suites over real SQLite plus one resume-parity test per touched family that compares live and reconstructed state.
6. No new suppression, baseline row, or exemption. Files stay under 1,200 lines and 40 functions without count splits.

## Non-goals

No change to what the run loop persists, to failure identities, or to wire keys; that is subtask 2. No visibility narrowing beyond what a dissolved bag requires; that is subtask 3. No reopening of the `*Boundaries` collapse decision.

## Dependency notes

Depends on: none. This commit includes every call-site change its signatures require so it ships alone. Subtasks 2 and 3 rebase on it if it lands first.

## Validation strategy

Name the regression before each test: a helper that gains authority through a renamed receiver, a token count lost between attempts, a ledger action recorded with a field from another action. Assert the census through the architecture test, not a grep in a test body. Drive the run loop end to end with the existing runner test support and assert resumed execution agrees with durable state. Run the engine suite, the `runtime-core` architecture guards, and the governed quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Continue to `spec_subtask_2_one-persistence-seam-and-typed-durable-failures.md` through the goal runtime after this subtask settles.

## Spec Path

.feature-specs/SKILL-352-runtime-engine-boundaries-and-simplicity/spec_subtask_1_own-run-loop-inputs-and-dissolve-parameter-bags.md
