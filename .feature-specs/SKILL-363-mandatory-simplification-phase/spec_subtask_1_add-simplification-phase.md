# SKILL-363 Subtask 1 - Add the mandatory simplification phase

Parent spec: [spec.md](spec.md)
Issue key: SKILL-363

## Scope

Deliver the whole feature in one reviewable commit. Add the `simplify` phase to
the feature-task runtime and retire the standalone
`bill-over-engineering-review` source and catalog entry.

Own the phase ID and workflow graph in
`runtime-kotlin/runtime-domain`, phase declarations and handoff projections,
phase prompt directives and composition in `runtime-kotlin/runtime-engine`,
phase output/persistence/resume seams and their tests, plus
`README.md` and `skills/bill-over-engineering-review/content.md` removal.

The phase runs after `implement` and before `audit`. It receives the runtime's
bounded current-subtask scope, reads the diff and owned paths, edits only
high-confidence feature-local excess, and returns a bounded change receipt.
The receipt must distinguish no-edit, addressed, and unresolved outcomes
without carrying a free-form review transcript. The next audit sees the
resulting tree and rechecks every planned acceptance criterion.

## Acceptance Criteria

1. `FeatureTaskRuntimePhaseIds`, the phase graph, labels, required artifacts,
   forward order, and resume action define `simplify` between `implement` and
   `audit`; `review` still requires a satisfied audit.
2. Simplify's launch briefing and handoff projection carry only the current
   subtask's scoped diff and owned paths, and the prompt forbids whole-tree
   discovery, out-of-scope edits, builds, tests, subagents, and delegated
   review.
3. Simplify's prompt admits only high-confidence local reductions such as dead
   feature-local code, one-use wrappers, unnecessary one-implementation
   abstractions, hand-rolled standard-library behavior, or equivalent local
   shrinkage, while explicitly protecting governed contracts, typed errors,
   loud-fail seams, parity tests, validator-backed rules, security,
   accessibility, and explicit requirements.
4. Simplify is classified as mutating and single-session, persists a bounded
   phase receipt, rejects malformed output through the normal retry path, and
   reconstructs correctly after interruption without replaying completed edits.
5. An simplify completion advances to audit on the changed tree; audit
   satisfaction remains the only route into review, and an audit gap still
   follows the existing audit-gap repair path.
6. Focused tests fail if phase order, scope projection, protected-surface
   wording, output validation, resume reconstruction, or audit-before-review
   routing regresses.
7. The governed removal path deletes the standalone over-engineering skill and
   README catalog row, the source installation does not recreate it, and
   `validate-agent-configs` passes.

## Non-goals

- No repository-wide complexity scan in the runtime phase.
- No new review specialist, platform-pack area, or separate persistence
  database.
- No change to code-review findings, review-fix behavior, validation commands,
  commit-before-review, or PR generation.
- No implementation of a shortcut-debt ledger or changes to the
  `shortcut:` marker convention.

## Dependency notes

None. This subtask owns the complete phase contract and its retirement of the
standalone skill; splitting runtime wiring from tests or source cleanup would
leave intermediate commits that cannot run the intended workflow.

## Validation strategy

Before each test, name the realistic regression it catches: simplify is
forward-reachable before implementation, audit is bypassed after simplification,
an out-of-scope file is editable, a protected contract is treated as bloat, a
malformed receipt advances the phase, resume duplicates a simplification, or
the removed skill is regenerated during install. Run the focused runtime
phase/prompt/handoff/persistence tests, `validate-agent-configs`, the
pack-declared quality gate, and the source installation flow. Apply
`bill-unit-test-value-check` to changed tests.

## Next path

None. This is the only subtask.

## Spec Path

.feature-specs/SKILL-363-mandatory-simplification-phase/spec_subtask_1_add-simplification-phase.md
