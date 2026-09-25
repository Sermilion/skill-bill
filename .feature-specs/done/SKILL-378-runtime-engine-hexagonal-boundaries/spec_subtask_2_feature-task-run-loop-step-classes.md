# SKILL-378 Subtask 2 - Break run-loop dependency cycles

Parent spec: [spec.md](spec.md)
Issue key: SKILL-378

## Why this subtask exists

The step-class follow-up (`followup_feature-task-step-classes.md`) turns the `object FeatureTaskRuntimeRunLoop*` namespaces into
constructor-injected classes. That only works if the step dependency graph has
no cycles: two classes that take each other as constructor collaborators cannot
be built. Two earlier implement attempts measured the graph and blocked. At that
point 16 of the 18 procedural objects formed one strongly connected component.

A regex census on 2026-09-25 showed where the cycles come from:

| State | Objects in cycles |
|---|---|
| Today | 18 in one component |
| Pure helpers moved out as top-level functions | 13, plus a 2-object cycle (`Drive` ↔ `BackwardEdge`) |
| Also move the shared block and phase-state-read primitives into one leaf | 3, plus a separate 4 |

Most back edges are calls to one shared primitive. `PhaseAttempts.blockInPhase`
has 24 cross-object call sites, and `blockAndPersist`, `blockAndPersistInPhase`,
`PlanningBranch.blockAt`, and `OutputPersistence.phaseStateRequest` add about 30
more. Steps reach back up the loop only to block or read state, not to
recurse.

This subtask removes the cycles while the code is still `object` namespaces and
while `FeatureTaskRuntimeRunLoopContext` is still the carrier. Everything it
changes is a move, so the follow-up's conversion becomes mechanical.

## Already met

Subtask 2's earlier attempts put the typed busy failure (`DatabaseBusyError` in
`skillbill.error.core`, plus the disposition check and its test), the removal
of the operator-block-retry inline wire keys, and the byte-identity fixture on
the branch. Recheck each one, then record it as met. The old subtask 2's
acceptance criteria 5, 6, and 7 are met and stay met.

## Scope

All changes are within `runtime-engine/.../engine/featuretask/runloop` and its
tests. This subtask changes no behavior.

1. **Recensus first.** Recompute the cross-object call graph of the
   `object FeatureTaskRuntimeRunLoop*` namespaces from source. Count an edge when
   object A calls a function of object B. Record the strongly connected components
   (SCCs) in the phase output before editing. If the counts differ materially
   from the table above, continue with the steps below, targeting the measured
   edges instead of the named ones.
2. **Pure helpers out.** A function that takes neither
   `FeatureTaskRuntimeRunLoopContext` nor a collaborator (recorder, gates,
   validator, launcher, writer, diagnostics, git operations, session) moves out
   of its object and becomes an `internal` top-level function in the same
   package. Name the file after the concept, not the old object. This is
   allowed by the "pure functions stay top-level" rule that the follow-up enforces.
   Examples from the census: `composeLaunchPrompt`,
   `composeLaunchPromptInputs`, `packBuildCommand` (it is duplicated in
   `OutputPersistence` and `ValidationGate`, so keep one), `resolveReviewPromptTier`,
   and `Transitions.qualityGateSelection` where it does not need a collaborator.
3. **One leaf for blocking and phase-state reads.** Create one
   `object FeatureTaskRuntimeRunLoopPhaseBlocking` in `runloop/core`. It becomes a
   class in the follow-up. Move into it the primitives that record a block or
   read phase state, and that depend only on the recorder, state, session,
   observability, clock, and diagnostics:
   `PhaseAttempts.blockInPhase`, `blockAndPersist`, `blockAndPersistInPhase`,
   `operatorReopenedPhase`, `PlanningBranch.blockAt`, `persistBranchSetupBlock`,
   `goalReviewStateOrNull`, `priorBlockerFindingIds`, `persistResolvedReviewTier`,
   and `OutputPersistence.phaseStateRequest`, `reviewPassNumber`. The thin
   wrappers that only forward to those primitives, `Checkpoint.blockCheckpoint`,
   `CheckpointRemediation.blockCheckpointScope`, `RepairReceipt.blockRemediationBaseSha`,
   and `RecordRejection.blockUnattributableRecordRejection`, move there too, or
   callers call the primitive directly. The leaf calls no other
   `FeatureTaskRuntimeRunLoop*` object. If a candidate needs a step object, it
   stays where it is and step 4 handles its edge.
4. **Break the residual cycles by direction.** Measure again after steps 2
   and 3. For the census's two residual components, the fix is:
   - `Checkpoint` ↔ `CheckpointRemediation` ↔ `RepairReceipt`: dependencies point
     from `CheckpointRemediation` and `RepairReceipt` to `Checkpoint`, never
     back. Where `Checkpoint` currently calls into remediation, it returns a
     typed outcome (a `sealed interface` result or an enum over existing
     values) that the caller acts on. Merging the three is not allowed because
     together they exceed 1,200 lines.
   - `Launch` ↔ `OutputPersistence` (with `ValidationGate`, `RecordRejection`):
     launch preparation (`OutputPersistence.prepareLaunch`,
     `launchedModelDirective`, and the prompt-composition helpers left after
     step 2) moves into `Launch`. `OutputPersistence` then depends on `Launch`,
     never the reverse. Break `RecordRejection` → `OutputPersistence` /
   `AttemptSettlement` and `ValidationGate` → `RecordRejection` the same way:
     move the function to the lower layer or return an outcome.
   Any other cycle the recensus finds, including the legitimate recursive
   drive loop (`Drive` → `BackwardEdge` → `PlanningBranch` → `PhaseRunner`), is
   broken the same way. The inner step returns an outcome value, for example
   rerun this phase, advance, or blocked. `Drive` or `PhaseRunner` dispatches on
   it instead of the inner step calling back up. Keep the existing
   control-flow semantics exactly: same phase order, same retry budgets, same
   persisted records in the same order.
5. **Acyclic guard.** Add a rule to the existing
   `RuntimeEngineBoundaryArchitectureTest`. It builds the object-to-object call
   graph for `featuretask/runloop` from source, as in step 1, and fails when it
   contains a cycle. The failure names each cycle's members. In the follow-up the
   rule applies to the classes, keyed by type references instead of
   `Object.fn` calls. Write it so the node is any `FeatureTaskRuntimeRunLoop*`
   declaration (object or class) and an edge is any reference from one
   declaration's body to another. That way it survives the conversion unchanged.

## Acceptance Criteria

1. The acyclic rule passes on the branch. On a synthetic pair of
   `featuretask/runloop` objects that call each other, it fails and names both.
2. No `FeatureTaskRuntimeRunLoop*` object calls a function of a
   `FeatureTaskRuntimeRunLoop*` object that transitively calls back to it.
   Criterion 1 is the enforcement.
3. `FeatureTaskRuntimeRunLoopPhaseBlocking` references no other
   `FeatureTaskRuntimeRunLoop*` step object.
4. No file exceeds 1,200 lines or 40 functions, and no function exceeds six
   parameters. No new suppression, baseline row, or threshold change.
5. Existing run-loop suites over real SQLite pass unchanged apart from imports
   and call-site renames: phase order, backward edges, checkpoint identity,
   resume from durable records, validate and review gates, commit finalization.
6. Old criteria 5 to 7 (typed busy disposition, no `"[SQLITE_BUSY]"` message
   check in runtime-engine, no inline wire key with byte-identical encoding)
   still hold.
7. The phase output records the SCC census before and after.

## Non-goals

- Converting objects to classes, deleting `FeatureTaskRuntimeRunLoopContext` or
  other bags, runner extension files, private inject properties, or the
  step-class guard. All of that is the step-class follow-up.
- Interfaces for step classes, a step framework, forwarding classes, lazy or
  provider injection to paper over a cycle, or per-run DI subcomponents.
- Changing persisted data, phase order, retry budgets, or failure identities.
- Goal runner and planning (subtask 3).

## Dependency notes

Depends on subtask 1. It does not wait for another issue. Break the cycles in
the feature-task call sites that exist now, using typed snapshots, artifact
accessors, and git results where they are already on the tree and the current
call shape where they are not. Edit architecture tests where they live.

## Validation strategy

The regressions to catch are a moved block primitive that drops a side effect
(for example, a continuation-recorder write or an observability line), and an
outcome-return rewrite that changes which phase runs next or the order of
persisted records. The existing end-to-end run-loop suites cover both. Add
tests only for a new outcome type whose dispatch no existing test reaches. Run
`cd runtime-kotlin && ./gradlew check`, the engine, core, and infra-sqlite
suites, and `bill-unit-test-value-check` on changed tests.

## Next path

The goal pauses after this subtask (`--stop-after-subtask 2`). Run the
step-class follow-up (`followup_feature-task-step-classes.md`) next, then
resume the goal for subtask 3.

## Spec Path

.feature-specs/SKILL-378-runtime-engine-hexagonal-boundaries/spec_subtask_2_feature-task-run-loop-step-classes.md
