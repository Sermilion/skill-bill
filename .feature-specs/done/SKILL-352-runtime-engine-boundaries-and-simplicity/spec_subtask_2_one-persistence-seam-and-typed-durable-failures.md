# SKILL-352 Subtask 2 - One persistence seam and typed durable failures

Parent spec: [.feature-specs/SKILL-352-runtime-engine-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-352

## Scope

Resolve F-003, F-004, F-009, and F-010 in [investigation.md](investigation.md).

Own `FeatureTaskRuntimePhaseRecorderApis.kt`, `FeatureTaskRuntimePhaseRecorder.kt`, the ten `*Recorder` classes and `FeatureTaskRuntimeWorkflowPersistence.kt`, the 19 files that call `decodeWorkflowArtifacts(record.artifactsJson)`, `GoalRunnerChildRepairWedgeApplyLoop.kt`, `FeatureTaskRuntimeWireMapping.kt`, `GoalPlanningSharedContextPacket.kt`, `GoalPlanningSharedContextPacketValidation.kt`, `GoalPlanningSharedContextPacketLegacy.kt`, `GoalRunnerWorkflowFamilyLookup.kt`, `GoalContinuationArtifactCodec*.kt`, `GoalPlanningPreparationCheckpoint.kt`, `GoalPlanningPreparationRecordMapping.kt`, `GoalChildPlanningHydrator.kt`, `GoalRunnerLaunchModels.kt`, `FeatureTaskRuntimePlanningStopper.kt`, `FeatureTaskRuntimePhaseLaunchBriefing.kt`, `RuntimeOwnedPersistenceBoundary.kt`, `FeatureTaskRuntimeRunLoopModels.kt` (the runtime-fact exceptions), `GoalRunnerExecutionCoordinator.kt`, and the governed-seam inventory in `runtime-core`.

Delete the ten role interfaces and the `Parts` bundle. Extend the existing persistence owner with typed read and write operations that decode the column once and patch once, and route the 19 files through it. Route the goal-planning packet and review-policy decoders through typed errors, delete the `IllegalArgumentException` recoveries and the engine reader in favour of the shared reader from SKILL-351 subtask 1, remove the `!!` through non-null decoder overloads in domain, and move the three runtime-fact exceptions under the runtime taxonomy. Declare each artifact key once and add these seams to the governed inventory.

## Acceptance Criteria

1. The ten `FeatureTaskRuntimePhase*Api` interfaces and `FeatureTaskRuntimePhaseRecorderParts` no longer exist. `FeatureTaskRuntimePhaseRecorder` keeps its current method surface by delegating to concrete recorder members or exposes those members by name; every current call site compiles. No interface with one implementor and no test substitute is introduced.
2. `record.artifactsJson` is spelled in one engine file. The other files use typed `read`/`write` operations on the persistence owner; `persistPatch` call sites outside that owner are zero; `state.patch["goal_continuation_outcome"] = null` and similar raw-map writes are typed writes.
3. Malformed `_goal_planning_shared_context` packets and `agent_addon_selection` artifacts raise their families' typed errors, not `IllegalArgumentException` or `IllegalStateException`; the block and quarantine paths that catch the typed error receive every such case. `FeatureTaskRuntimeWireMapping.kt` and the `catch (IllegalArgumentException)` recoveries in `FeatureTaskRuntimePlanningStopper` and `FeatureTaskRuntimePhaseLaunchBriefing` are deleted. If SKILL-351 subtask 1 has not landed, the engine reader is `internal`, has one exact integer coercion, and is named for replacement in the decision log.
4. No `!!` follows a `decode*FromArtifact` call; the domain exposes non-null overloads for callers that have already established the input, and the nullable forms remain for callers that have not.
5. `RuntimeOwnedFactUnavailable`, `MissingCarriedForwardGoalReviewResultException`, and `GoalRunnerExecutionAlreadyRunningException` extend the runtime exception taxonomy; any `catch (IllegalStateException)` that relied on the old hierarchy is replaced by the typed catch.
6. Each engine artifact encode/decode pair references one owning `*Keys` object for every governed key. `GoalPlanningSharedContextPacket.PACKET_FIELDS`, `isImplementationReturnContract`, and `GoalContinuationArtifactCodec` reference declared owners; the governed-seam inventory scans these pairs and a literal governed key inside them fails the wire-vocabulary test. Prose, prompt text, and pack-authored payloads stay open.
7. Existing rows decode to the same artifacts and supported artifacts encode to the same bytes before and after, proven by round-trip fixtures per artifact family and a snapshot diff.

## Non-goals

No new port, module, or repository layer; no change to unit-of-work transaction ownership; no move of recorders into `runtime-ports`. No typed replacement of `Map<String, Any?>` beyond the seams named here. No visibility narrowing or dead-code deletion; that is subtask 3.

## Dependency notes

Depends on: none within this spec. Cross-spec: prefers the shared raw-map reader from SKILL-351 subtask 1; criterion 3 names the fallback if it is absent. Subtask 1 of this spec may land first or second; rebase either way.

## Validation strategy

Name the regression before each test: a durable packet that today crashes a read must reach the block path with a typed reason; a valid artifact must decode identically through the new owner; an inlined key drift between encoder and decoder must fail the wire-vocabulary test. Build malformed fixtures for the two goal-planning decoders. Round-trip every artifact family through the persistence owner and diff bytes. Run the engine suite, `runtime-infra-sqlite` and `runtime-domain` suites where decoder overloads are added, the `runtime-core` wire-vocabulary and typed-parse-boundary guards, and the governed quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Continue to `spec_subtask_3_record-fallbacks-restore-ownership-and-shrink-surface.md` through the goal runtime after this subtask settles.

## Spec Path

.feature-specs/SKILL-352-runtime-engine-boundaries-and-simplicity/spec_subtask_2_one-persistence-seam-and-typed-durable-failures.md
