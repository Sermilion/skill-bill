# SKILL-380 Subtask 3 - code_review strategy inline (InlineReviewStrategy)

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Resolves the structural half of investigation F-004 and the review part of F-005.

Subtask 2 registered today's review as a wrapper strategy with id `inline`. This
subtask makes it `InlineReviewStrategy`: today's `last-commit-fix` behaviour, renamed,
moved into the `code_review` slot package under `skillbill.engine.featuretask.slot`,
with a per-call review target.

**Behaviour (unchanged).** One agent reviews the target in its own session with no
subagents, fixes Blocker and Major findings in that session, and reports the remaining
findings and a verdict (`approved` / `changes_requested`).

**Review target.** The target is a per-call fact, not something the strategy decides.
The skeleton passes the subtask's last commit against its first parent, and the
composed prompt text for that target is byte-identical to today's. Subtask 8 passes
`HEAD`, `uncommitted`, or a commit. `FeatureTaskLastCommitReviewDriver`'s last-commit
prompt becomes the strategy's directive, parameterised by the target.

**Move in:**

- the review cycle
- goal review-pass reservation, carry-forward, and caps
- review-generation invalidation
- `review_fix` re-entry handling and the remediation checkpoint
- verify_findings launch specifics (spec-intent section, boundary memory, read-only
  idle)
- implement_fix coverage relaunch and receipt checks
- the review parts of output verification, now as the strategy decoding its own
  step values (parent "Phase input and output"); the runner decodes nothing

**Rules:**

- Every step runs through the strategy's own `PhaseRunner` instance. Review-pass
  reservations, carry-forward, review generations, and the remediation checkpoint are
  read and written through `PhaseRunState`, so the strategy runs unchanged over the
  in-memory state in subtask 8.
- Extract verify_findings and implement_fix into step classes in the `code_review` slot
  package, so `DelegatedReviewStrategy` (subtask 6) composes the same classes.
- Delete `FeatureTaskRuntimeRunLoopPhaseRunner`. Its review-specific launch code moves
  into the strategy or into the generic `PhaseRunner`; no review-specific runner stays.
- `FeatureTaskRuntimeReviewDriver` becomes this strategy's collaborator, or folds into
  it. `ApprovingReviewDriverStub` still substitutes the review step in tests.
- The skeleton selection maps every review mode it accepts today to `inline`. The
  strategy reads `CodeReviewExecutionMode` only where today's code does: the accounting
  gate and the resume conflict check.
- Delete the review entries of the phase directive table and `reviewExecutionDirective`
  (in `review/core/FeatureTaskRuntimeReviewExecutionDirective.kt`) if a caller census on
  the current tree shows no production caller. Record the census result in the commit message.
- Rename every remaining `last-commit-fix` / "last commit review" name in production
  code to the inline name. Stored bytes do not contain the strategy id.

## Acceptance Criteria

1. `InlineReviewStrategy` (id `inline`) owns the behaviour listed in Scope in the `code_review` slot package, and the shared step code no longer contains it.
2. The review target is a per-call fact. The skeleton's last-commit target composes prompt text byte-identical to the subtask 1 fixture, and a test composes the prompt for `uncommitted` and for a named commit.
3. `FeatureTaskRuntimeRunLoopPhaseRunner` no longer exists, and no review-specific runner class exists.
4. verify_findings and implement_fix are step classes in the `code_review` slot package.
5. A `review_fix` re-entry, a goal review-pass carry-forward, and a capped review pass each behave as before, proved by the existing suites plus one resume-parity test per path where the suite has none.
6. The strategy reads and writes run state only through `PhaseRunState`.
7. Every subtask 1 fixture matches unchanged.

## Non-goals

- The quality gate (subtask 4) and the other slots (subtask 5).
- `DelegatedReviewStrategy` (subtask 6).
- Moving the review steps' output to prose, or changing the goal review reducers or the unaddressed-findings ledger (follow-up bundle).
- Changing review prompts for the last-commit target, caps, or stored bytes.

## Dependency notes

- Depends on subtask 2. Independent of subtask 4.
- Use the learnings delivery that is already on the tree.

## Validation strategy

The regressions to catch:

- a `review_fix` re-entry or goal carry-forward lost in the move
- verify_findings launched without the read-only idle policy
- implement_fix skipping its coverage relaunch
- the last-commit prompt changing when the target became a parameter

What covers them: the existing run-loop and goal-runner review suites over real
SQLite, the fixture comparison, the target prompt tests, and the resume-parity tests
above.

Run `cd runtime-kotlin && ./gradlew check`, plus the engine, core, and infra-sqlite
suites. Run `bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_4_skeleton-definitions-and-quality-gate.md`.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_3_inline-review-strategy.md
