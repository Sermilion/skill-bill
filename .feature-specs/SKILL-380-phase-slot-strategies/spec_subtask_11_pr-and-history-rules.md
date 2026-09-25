# SKILL-380 Subtask 11 - pr-description and boundary-history own their rules

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Two skeleton steps still tell the agent to invoke a listed skill. SKILL-383 deletes
those skills, so the strategies must own the rules first. This is one planned prompt
change for two steps.

**`pr-description` owns the PR template rules.** Port the repo-native template search
and the built-in fallback from `skills/bill-pr-description/content.md` into runtime-owned
prompt fragments the strategy loads:

- search order stays that file's list
- found template: fill it, keep headings and section order, omit checklists
- none found: coded fallback
- multiple templates with no default: usage error naming the paths

Replace the skeleton pr directive's "Invoke bill-pr-description, honor any repo-native
PR template except its checklist, …" with these rules. The title rule
(`[<issue key>] <descriptive title>`) stays. `phase pr` (subtask 9) gets the
same rules because it runs the same strategy.

**`boundary-history` owns the history and decision rules.** Port the authored rules of
`skills/bill-boundary-history/content.md` (65 lines: when to write, when to skip, entry
shape, target file) and `skills/bill-boundary-decisions/content.md` (97 lines: decision
entry shape, when a change is a decision) into runtime-owned prompt fragments the
strategy loads. Keep their substance. Drop ceremony that only makes sense for a listed
skill (shell ceremony, telemetry-contract sidecars, invocation examples). Replace the
write_history directive's "Invoke bill-boundary-history inline and apply its write/skip
rules …" with these rules. The uniform output instruction from subtask 5 stays.

**Telemetry.** `bill-pr-description` emits `pr_description_generated` through its
telemetry contract when the agent invokes it. Once the prompt stops invoking the skill,
the `pr-description` strategy emits that event itself after the step, with the payload
and `skill` label today's event carries, filled from runtime-measured PR facts.

**Fixtures (planned re-baseline).** Re-baseline the pr and write_history step prompt
fixtures in this commit. Each diff contains only the directive change. Keep the
`bill-pr-description` and `bill-boundary-history` telemetry labels.

**Tests that name the skill files.** `ExcludedRootAgentTreeAbsenceTest` and any
authoring test that reads `skills/bill-pr-description/content.md` or
`skills/bill-boundary-*/content.md` keep passing; SKILL-383 updates or removes them.

## Acceptance Criteria

1. Neither the pr nor the write_history prompt tells the agent to invoke a skill. Both compose their rules from runtime-owned fragments.
2. When `.github/pull_request_template.md` (or another search-path template) exists, the PR summary from the skeleton and from `phase:pr` keeps that template's headings and drops its checklist. When none exists, the coded fallback is used. Two templates with no default is a usage error naming both paths.
3. A write_history run over a fixture change the old rules would skip still settles as skipped, and one they would write still writes an entry of the same shape.
4. The pr and write_history prompt fixtures are re-baselined and each diff contains only the directive change. Every other fixture still matches.
5. The skeleton pr step and `skill-bill phase pr` each emit `pr_description_generated` matching the subtask 1 payload fixture.

## Non-goals

- Deleting `skills/bill-pr-description`, `skills/bill-boundary-history`, or `skills/bill-boundary-decisions` (SKILL-383).
- Changing the history or decision file formats, or where they are written.
- New strategies for pull_request or write_history.

## Dependency notes

- Depends on subtask 9 (`phase pr` exists) and subtask 5 (both strategies own
  their directives and the uniform output).

## Validation strategy

Catch: skipping a found template; a lost history skip rule, so history is written for
every change; a decision entry with a different shape; a prompt changing beyond its
directive. Cover with template present/absent/ambiguous fixtures, the two write/skip
fixture runs, and the prompt fixture diffs. Run `cd runtime-kotlin && ./gradlew check`
plus engine, core, CLI. `bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_12_skill-bill-dispatcher.md`.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_11_pr-and-history-rules.md
