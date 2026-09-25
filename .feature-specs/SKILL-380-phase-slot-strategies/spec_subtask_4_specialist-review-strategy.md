# SKILL-380 Subtask 4 - Specialist findings review strategy

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Resolves the behavioural half of investigation F-004. It adds the second
`code_review` strategy, the one feature-task used before commit `d6651d94b`
(2026-09-11). It proves that a profile can swap a slot's strategy without touching the
run loop.

**Reproduce first.** `d6651d94b` removed the specialist path because "isolated
findings-only review buffered until exit and hung".

- Before writing the strategy, reproduce that behaviour with a test. Drive
  `ParallelCodeReviewRunner` from the feature-task launch path with a lane agent that
  writes nothing until it exits.
- Record the cause in `runtime-kotlin/agent/decisions.md`.
- The strategy must not hang. Every lane launch must use a bounded progress policy,
  meaning the read-only idle policy with the progress timeout that verify_findings
  uses, or an equivalent strategy on `AgentRunProcessRequest`. A lane that emits
  nothing before exit must settle as a lane failure within that bound.
- If the cause is outside the review runner (for example in the launcher's output
  capture), fix it at its owner. Do not work around it inside the strategy.

**Strategy `specialist-findings` (slot `code_review`).** It is a new strategy in the
`code_review` slot package, beside `last-commit-fix`.

- **review step:** calls `ParallelCodeReviewRunner` with the subtask's reviewed commit
  range.
  - The runner produces a findings-only review: specialists routed by the dominant
    pack, learnings resolved by the driver as SKILL-379 delivers them, and merged
    findings.
  - The strategy then assembles the review envelope the existing
    `FINDINGS_VERIFICATION_INPUT` projection expects.
  - It does not edit files. Its review step policy is read-only and not mutating.
- **verify_findings and implement_fix:** run as they do under `last-commit-fix`. That
  means the same `review_fix` edge, the same cap, and implement_fix repairing verified
  findings with the remediation checkpoint. Reuse the existing step code; do not
  duplicate it.
- **`CodeReviewExecutionMode`:** it now takes effect here. INLINE and DELEGATED
  select the runner's inline or delegated lanes. AUTO resolves through
  `ReviewExecutionModePolicy` as it does for `skill-bill code-review`. The mode stays
  pinned on resume as today. The DELEGATED `commit_focused_accounting` gate applies
  unchanged.
- **Goal children:** review-pass reservation, carry-forward, and caps behave as they
  do under `last-commit-fix`.
- **Default:** `last-commit-fix` stays the `code_review` default. A profile selects
  `specialist-findings` with `code_review: specialist-findings`.

**Docs.** In the ARCHITECTURE.md strategy table and the `AGENTS.md` profile paragraph,
add the strategy, what it does differently, and how to select it.

## Acceptance Criteria

1. A test reproduces the findings-only hang against the pre-fix code path, and with the fix a lane that writes nothing until exit settles as a lane failure within the configured progress bound.
2. With `workflow_profile.slots.code_review: specialist-findings`, a feature-task run reviews through `ParallelCodeReviewRunner`, the review step edits no files, and verified findings reach implement_fix through the existing `review_fix` edge.
3. Under `specialist-findings`, INLINE and DELEGATED each launch the corresponding lane shape, and AUTO resolves the same way as `skill-bill code-review` for the same inputs.
4. Resuming a run started under `specialist-findings` runs `specialist-findings`, and resuming with an override to `last-commit-fix` blocks as pinned.
5. `skillbill_feature_task_runtime_finished` reports `code_review: specialist-findings` for such a run.
6. Runs without a `code_review` profile entry still use `last-commit-fix`, and the subtask 3 byte fixtures still match.
7. `agent/decisions.md` records the hang's cause and fix. ARCHITECTURE.md and `AGENTS.md` document the strategy and its selection.

## Non-goals

- Changing `last-commit-fix`, the default strategy, or `skill-bill code-review` output.
- Re-review after implement_fix. The `review_fix` cap stays 1.
- New specialist areas, rubric changes, or learnings behaviour beyond SKILL-379.
- Changing `ParallelCodeReviewRunner` beyond the hang fix and the inputs this strategy needs.

## Dependency notes

- Depends on subtask 3: profile selection, resume pinning, and the telemetry field.
- Use the learnings delivery that is on the tree.
- Use the review-preparation type and `AgentRunLaunchFacts` termination that exist now. If this strategy introduces a caller, update that caller when those types change inside this commit only as far as this subtask's criteria require.
- Skippable: waves A (1–3), B, C, and D are complete without it. The manifest marks its dependency on subtask 3 as non-optional, but the subtask itself can be marked skipped.

## Validation strategy

The regressions to catch:

- the review hanging on a silent lane
- specialist review editing files
- findings not reaching implement_fix
- mode drift between feature-task and standalone review
- the default strategy changing

What covers them:

- the reproduction test
- one real-SQLite run-loop test per mode with scripted lane agents
- a resume-pin test
- the telemetry golden
- the subtask 3 fixtures under the default profile

Run `cd runtime-kotlin && ./gradlew check`, plus the engine, application, core, and
launcher suites. Run `bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_5_isolated-engine-review-validation.md`. Waves B–D do not depend on this subtask.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_4_specialist-review-strategy.md
