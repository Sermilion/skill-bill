# SKILL-379 subtask 2 - Triage promotes rejections to learnings

## Scope

- Extend `TriageResult` and the `triage_findings` MCP response with
  `learning_candidates`. The response returns one candidate for each recorded
  `fix_rejected` or `false_positive` decision with a non-blank note. Each
  candidate carries `review_run_id`, `finding_id`, a suggested title from the
  finding, the note as the suggested rule, and suggested scope `repo` with the
  scope key from subtask 1's repo scope key port.
- Add a strict-schema MCP tool (for example `add_learning`) that wraps
  `LearningService.add`. It applies the same source validation as
  `skill-bill learnings add`: the source finding exists, and its latest
  outcome is a rejection. Register it in `McpToolRegistry` and the tool-schema
  parity coverage, and declare its wire keys in `LearningPayloadKeys`.
- Update the triage guidance in `orchestration/telemetry-contract/PLAYBOOK.md`
  ("Triage Ownership"). When `triage_findings` returns candidates, the parent
  review shows them and asks the user once, as a batch, which to promote and
  with which scope. It calls the add-learning tool only for confirmed
  candidates. Update the `skill-bill learnings` section of
  `docs/review-telemetry.md` to name the MCP path. Run `./install.sh`.

## Acceptance Criteria

1. Triage with a `false_positive` decision and a non-blank note returns one
   learning candidate for that finding, with the note as the suggested rule.
2. Triage with an accepted or fix-applied decision, or a rejection with a blank
   note, returns no learning candidate for that finding.
3. Triage never writes a `learnings` row by itself.
4. The add-learning MCP tool creates an active learning from a rejected finding.
   A later `resolve_learnings` call for the same repo and skill returns it.
5. The add-learning MCP tool rejects a finding whose latest outcome is not a
   rejection, and rejects unknown top-level arguments, with typed errors.
6. `orchestration/telemetry-contract/PLAYBOOK.md` tells the parent review to
   offer candidates to the user and to create learnings only on confirmation.

## Non-goals

- Automatic promotion or a promotion heuristic without a note.
- Changes to `bill-pr-review-fix`.
- Backfilling existing rejections.

## Dependency notes

Depends on subtask 1 for the repo scope key port. Without subtask 1, a
promoted learning would never reach a review.

## Validation Strategy

- One application test: triage with mixed decisions returns candidates only
  for noted rejections. Then add one candidate through the MCP tool and resolve
  it back. This catches both a missing candidate and a broken add path.
- MCP schema parity coverage for the new tool.
- Kotlin pack `validation_gate` via `bill-code-check`.
- `./install.sh`.

## Next

Parent complete. Run `bill-pr-description` for the feature branch.
