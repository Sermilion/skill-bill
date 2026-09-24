# SKILL-372 Subtask 2 - Domain-owned artifacts, shared rules, and adapter-side validation

Parent spec: [.feature-specs/SKILL-372-runtime-domain-hexagonal-boundaries/spec.md](spec.md)
Issue key: SKILL-372

## Scope

Resolve F-005, F-006, F-007, and F-008 in [investigation.md](investigation.md). Recheck every anchor against the post-SKILL-370 and post-SKILL-376 tree first.

**Artifacts (F-005).**

- For each artifact family read or written outside runtime-domain, add a typed read, and a write where the artifact is written, as an extension on `DurableWorkflowArtifacts` in the family's owning domain package. Implement each through the existing `fromArtifactMap`/`toArtifactMap` or decoder.
- Migrate the raw reads in engine, application (including the code SKILL-370 moved into domain `workflow.decomposition`), SQLite, and MCP, including the literal-key reads.
- Make domain `*_ARTIFACT_KEY` constants `internal`. Keys that engine alone owns stay there.
- Delete the engine and SQLite `goalContinuation(artifacts)` copies and engine `FeatureTaskRuntimePhaseArtifactDecoders.kt`.

**Shared rules (F-006).**

- Move into runtime-domain:
  - the goal-parent artifact projection, taking an encoded manifest map
  - the decomposed-parent predicate
  - decomposition-runtime decode from snapshot artifacts
  - the projection-failure artifact entry shape
- Collapse the duplicate shells: `GoalParentProjectionWriter`, `GoalContinuationArtifactCodec`, `DecompositionWorkflowRuntimeLookup(+ParentDiscovery)`, `DecompositionManifestProjectionFailurePersistence`, and the review-policy/out-of-band decode. Keep one owner per shell in engine or application; engine may call application.
- Reconcile the 14 diverged twins SKILL-376 subtask 2 carries into engine as private functions (listed in its commit body): `clearDecompositionManifestProjectionFailure`, `decompositionRuntime`, `findDecomposedParentOrCorruptFallback`, `findMatchingDecompositionManifests`, `goalContinuation`, `goalRepositoryIdentity`, `goalReviewArtifacts`, `goalReviewEmissionEnvelope`, `hasDecompositionPlan`, `isGoalContinuationChildWorkflow`, `loadDecompositionManifest`, `missingResultPrefixTerminalOutcomeArtifact`, `persistDecompositionManifestProjectionFailure`, `validatedGoalReviewPasses`. Each ends with one definition, and each semantic difference is kept or removed deliberately with a test. `reviewPolicyFromLegacyArtifacts` and `outOfBandAcceptancesFromLegacyArtifacts` stay in SQLite with their ledger migration.
- Reconcile those twins wherever they live. If the SQLite copies have not moved into the engine, collapse them in place so each rule has one definition.
- If `GoalRepositoryIdentity` is one of the duplicated rules, give it one definition in this commit.

**Validation (F-007).**

- Remove validator parameters from the 27 domain call sites. Callers validate at the adapter or application seam before decoding, or after encoding.
- Delete the 12 forwarding `validateX` extensions; callers use `validate(kind, …)`.
- Move `FeatureTaskRuntimeWireArtifactValidator`, `FeatureTaskRuntimeWireArtifactKind`, `FeatureTaskRuntimePhaseOutputValidator`, `DecompositionManifestValidator`, `InstallPlanWireValidator`, and `ReviewContextEnvelopeValidator` to `runtime-ports`.
- Put `decodeManifest`/`encodeManifestWireMap` in the file that declares `DecompositionManifestValidator`.
- Before moving, type `FeatureTaskRuntimeWireArtifactValidator.validate`'s `payload: Any` and `FeatureTaskRuntimePhaseOutputValidator.validateAndReadPhaseOutput(): Any` with the family's typed wire carrier (not a raw map), or delete `validateAndReadPhaseOutput` if `normalizePhaseOutput` serves its 3 callers. Remove the `= Unit` default on `ReviewContextEnvelopeValidator.validateSpecIntentProjection`.
- Delete the three goal validator aliases and the three `Noop*` fixtures, and rename the `infra/contracts` class that shares the wire-artifact port's name.
- Domain owner packages for SKILL-376's codec fallback: phase records, phase ledger, and artifact encoding in `skillbill.workflow.taskruntime.artifact`; decomposition runtime in `skillbill.workflow.decomposition.runtime`. SQLite `resolveDecompositionManifest` is not domain-ownable and collapses into application's copy.
- At `FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint.kt:84-89` (SKILL-378 F-010), write the operator-block-retry payload through its domain owner.

**Coercion (F-008).** Keep `DurableArtifactMapReader`'s exact coercion and `AttemptLedgerWorkflowDecoding.asLenientIntOrNull`. Migrate `asGoalRunnerIntOrNull`, `asGoalObservabilityIntOrNull`, the plan-outcome `asIntOrNull`, the three `asIntegerOrNull`, and the duplicate exact coercions in `WorkflowEngineNumericCoercion.kt` and `ReviewRunLaneSegmentAccountingJson.kt`, then delete them.

**Decisions.** Record decisions superseding the 2026-09-06 duplicated-cluster entry and decision (a) of the 2026-09-16 runtime-domain entry, plus the artifact-ownership rule.

## Acceptance Criteria

1. Every domain `*_ARTIFACT_KEY` constant is `internal`. No main source outside runtime-domain indexes a workflow artifact map with a domain key or a literal key.
2. Each accessor returns, for every supported fixture, the same value the replaced raw read produced.
3. A malformed goal-continuation artifact raises the typed error on the engine and SQLite (or post-376 engine) paths that previously returned `null`, and tests assert the quarantine or block outcome.
4. Each of the 14 diverged twins has exactly one definition in runtime-kotlin main source. The pure rules listed in scope are declared in runtime-domain without `java.nio` or `skillbill.ports` imports. No other module holds a copy of their bodies, and goal-parent rewrites, decomposition lookups, and legacy control migration produce byte-identical artifacts to baseline fixtures.
5. No runtime-domain function takes a schema-validator port, runtime-domain declares no validator interface, and runtime-domain `testFixtures` holds no `Noop*` validator. The moved validator interfaces declare no `Any`-typed member and no constant-result default.
6. Every existing schema rejection test still rejects, now at an adapter or application seam. A malformed artifact written through an engine or SQLite path is rejected before it is persisted.
7. runtime-domain has one exact and one documented lenient integer coercion. A non-integral number on a formerly truncating path raises the family's typed error, and supported integral values decode unchanged.
8. The restored raw-map guard (SKILL-371) reports no violation introduced by this subtask.
9. `../../../agent/decisions.md` records both superseding decisions and the artifact-ownership rule.

## Non-goals

No aggregate shape change (subtask 1). No package moves, aliases other than the three validator aliases, or visibility sweep (subtask 3). No change to goal-runner transaction scope or statement order. No typed models for artifact payload internals.

## Dependency notes

Depends on subtask 1: accessors extend the typed `artifacts` field, and validator moves build on the write-seam validation from subtask 1. Do the collapse on the current tree. It does not wait for another issue.

## Validation strategy

Name the regression before each test: a malformed goal-continuation artifact that engine read as "not a goal child" must now quarantine; a `2.7` count that decoded as `2` must now fail typed; a goal-parent rewrite must write the same artifacts from every caller.

- Test accessors with valid and malformed fixtures, plus one real caller per module.
- Move each schema rejection test to its new seam.
- Diff goal-parent, decomposition, and migration artifacts against baselines.
- Run the runtime-domain, runtime-ports, runtime-application, runtime-engine, infra-sqlite, infra-contracts, runtime-mcp, runtime-cli, and runtime-core architecture suites, then the governed quality gate.
- Apply `bill-unit-test-value-check` to changed tests.

## Next path

Continue to `spec_subtask_3_aliases-packages-and-surface.md` after this subtask settles.

## Spec Path

.feature-specs/SKILL-372-runtime-domain-hexagonal-boundaries/spec_subtask_2_domain-owned-artifacts-and-shared-rules.md
