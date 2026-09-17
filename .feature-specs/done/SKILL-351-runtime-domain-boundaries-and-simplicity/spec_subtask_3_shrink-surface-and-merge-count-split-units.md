# SKILL-351 Subtask 3 - Shrink surface and merge count-split units

Parent spec: [.feature-specs/SKILL-351-runtime-domain-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-351

## Scope

Resolve F-006, F-007, and F-010 in [investigation.md](investigation.md).

Re-run the reference census described under F-007 in [investigation.md](investigation.md) against the current branch. Delete declarations with no reference anywhere, make same-file-only declarations `private` and module-only declarations `internal`, remove `TelemetryRemoteStatsRuntime` in favour of its top-level function, and drop `implementation(libs.kotlinx.serialization.json)` from `runtime-domain/build.gradle.kts`.

Merge the `FeatureTaskRuntimeProjectionCanonicalizer*` family and `GoalObservabilityParsing{,Collections,Fields}` into the units their responsibility defines; evaluate `FeatureTaskRuntimeHandoffProjection*`, `WorkflowEngine*`, and `FeatureTaskRuntimePhaseWorkflow*` by responsibility and merge where a file is a helper bucket rather than a named responsibility. Either populate `FEATURE_TASK_RUNTIME_CLOSED_PROJECTION_OBJECT_KEYS` from its schema authority so the parity `repoTest` can fail, or delete the branch, constant, and test.

Convert `AttemptLedgerAccumulator` to a pure reduction returning `GoalRunnerAttemptLedgerSummary`, return truncation records from the repair-receipt and rejected-verification decoders as part of their result, and make `InstallTransaction.createdSymlinks` immutable with a named transition.

## Acceptance Criteria

1. Every declaration deleted appears in the re-run census as unreferenced; every module in runtime-kotlin compiles; the full runtime-kotlin test suite passes; the surface census shows zero public domain declarations with no consumer outside the module except those a written note keeps for documented generated or reflective use.
2. `TelemetryRemoteStatsRuntime` is gone and its callers use the top-level function. `runtime-domain/build.gradle.kts` declares no serialization dependency and the inward-layer import rule still passes.
3. The canonicalizer is one or two files under the 1,200-line and 40-function ceilings with no qualified sibling-object call chains; `GoalObservabilityParsing` is one unit. Remaining multi-file families are split by named responsibility, and the note that justifies each remaining split is in the module `../../../agent/decisions.md`.
4. The closed-key branch either discards unknown top-level keys for every projection kind its schema authority lists, with a parity test that fails when a schema property lacks a Kotlin owner, or the branch, constant, and `repoTest` are deleted. Canonicalization output for existing fixtures is unchanged.
5. No domain model exposes a public `var` or mutable collection. `AttemptLedgerAccumulator`'s consumer reads the same summary for the same ledger entries. Truncation records reach the same callers through the decode result.
6. Spillover, line-ceiling, logical-type, and clustering guards pass with no new exemption or baseline row.

## Non-goals

No behaviour change in canonicalization, ledger accounting, or install apply. No renaming for style. No move of ownership or port shape; that is subtask 2. No change to decode failure identities; that is subtask 1.

## Dependency notes

Depends on: none. Rebase on the branch head before starting because subtasks 1 and 2 edit the canonicalizer, decoders, and wrappers this subtask merges or deletes.

## Validation strategy

Compile and run the full runtime-kotlin suite after deletions; text greps are not sufficient. Snapshot canonicalizer diagnostics and ledger summaries for existing fixtures before merging and assert identity after. Run the architecture guards named in criterion 6 and the governed quality gate. Apply bill-unit-test-value-check to changed tests and delete tests whose only subject was removed.

## Next path

Final subtask. Completion closes SKILL-351 through the goal runtime.

## Spec Path

.feature-specs/SKILL-351-runtime-domain-boundaries-and-simplicity/spec_subtask_3_shrink-surface-and-merge-count-split-units.md
