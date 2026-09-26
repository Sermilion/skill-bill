# SKILL-378 Follow-up - Goal-runner step classes and engine surface

Parent spec: [spec.md](spec.md)
Issue key: SKILL-378 (run as its own issue; the SKILL-378 goal cannot add subtasks)

## Scope

Resolves investigation F-002 for `skillbill.engine.goalrunner`, plus F-005,
F-009, and the raw-map part of F-008. Split out of subtask 3, which kept the
sequence, read-query, and silent-read work.

**Step classes.** The step-class rule: a class's constructor takes the collaborators it
uses (recorders, emitters, stores, gates, diagnostics, clock, git operations); per-call
facts stay parameters; no top-level `object` with functions; no `*Args`/`*Inputs`/`*Context`
bag with a collaborator-typed constructor parameter; pure functions over domain values stay
top-level; no forwarding class, step interface, framework, or per-run DI subcomponent.
This follow-up introduces the rule and applies it to `goalrunner/execution`, `planning`,
`launch`, `repair`, `status`, and `preflight`. The step-class follow-up
(`followup_feature-task-step-classes.md`) runs after this follow-up and extends the same
rule to `skillbill.engine.featuretask`. Delete `GoalRunnerSharedArgs.kt`.
Collaborators such as `GoalRunnerObservabilityEmitter` and `GoalRunnerLedgerRecorder`
move into the constructors of the classes that use them. Replace the 32
`DefaultGoalPlanningSweep.*` extensions and their `Produce*Args`/`GoalPlanning*Context`
bags with members or planning classes that own their collaborators, and make the
sweep's `manifestFileStore` private. Make the constructor properties of
`GoalRunnerStatusProjectionAssembler`, `GoalRunnerStatusProjectionDataSources`,
and other goal-runner `@Inject` classes private. `GoalRunnerStatusService`,
`GoalPreflightService`, `GoalPlanningPreparationCheckpoint`,
`GoalChildPlanningHydratorPortAdapter`, and `GoalRunnerChildRepairOperations` take
their collaborators as constructor parameters where kotlin-inject can resolve
them. Add the step-class rule to the existing `RuntimeEngineBoundaryArchitectureTest`
scoped to `skillbill.engine.goalrunner`, written so the follow-up can widen its scope
to `featuretask` by adding a package root. Extend SKILL-370's inject-property rule in
`InjectConstructorDefaultsArchitectureTest` to `skillbill.engine.goalrunner`, including
the classes SKILL-376 subtask 2 moved into it. Keep SKILL-378 subtask 2's acyclic run-loop rule
passing.

**Aliases.** Delete `goalrunner/model/GoalRunnerPersistenceModelAliases.kt` (17
ports targets; SKILL-372 subtask 3 deletes the domain-targeted `GoalContinuation`
alias), the 19 aliases in `work/model/IdeStatusModels.kt`, and the intra-engine
alias in `goalrunner/persist/DurableChildRecoveryClass.kt`. Re-census the targets
first, because SKILL-377 subtask 3 renames the ports `*Model` types behind its own
aliases. Import the owners in the engine,
CLI, MCP, core, and tests. Update the pinned inbound API list to engine-declared
types only.

**Visibility.** Make every public engine type `internal` when no other module
imports it and runtime-core's generated component does not need it. Compilation
decides. Classes SKILL-376 subtask 2 moved in are already `internal`, except
`WorkflowGoalRunnerManifestStore` and `WorkflowGoalRunnerOutcomeStore`, which
runtime-core binds. Add those two to the pinned engine types. Then set
`includeDefaultPublic = true` in `RuntimeEngineBoundaryArchitectureTest`.

**Raw maps.** Make each remaining public engine declaration with
`Map<String, Any?>` in its signature internal or typed, and add runtime-engine main
to the path filter of `RuntimeRawMapArchitectureTest`.

## Acceptance Criteria

1. `GoalRunnerSharedArgs.kt` does not exist. No file other than
   `DefaultGoalPlanningSweep`'s own declares a `DefaultGoalPlanningSweep.` extension.
2. The step-class rule scans `skillbill.engine.goalrunner` and fails on a
   synthetic `FooArgs(val ledger: GoalRunnerLedgerRecorder)` there. The
   inject-property rule covers `skillbill.engine.goalrunner` with an empty baseline
   and fails on a synthetic goal-runner `@Inject` class with a public collaborator
   property. `skillbill.engine.featuretask` is left to the step-class follow-up.
3. runtime-engine main declares no typealias, and the pinned inbound API lists
   only engine-declared types. The inbound API
   test passes.
4. The engine visibility guard counts default-public declarations and passes.
5. The raw-map guard reads runtime-engine main files and passes. A synthetic
   public engine function returning `Map<String, Any?>` fails it.
6. Existing goal-runner, planning, status, repair, preflight, and CLI goal
   suites pass, with test changes limited to construction, imports, visibility,
   and the new tests.

## Non-goals

- Converting `skillbill.engine.featuretask` run-loop objects; that is
  `followup_feature-task-step-classes.md`, which runs after this one.
- Collapsing `GoalRunnerChildRepairRunnerPort`, `GoalRunnerChildRepairStore`, or
  `GoalChildPlanningHydratorPort` (SKILL-376 follow-up).
- Deleting `fun interface` seams with test substitutes.
- Changing status output, control verbs, or planning semantics.
- Typealiases in other modules.

## Dependency notes

Runs after SKILL-378 subtask 3 lands. It does not wait for another issue.
Restructure the goal-runner classes where they live, including coordination
classes that are still in sqlite only when this spec's criteria require editing
them in place. If raw-map or inbound-API scanners still skip files, repair those
scanners here so criteria 3 and 5 observe real source. Delete aliases and rename
ports types that the surface criteria still trip over. If slot packages already
exist, include them in the visibility pass.

## Validation strategy

The regressions to catch are a status projection that changes when read through
the query, a dissolved bag that drops a ledger or observability emission,
duplicate or reordered sequence numbers, an import resolving through a removed
alias, and a narrowed type that runtime-core's component still needs (a compile
failure). Use the goal-runner and status suites over real SQLite, compile all
modules, and add the tests listed above. Run `./gradlew check`, the engine, CLI,
MCP, core, and infra-sqlite suites, and `bill-unit-test-value-check`.

## Next path

Run `followup_feature-task-step-classes.md`, which widens this spec's step-class
rule to `skillbill.engine.featuretask`.

## Spec Path

.feature-specs/SKILL-378-runtime-engine-hexagonal-boundaries/followup_goal-runner-step-classes-and-engine-surface.md
