# SKILL-347 Subtask 3 - Simplify review composition and stateless use cases

Parent spec: [.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec.md](./spec.md)
Issue key: SKILL-347

## Scope

Own F-005, F-006, and F-007. Refactor review/ParallelCodeReviewRunner.kt and review/model/ParallelCodeReviewRunnerBoundaries.kt with their existing planning, lane-launch, result-assembly, and verification collaborators and runtime-core wiring. Remove the copied unused fields and the duplicate launcher and locator-reader bindings. Compose executable collaborators at the existing composition root or its established injection mechanism. The runner should sequence the use case. Do not move the same dependencies into another data class, expose all helper internals, or introduce an interface per collaborator.

Keep each collaborator's transaction, evidence, and failure contract. Preserve the existing review run request and result, tier selection, resume identities, persisted findings and verdicts, evidence accounting, and output prose. Keep the current provider staging adapter and the lifetime fix from subtask 1.

Delete TelemetryConfigMutationRuntime, which has no production caller, and remove TelemetryConfigRuntime forwarding by calling the existing domain parsers directly. Delete the production-unused ReviewCommitSequenceResolver and its parseCommitUnits forwarder, while moving useful parsing and assembly coverage onto SharedReviewEvidenceAssembler and SharedReviewEvidenceProjection. Remove prepareForFeatureImplement and prepareForGoal, which only tests call, from FeatureSpecPreparationRuntime. Keep its active injected preparation path and the documented InstallAgentService adaptation.

Replace UpdateCheckService.lastUnknown, releasePayloadMalformed, and releaseEntryMalformed with invocation-local return values. Keep the design local to this service, with no generic result library or parser framework. The goal is fewer mutable states and fewer steps between a caller and its actual operation.

## Acceptance Criteria

1. ParallelCodeReviewRunner no longer unwraps the two boundary data groups or manually reconstructs the complete review collaborator graph. The existing composition root creates the required collaborators with one authoritative launcher and evidence-reader binding.
2. Unused copied boundary fields disappear, helper visibility matches actual callers, and a collaborator receives only dependencies it uses. No replacement dependency bag or single-implementation role interface is added.
3. Existing inline, delegated, empty-delta, resumed-stage, evidence-refusal, verification, adjudication, and accounting behavior remains compatible at the runner entry point.
4. TelemetryConfigMutationRuntime and ReviewCommitSequenceResolver have no production declarations after their useful behavior coverage moves to live owners. TelemetryConfigRuntime callers use existing domain functions, and the two unused feature preparation aliases are removed.
5. UpdateCheckService has no mutable per-request parser flags or last-result fields. Deterministic overlapping calls cannot exchange failure reasons or contaminate a valid response with another response's malformed-entry state.
6. All existing update-check outcomes and installed-version rules remain compatible, including interruption propagation from subtask 1. No new transport or external API contract is introduced.
7. Record the before and after dependency and deletion census, and update affected source-shape architecture assertions to check the surviving ownership boundary. Keep behavioral tests, schema checks, and the documented InstallAgentService retention decision.

## Non-Goals

- No review pipeline framework, tier redesign, public command renaming, new package layer, or wholesale facade deletion.
- No class or file split based solely on constructor count or line count.
- No claim of a performance improvement without measurement.

## Dependency Notes

Depends on: none
No prerequisite. This is a separately shippable simplification commit. Apply it after the other subtasks in normal goal order to reduce conflicting edits, but it requires no new schema or API from them.

## Validation Strategy

Use the existing review entry-point and resume suites as the before and after behavior check. Add one controlled overlapping-call update-check regression with distinct valid and malformed responses. Run the application suite and relevant composition, dependency, public API, and package-cycle guards. Retarget useful tests from deleted compatibility paths to the production path and run bill-unit-test-value-check during implementation review.

## Next Path

All implementation subtasks are complete. Continue through the goal runtime completion path.

## Spec Path

.feature-specs/SKILL-347-runtime-application-ownership-and-simplification/spec_subtask_3_simplify-review-composition-and-stateless-use-cases.md
