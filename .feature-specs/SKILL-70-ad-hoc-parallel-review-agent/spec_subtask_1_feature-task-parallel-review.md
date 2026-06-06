# SKILL-70 Subtask 1 — Feature-Task Parallel Review

Created: 2026-06-06
Status: complete
Issue key: SKILL-70
Subtask id: 1

## Scope

Add `parallel_review:<agent_id>` as a run-scoped modifier for `bill-feature` /
`bill-feature-task`. During the review phase of the feature-task runtime, launch
the default resolved review agent and the alternative review agent independently
against the same diff, merge findings with provenance, and feed the merged result
into the existing fix-loop, telemetry, and workflow status surfaces.

## Acceptance Criteria

See the parent spec (`spec.md`) acceptance criteria AC1–AC14. All criteria are
scoped to the feature-task runtime and CLI entry point.

## Non-Goals

- Standalone code-review skills (`bill-code-review`, `bill-kotlin-code-review`,
  `bill-kmp-code-review`, etc.) — covered by subtask 2.
- Permanent config defaults for parallel review.
- More than one alternative lane per run.

## Dependency Notes

No upstream subtask dependency. This is the foundation; subtask 2 builds on the
validated modifier syntax and supported-agent contract established here.

## Validation Strategy

- Maintainer validation: `skill-bill validate`, `./gradlew check`, `npx agnix --strict .`, `scripts/validate_agent_configs`
- Runtime unit tests in `FeatureTaskRuntimeRunnerTest` and companion test classes
- CLI parser tests for `--parallel-review-agent`
- Telemetry contract tests for parallel-review dimensions

## Status

Complete. Implemented on branch `feat/SKILL-70-ad-hoc-parallel-review-agent`,
workflow `wftr-20260606-170508-mzoy`, commit `8648aed8`. PR #162.
