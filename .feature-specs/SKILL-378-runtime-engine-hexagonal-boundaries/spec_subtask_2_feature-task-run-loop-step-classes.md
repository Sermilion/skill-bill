# SKILL-378 Subtask 2 - Feature-task step classes

Parent spec: [spec.md](spec.md)
Issue key: SKILL-378

## Scope

Resolves investigation F-002 for `skillbill.engine.featuretask`, F-004, and the
wire-key item of F-010.

**Step classes.** Replace the 20 stateless `object FeatureTaskRuntimeRunLoop*`
namespaces with classes grouped by responsibility. A class's constructor takes
the collaborators it uses (recorder, gates, output validator, continuation
recorder, diagnostics, clock, git operations). Per-run values (request, state,
session, observability) go in the constructor when every method uses them and
stay parameters otherwise. `FeatureTaskRuntimeRunLoop` constructs the classes
for its run. Per-call facts (`PhaseRun`, iteration, output text, captures) stay
method parameters. Merge objects that share collaborators and change together
when the result stays under 1,200 lines and 40 functions. Do not create a
forwarding class.

Apply the same rule to the `FeatureTaskRuntimeRunner.*` extension files
(`FeatureTaskRuntimeRunnerExecute.kt`, `…ExecutePrepared.kt`,
`FeatureTaskRuntimeAgentContextTelemetry.kt`, `FeatureTaskRuntimeReviewFixBudget.kt`).
Once nothing outside the class reads its 11 constructor properties, make them
`private`. Do the same for other `@Inject` classes under `featuretask` that expose
collaborators, and for `lifecycle`, `phase`, `review`, and `validation` helpers
that take the same collaborator set. Pure functions over domain values stay
top-level functions.

Where a `featuretask` `@Inject` class builds its collaborators by hand
(`FeatureTaskRuntimePhaseRecorder`, `FeatureTaskRuntimeGoalContinuationRecorder`,
`FeatureTaskContinuationLookupService`), take them as constructor parameters
when kotlin-inject can resolve them. `WorkflowEngine` construction is
SKILL-372's. Per-attempt data classes with public `var` fields in
`runloop/core/FeatureTaskRuntimeRunLoopModels.kt` become state owned by the
class that mutates them, exposed read-only.

Delete each `*Args`/`*Inputs`/`*Context` bag under `featuretask` that becomes
unnecessary. A bag survives only as a named value with no collaborator field.

**Busy failures.** At the SQLite session boundary, translate a busy failure
into one typed exception in the `skillbill.error` taxonomy, keeping the original
message as its message. Place it in `skillbill.error.core`, which already exists. `FeatureTaskRuntimeRunLoopPhaseRunner.goalReviewPreparationDisposition`
checks the type. The persisted-reason checks at L207–L209 and
`FeatureTaskRuntimeRunState.kt:347` stay.

**Wire keys.** Encode the operator-block-retry artifact in
`FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint.kt` through the domain
`FeatureTaskRuntimeOperatorBlockRetry` owner and the typed accessors SKILL-372
subtask 2 adds on `DurableWorkflowArtifacts`, not through new constants. SKILL-372
makes domain artifact-key constants `internal`, so the engine cannot declare
keys beside them. If SKILL-372 subtask 2 already removed the inline keys, record
the criterion as met.

**Guards and docs.** Add the step-class rule to the existing
`RuntimeEngineBoundaryArchitectureTest`, wherever SKILL-373 has placed the suite.
The rule fails when `featuretask/runloop` declares a top-level `object` with
functions, or when a bag class under `skillbill.engine.featuretask` has a
collaborator-typed constructor parameter. Extend SKILL-370's inject-property rule
in `InjectConstructorDefaultsArchitectureTest` to runtime-engine
`skillbill.engine.featuretask` with an empty baseline. Delete
`FeatureTaskRuntimeParameterBagArchitectureTest` and
`FeatureTaskRuntimeRunLoopContextExtensionCensusArchitectureTest` if SKILL-373
subtask 2 has not already deleted them. Both pin the procedural form.
Replace the SKILL-247 case log under ARCHITECTURE.md State Ownership with the
step-class rule. Record a decision that supersedes the 2026-09-15 helper-input
form and the 2026-09-17 bag census. `LongParameterList.functionThreshold` stays 6.

## Acceptance Criteria

1. `featuretask/runloop` declares no top-level `object` containing functions,
   except pure constant holders.
2. No class under `skillbill.engine.featuretask` named `*Args`, `*Inputs`, or
   `*Context` has a constructor parameter typed as a recorder, gate, output
   validator, continuation recorder, `RuntimeDiagnostics`, or `WorkflowGitOperations`.
3. No `@Inject` class under `skillbill.engine.featuretask` exposes a constructor
   collaborator as a non-private property, and no file declares
   `fun FeatureTaskRuntimeRunner.` extensions.
4. No function under `skillbill.engine.featuretask` exceeds six parameters, with
   no new suppression, baseline row, or threshold change.
5. A test that makes a review-preparation write fail with the typed busy
   exception gets a RETRYABLE disposition, and the same failure message without
   the type gets NEEDS_USER_ACTION.
6. No `"[SQLITE_BUSY]" in error.message` check remains in runtime-engine.
   Persisted block reasons keep the same text.
7. `FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint.kt` contains no inline
   string wire key, and the encoded operator-block-retry artifact bytes match the
   pre-change fixture.
8. The step-class rule fails on a synthetic top-level `object` with a function
   under `featuretask/runloop` and on a synthetic `FooArgs(val recorder:
   FeatureTaskRuntimePhaseRecorder)`. The inject-property rule fails on a
   synthetic engine `@Inject` class with a public constructor property.
   `FeatureTaskRuntimeParameterBagArchitectureTest` and
   `FeatureTaskRuntimeRunLoopContextExtensionCensusArchitectureTest` do not exist.
9. Existing run-loop suites over real SQLite pass (phase order, backward edges,
   checkpoint identity, resume from durable records, validate and review gates,
   commit finalization), with test changes limited to construction and imports.
10. ARCHITECTURE.md states the step-class rule without file tables or ticket
    keys, and `agent/decisions.md` records the superseding decision.

## Non-goals

- Goal runner and planning (subtask 3).
- Changing persisted data, phase order, retry budgets, or failure identities.
- Interfaces for step classes, a step framework, or per-run DI subcomponents.
- A busy-retry loop in the engine. SKILL-370 puts self-managed write retry in
  the adapter.

## Dependency notes

Depends on subtask 1. It does not wait for another issue. Restructure the feature-task call sites that exist now. Use typed snapshots, artifact accessors, and git results when they are already on the tree. When they are not, keep the current call shape inside the new step classes. Edit architecture tests where they live. The typed busy exception in this subtask is new. This subtask does not wait for SKILL-380, and SKILL-380 does not have to wait for it.

## Validation strategy

The regressions to catch are a step that drops a collaborator's side effect
(for example, a continuation-recorder write), a resumed run whose reconstructed
state differs from live state, a checkpoint amend that skips identity
persistence, and a busy failure misclassified. The existing end-to-end run-loop
tests cover the first three. Add a resume-parity assertion only where a touched
family has none, plus the disposition test above. Do not add structural tests
beyond the rewritten guard. Run `./gradlew check`, the engine, core, and
infra-sqlite suites, and `bill-unit-test-value-check`.

## Next path

Continue to `spec_subtask_3_goal-runner-step-classes-and-engine-surface.md`.

## Spec Path

.feature-specs/SKILL-378-runtime-engine-hexagonal-boundaries/spec_subtask_2_feature-task-run-loop-step-classes.md
