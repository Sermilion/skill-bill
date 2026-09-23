# SKILL-379 subtask 1 - Driver resolves learnings and delivers them to workers

## Scope

- Replace the empty `ReviewLearningsPort` stub in
  `ParallelReviewPreparationCompiler.reviewFactPorts` with a port backed by
  `LearningRepository.resolve`. Inject it into `ParallelCodeReviewRunnerPlanning`
  through the existing DI graph; do not construct it inside the compiler.
- Derive the repo scope key from the reviewed repo's `origin` remote,
  normalized to `owner/name`, matching the documented
  `--scope-key oila-gmbh/skill-bill` form. Put this behind a port so
  subtask 2 can reuse it. With no `origin` remote, resolve global and skill
  scopes only and emit a diagnostic record.
- Resolve the skill scope key from the run's routed review skill, the same
  value stored as `review_runs.routed_skill_canonical`, for example
  `bill-kotlin-code-review`.
- Add scope, title, and bounded rule text to `ReviewLearningsReference`.
  Project the learnings into the assignment and launch envelopes next to
  `matched_rules`. Update `../../../orchestration/contracts/review-context-schema.yaml`
  (`learnings_reference`, assignment, launch), bump
  `REVIEW_CONTEXT_CONTRACT_VERSION` from 2.3, and update the parity test.
- Tell workers how to use learnings in the consumer contract: as explicit
  context, never as suppression of evidence-based correctness, security, or
  contract findings. Keep `learnings_resolution` forbidden.
- Persist `session_learnings` for the run's review session id through
  `LearningRepository.saveSessionLearnings`, including an empty set.
- Make the driver's summary and the `ReviewFinishedPayloads` applied-learnings
  value come from the resolved set.
- Rewrite `code-review-shell.yaml` "Local Review Learnings" and
  `review-orchestrator/PLAYBOOK.md` "Shared Learnings Context" to describe
  driver-owned resolution, not agent-called `resolve_learnings`. Run
  `./install.sh` so the installed `bill-code-review` SKILL.md matches.

## Acceptance Criteria

1. With an active repo-scoped learning whose scope key matches the reviewed
   repo's `origin` slug, the prepared packet's `learnings_references` contains
   it, and every launch envelope carries its title and rule text.
2. A disabled learning, and a learning scoped to a different repo or skill,
   never appear in the packet or any envelope.
3. A driver run with zero matching learnings still writes one
   `session_learnings` row for its review session id, and its summary reports
   `Applied learnings: none`.
4. A driver run with applied learnings reports their references in the summary,
   and the finished-review telemetry records the same references.
5. A repo with no `origin` remote still resolves global and skill learnings,
   and the run emits a diagnostic record for the missing repo scope.
6. The review-context schema, the Kotlin contract version, and the parity test
   agree on the new version. A packet record carrying the previous version
   follows the existing quarantine path.
7. A rule text longer than `REVIEW_RULE_EXCERPT_MAX_CHARS` fails loudly at the
   reference boundary with a typed error; it is never silently truncated.
8. `code-review-shell.yaml` and `review-orchestrator/PLAYBOOK.md` no longer
   state that the driver calls the `resolve_learnings` MCP tool.

## Non-goals

- Triage promotion and the add-learning MCP tool (subtask 2).
- Filling the guidance-rule and build/test-fact stubs.
- Changing learnings storage or precedence.

## Dependency notes

None. This is the first subtask.

## Validation Strategy

- One application-level test through `ParallelCodeReviewRunnerPlanning` with
  a seeded SQLite store. It covers criteria 1-3 and would catch a regression
  back to the empty stub.
- A contract parity test for the version bump.
- Kotlin pack `validation_gate` via `bill-code-check`.
- `./install.sh`, then confirm the installed
  `~/.claude/skills/bill-code-review/SKILL.md` matches the new prose.
- Manual check: add a learning for this repo, run
  `skill-bill code-review last`, and confirm the summary line and the
  `session_learnings` row.

## Next

Subtask 2: `spec_subtask_2_triage-learning-promotion.md`.
