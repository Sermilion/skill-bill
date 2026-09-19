# SKILL-364 Subtask 1 - Make PR readiness authoritative

Parent spec: [spec.md](spec.md)
Issue key: SKILL-364

## Scope

Deliver the complete change in one reviewable commit. Add a durable
PR-readiness boundary after validation that snapshots the final source tree and
base ref, derives the applicable repository checks from governed declarations,
reruns invalidated checks, and blocks commit or PR progression until every
selected check has a recorded passing result.

The boundary must cover the Kotlin runtime quality gate and the IntelliJ plugin
check when their paths are affected. It must distinguish source changes from
allowed boundary-history output, report stale base/head identities, and retain
failure or degradation evidence instead of treating an absent check as a pass.

## Acceptance Criteria

1. A final readiness record binds validation results to the exact tree identity,
   base-ref identity, selected checks, and completed check results before
   commit/push or PR creation.
2. A source edit after validation invalidates only the affected check evidence
   and blocks progression until that check is rerun; boundary-history output
   does not invalidate unrelated source checks.
3. Runtime Kotlin paths select the pack-declared quality gate, while
   `intellij-plugin/**` selects the plugin check equivalent to
   `intellij-plugin/gradlew clean check --no-build-cache`.
4. A changed base ref or mismatched head/tree identity blocks stale readiness
   evidence and reports both identities in the durable diagnostic.
5. Failed, skipped, missing, or unpersisted selected checks cannot settle
   readiness as passing, and each fallback or degradation emits the required
   observability record.
6. Focused tests catch the PR #389 shape: a Detekt violation introduced after
   validation and a plugin test failure outside the Kotlin runtime gate.
7. Existing feature-task phase transitions, commit-before-review rules,
   same-branch commit ownership, and CI-required checks remain unchanged.

## Non-goals

- No replacement or weakening of GitHub Actions.
- No repository-wide workflow execution for every feature.
- No hard-coded list of all current platforms or future plugin types.
- No unrelated changes to IntelliJ status mapping or macOS timing behavior.

## Dependency notes

None. This subtask owns the readiness contract, applicable-check routing,
invalidation and persistence seams, and their focused tests; splitting those
pieces would leave an intermediate commit that cannot enforce the intended
boundary.

## Validation strategy

Before each test, name the realistic regression it catches: final edits escape
validation, plugin paths are ignored, a moved base ref is accepted, history
writes force needless reruns, a failed selected check settles as passing, or a
missing result is swallowed. Run focused readiness, routing, identity,
invalidation, persistence, and commit/PR transition tests, then the Kotlin
quality gate, IntelliJ plugin check, repository validation, and installation
flow. Apply `bill-unit-test-value-check` to changed tests.

## Next path

None. This is the only subtask.

## Spec Path

.feature-specs/SKILL-364-ci-validation-parity/spec_subtask_1_make-pr-readiness-authoritative.md
