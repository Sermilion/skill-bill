# SKILL-247 Subtask 3 - Narrow run-loop helper dependencies

Parent spec: [.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec.md](spec.md)
Issue key: SKILL-247

## Scope

Resolve F-005 in investigation.md. Own FeatureTaskRuntimeRunLoop, its context/session, PlanningBranch, Drive, ValidationGate, and AttemptSettlement helper families, adapting direct callers and tests in the same commit. Start with the helpers named in the investigation. Preserve the existing phase machine and run-state owner.

## Acceptance Criteria

1. FeatureTaskRuntimeRunLoopPlanningBranch receives the request facts, state operations, recorder, or observability capabilities each operation actually uses. Remove overloads that differ only by accepting a run loop versus its context or a repeated dependency list.
2. Build/validation settlement and carried-forward review helpers stop using FeatureTaskRuntimeRunLoopContext as an all-access receiver. Pure checkpoint, routing, and result calculations accept values. Effectful operations receive their specific existing ports or cohesive concrete collaborators.
3. The loop remains the owner of phase advancement and its session transitions. Helpers cannot acquire new authority merely by receiving a renamed context, a callback bag, or an interface exposing the same collaborators. Keep a context only at the orchestration owner if it still reduces repetition there.
4. Remove the public collaborator aliases on FeatureTaskRuntimeRunLoop where no external API consumer needs them. Establish necessity by call-site inspection across production, tests, and published entry APIs. Adapt callers directly rather than replacing every alias with a getter.
5. Preserve standalone and goal-child phase order, one bounded review-fix round, process-failure budgets, carried-forward review behavior, checkpoint ownership, and resume reconstruction. Keep sealed terminal outcomes and private mutable session storage.
6. Document the before/after dependency list for the named helper families and record any retained broad input with a concrete current requirement. The changed path must reduce shared access and duplicated forwarding; file-count and line-count reduction alone do not satisfy this criterion.
7. Use existing resume and gate tests to prove the refactor. Add only a missing realistic sequence exposed by changed ownership. Keep logical-type and package-cycle enforcement truthful; do not add a new receiver-count threshold or baseline exemption.

## Non-Goals

- No generic state-machine framework, new Gradle module, phase redesign, or port per helper.
- No whole-module value-class conversion or wholesale extension-function ban.

## Dependency Notes

Depends on: none
Independent of telemetry and adapter cleanup. Can ship separately because it preserves behavior and does not require their new contracts. Rebase caller changes if another process edits the run loop.

## Validation Strategy

Run affected FeatureTaskRuntimeRunner, run-state, carried-forward review, validation/build-gate, and goal-resume tests in runtime-engine. Run ApplicationPackageAcyclicityArchitectureTest and the existing composition/logical-type guards. Review the actual narrowed signatures and state writes alongside behavioral test results.

## Next Path

Continue with subtask 4 after the run-loop refactor passes its existing behavior tests.

## Spec Path

.feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/spec_subtask_3_narrow-run-loop-helper-dependencies.md
