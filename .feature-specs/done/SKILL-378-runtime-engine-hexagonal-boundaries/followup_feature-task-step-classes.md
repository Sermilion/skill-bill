# SKILL-378 Follow-up - Feature-task step classes

Parent spec: [spec.md](spec.md)
Issue key: SKILL-378

## Scope

Resolves investigation F-002 for `skillbill.engine.featuretask`. F-004 and the
wire-key item of F-010 landed with subtask 2's earlier attempts; subtask 2
records them as met.

Subtask 2 left the `FeatureTaskRuntimeRunLoop*` call graph acyclic, and
`RuntimeEngineBoundaryArchitectureTest` enforces that. Build the classes in
dependency order from the leaf (`FeatureTaskRuntimeRunLoopPhaseBlocking`)
upward: each class takes the lower-layer step classes it calls as constructor
collaborators, and the acyclic rule keeps passing over the classes. If the
conversion would need a back edge, it returns an outcome instead, as subtask 2
does. It never uses lazy or provider injection, or a forwarding class.

**Step classes.** Replace the stateless `object FeatureTaskRuntimeRunLoop*`
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

**Guards and docs.** Widen the goal-runner follow-up's step-class rule in
`RuntimeEngineBoundaryArchitectureTest` to `skillbill.engine.featuretask`.
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
5. Subtask 2's acyclic rule still passes, now over classes.
6. Subtask 2's already-met criteria (typed busy disposition, no
   `"[SQLITE_BUSY]"` message check, no inline operator-block-retry wire key)
   still hold.
7. The step-class rule fails on a synthetic top-level `object` with a function
   under `featuretask/runloop` and on a synthetic `FooArgs(val recorder:
   FeatureTaskRuntimePhaseRecorder)`. The inject-property rule fails on a
   synthetic engine `@Inject` class with a public constructor property.
   `FeatureTaskRuntimeParameterBagArchitectureTest` and
   `FeatureTaskRuntimeRunLoopContextExtensionCensusArchitectureTest` do not exist.
8. Existing run-loop suites over real SQLite pass (phase order, backward edges,
   checkpoint identity, resume from durable records, validate and review gates,
   commit finalization), with test changes limited to construction and imports.
9. ARCHITECTURE.md states the step-class rule without file tables or ticket
   keys, and `../../../agent/decisions.md` records the superseding decision.

## Non-goals

- Goal runner and planning (subtask 3).
- Changing persisted data, phase order, retry budgets, or failure identities.
- Interfaces for step classes, a step framework, lazy or provider injection,
  or per-run DI subcomponents.
- A busy-retry loop in the engine. SKILL-370 puts self-managed write retry in
  the adapter.
- Breaking dependency cycles by restructuring control flow. That is subtask 2.
  If a new cycle appears, stop and report its members.

## Dependency notes

Runs after `followup_goal-runner-step-classes-and-engine-surface.md`, which introduces the step-class rule for `skillbill.engine.goalrunner`;
subtask 2 made the run-loop graph acyclic. It does not wait for another
issue. Restructure the feature-task call sites that exist now. Use typed
snapshots, artifact accessors, and git results when they are already on the
tree. When they are not, keep the current call shape inside the new step
classes. Edit architecture tests where they live. This follow-up does not wait
for SKILL-380, and SKILL-380 does not have to wait for it.

## Validation strategy

The regressions to catch are a step that drops a collaborator's side effect
(for example, a continuation-recorder write), a resumed run whose reconstructed
state differs from live state, and a checkpoint amend that skips identity
persistence. The existing end-to-end run-loop
tests cover the first three. Add a resume-parity assertion only where a touched
family has none. Do not add structural tests
beyond the rewritten guard. Run `./gradlew check`, the engine, core, and
infra-sqlite suites, and `bill-unit-test-value-check`.

## Next path

This is the last SKILL-378 step.

## Spec Path

.feature-specs/SKILL-378-runtime-engine-hexagonal-boundaries/followup_feature-task-step-classes.md
