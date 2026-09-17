# SKILL-351 Subtask 2 - Restore ownership and typed boundaries

Parent spec: [.feature-specs/SKILL-351-runtime-domain-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-351

## Scope

Resolve F-005, F-008, F-009, and F-011 in [investigation.md](investigation.md).

Validator ports: pick one home and record it in `../../../runtime-kotlin/ARCHITECTURE.md` and the module `agent/decisions.md`. Remove production logic from `FeatureTaskRuntimePhaseOutputValidator` and `DecompositionManifestValidator` default bodies and the `= Unit` default from `FeatureTaskRuntimeHandoffFoundationValidator`; the adapter or a domain function owns that logic. Collapse the seven identical `(Any, String)` ports into one port keyed by a closed artifact-kind enum, or give each a named payload type; remove the forwarding adapters and fixtures the change makes redundant. Move SKILL-numbered narrative KDoc to `agent/decisions.md`.

Ownership: move `learningEntryDto`, `learningEntrySessionJson`, and the other DTO helpers from `skillbill.learnings` to their application owner and delete the duplicate overload. Move `SkillBillVersion` to `runtime-core` or make its fallback observable, and extend `AmbientEnvironmentArchitectureTest` to classpath reads or document the exception. Relocate each `WorkflowBoundaryCollections.kt` wrapper to its area's `model` package or remove it. Replace the two `workflowName == FeatureTaskRuntimePhaseWorkflowDefinition.definition.workflowName` comparisons in `workflow.engine` with a `WorkflowDefinition` field the feature-task definition sets, removing the engine's import of `taskruntime`.

Typing: carry `WorkflowStatus`, `WorkflowStepStatus`, and `WorkflowResumeMode` through `WorkflowStateSnapshot`, `WorkflowUpdateInput`, `WorkflowStepState`, `WorkflowDefinition`, and the engine functions; convert with `wireValue` at the snapshot codec and acknowledgement seams only.

Keys: for each `toArtifactMap`/`fromArtifactMap` pair, declare every governed key once in the owning `*Keys` object and reference it from both sides. Extend `WireVocabularyGovernedSeamInventory` so these pairs are scanned.

## Acceptance Criteria

1. Validator ports have one documented home. No port carries a production default body or a no-op default. The seven identical-shape ports are one port with a closed artifact kind, or each names its payload type; adapters and fixtures shrink to match. Every validator adapter still runs its schema validation and every existing rejection test still passes.
2. Domain contains no mapping to `runtime-contracts` DTOs; the learnings application service produces the same session JSON as before, proven by fixture comparison.
3. `SkillBillVersion` lives in `runtime-core`, or its fallback emits a record; the ambient-environment scanner covers `getResourceAsStream` or `ARCHITECTURE.md` documents the single exception.
4. `workflow.engine` imports nothing from `workflow.taskruntime`; the runtime-specific continuation branch fires for the feature-task definition exactly as before and not for other definitions. The acyclicity scan at the module's documented granularity reports no `engine` ↔ `taskruntime` cycle.
5. Engine models and `WorkflowDefinition` carry status enums; snapshot JSON, step JSON, and acknowledgement payloads are byte-identical for supported statuses; an unknown durable status token raises the typed error from subtask 1 or the current typed error if subtask 1 has not landed.
6. Every governed key in each artifact encode/decode pair references one `*Keys` declaration; the governed-seam inventory scans those pairs; a fixture with an undeclared literal key at such a seam fails the scanner.
7. Documentation describes the actual port home, wrapper policy, key ownership, and scanner coverage. No new baseline rows, exemptions, or suppressions.

## Non-goals

No change to schema validation semantics or schema files. No relocation of ports to `runtime-ports` unless that is the chosen home. No renaming of the `FeatureTaskRuntime*` prefix. No deletion of unreferenced code beyond what a move makes dead; that is subtask 3.

## Dependency notes

Depends on: none. Can ship before or after subtask 1; criterion 5 names both outcomes. Rebase on the branch head before starting because subtask 1 edits the same decoders.

## Validation strategy

Assert relocated behaviour through the new owner and the absence of the old import edge through the acyclicity scan. Compare learnings session JSON and workflow snapshot bytes against fixtures captured before the change. Exercise the collapsed or retyped ports through their adapters with valid and invalid payloads. Run the scanner fixture for an undeclared literal key. Run runtime-domain, runtime-application, runtime-infra-fs, runtime-core architecture guards, and the governed quality gate. Apply bill-unit-test-value-check to changed tests.

## Next path

Continue to `spec_subtask_3_shrink-surface-and-merge-count-split-units.md` through the goal runtime after this subtask settles.

## Spec Path

.feature-specs/SKILL-351-runtime-domain-boundaries-and-simplicity/spec_subtask_2_restore-ownership-and-typed-boundaries.md
