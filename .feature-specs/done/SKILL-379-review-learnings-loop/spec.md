# SKILL-379 - Review learnings loop

## Mode

decomposed

## Intended outcome

Code-review learnings work end to end. When a user rejects a review finding
with a reason, they can promote that rejection to a learning. Every later
`skill-bill code-review` run resolves the active learnings for its repo and
routed review skill. It gives their text to each review worker, records which
learnings it applied, and reports them in the review summary.

Today the loop is broken at both ends:

- The runtime review driver never resolves learnings.
  `ParallelReviewPreparationCompiler.reviewFactPorts`
  (`runtime-application/.../review/parallel/planning/ParallelReviewPreparationCompiler.kt:199-205`)
  wires `ReviewLearningsPort` to a stub that returns `emptyList()`. No driver
  code calls `LearningService` or `LearningRepository.saveSessionLearnings`.
  The packet's `learnings_references` is always `[]`.
- Even a populated packet would not reach workers. `ReviewLearningsReference`
  holds only `learningId`, `source`, and `digest`, with no rule text. The
  assignment and launch envelopes project `matched_rules` but not learnings.
- Nothing creates learnings. The only path is a manual
  `skill-bill learnings add --from-run --from-finding`. `triage_findings`
  records `fix_rejected` and `false_positive` outcomes but never offers to
  promote them, and there is no MCP tool for adding a learning.
- The prose describes a flow that no longer exists.
  `../../../orchestration/skill-classes/code-review-shell.yaml` ("Local Review
  Learnings") and `orchestration/review-orchestrator/PLAYBOOK.md` ("Shared
  Learnings Context") say the driver calls the `resolve_learnings` MCP tool.

Local evidence from `~/.skill-bill/review-metrics.db` (2026-08-24 to
2026-09-23): 71 review runs, 8 rejected findings (4 `false_positive`, 4
`fix_rejected`), 0 `learnings` rows, 0 `session_learnings` rows.

## Acceptance Criteria

1. A `skill-bill code-review` run resolves active learnings for the global
   scope, the run's repo scope key, and its routed review skill. Resolution
   uses the existing `LearningRepository.resolve` precedence (skill, repo,
   global).
2. Each resolved learning's id, scope, title, rule text, and digest reach
   every launched review worker through the assignment and launch envelopes.
   The review-context contract is bumped for this change.
3. Each driver run persists one `session_learnings` row for its review
   session id, including when zero learnings applied.
4. The driver's review summary reports `Applied learnings: none` or the
   applied learning references. That value matches what
   `ReviewFinishedPayloads` records for the run.
5. When triage records a `fix_rejected` or `false_positive` decision with a
   non-blank note, the triage result returns a learning candidate for that
   finding. No learning is created without explicit user confirmation.
6. A strict-schema MCP tool creates a learning from a rejected finding. It
   applies the same source validation as `skill-bill learnings add`.
7. Shell, playbook, and telemetry-contract prose describes what the runtime
   actually does for learnings resolution and promotion.

## Constraints

- Learnings are explicit context, not suppression rules. Workers must not
  drop evidence-based correctness, security, or contract findings because
  a learning exists.
- Workers still must not resolve learnings themselves. `learnings_resolution`
  stays in `ReviewPacketConsumerContract.FORBIDDEN_REDISCOVERY`.
- Contract changes follow the runtime contract path: schema YAML first, then
  the Kotlin `REVIEW_CONTEXT_CONTRACT_VERSION` bump, the parity test, and a
  typed loud-fail at parse seams. Legacy packet records follow the existing
  quarantine behavior.
- Every fallback emits a record (`../../../docs/observability-policy.md`). This covers
  a missing `origin` remote when deriving the repo scope key, and a failed
  learnings read.
- Wire keys are declared once in `LearningPayloadKeys` or the owning
  `*PayloadKeys` object and never inlined at governed seams.
- Worker learnings text is bounded. The per-learning cap matches
  `REVIEW_RULE_EXCERPT_MAX_CHARS`, and the whole learnings block counts
  against the existing `ReviewContextBudgetPolicy`.
- Follow `../../../runtime-kotlin/ARCHITECTURE.md` Design Principles and
  `docs/code-principles.md`. Kotlin gets no `//` comments.

## Non-goals

- Promoting learnings automatically without user confirmation.
- Changing `bill-pr-review-fix`, which records durable guidance in
  `../../../agent/history.md` through `bill-boundary-history`.
- Resolving the `ReviewGuidancePort` and `ReviewBuildTestFactsPort` stubs in
  the same compiler. They are separate gaps.
- Changing learnings storage, schema, or scope precedence.
- Backfilling learnings from the 8 existing rejections.

## Subtasks

1. Driver resolves learnings and delivers them to workers
   (`spec_subtask_1_driver-resolves-learnings.md`). Covers criteria 1-4 and
   the resolution half of 7.
2. Triage promotes rejections to learnings
   (`spec_subtask_2_triage-learning-promotion.md`). Covers criteria 5-6 and
   the promotion half of 7.

Split condition: subtask 1 ships alone as a bug fix; it makes hand-added
learnings work today. Subtask 2 adds a new MCP tool and a triage response
field, and it reuses subtask 1's repo scope key derivation.

## Next

```bash
skill-bill goal SKILL-379
```
