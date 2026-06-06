# SKILL-70 Subtask 2 — Standalone Code-Review Parallel Review

Created: 2026-06-06
Status: pending
Issue key: SKILL-70
Subtask id: 2

## Scope

Extend the standalone code-review skill entry points (`bill-code-review` and
all stack-specific variants: `bill-kotlin-code-review`, `bill-kmp-code-review`,
and PHP equivalents where present) to accept a `parallel_review:<agent_id>`
modifier. When the modifier is present the skill runs two independent review
passes — the default lane (invoking agent) and an alternative lane (the
requested agent) — against the same diff, then merges findings with per-finding
provenance before presenting the output.

This subtask does not touch the feature-task runtime. It is purely a
skill-content and skill-routing change that mirrors the user experience
introduced in subtask 1 for the standalone review entry point.

## Problem

After subtask 1, a user who runs `bill-feature SKILL-70 parallel_review:codex`
gets two independent review lanes inside the feature-task workflow. The same user
cannot get two independent review lanes when they invoke code review standalone
(`/bill-code-review`, `/bill-kotlin-code-review`, etc.) — those skills ignore the
modifier entirely and run one review pass on the invoking agent.

## Goals

1. Parse `parallel_review:<agent_id>` from skill args in `bill-code-review` and
   each stack-specific code-review skill entry point.
2. Validate the agent id against the same supported-agent list used by the
   feature-task surface (subtask 1). Reject blank, unknown, or duplicate agent
   ids before launching any review pass.
3. Run the default lane (invoking agent) and the alternative lane (alternative
   agent via Agent tool delegation) independently on the same diff and the same
   selected specialist set. The alternative lane receives the same briefing inputs
   as the default lane and must not receive the default lane's findings.
4. Both lanes run concurrently when the execution context allows parallel Agent
   tool calls; if concurrency is not available, they run sequentially with an
   explicit note in the output.
5. Merge findings from both lanes into one output. Each finding carries the
   reviewer agent id. Findings that both lanes surface independently are
   coalesced with both agent ids recorded.
6. The merged output uses the same risk-register shape as today. Provenance
   appears as `[agent_id, ...]` beside each finding code, e.g.
   `F-001 [codex, claude] Blocker …`.
7. When no modifier is present, behavior is identical to today (no regression).

## Non-Goals

- No change to the feature-task runtime (subtask 1 owns that).
- No permanent config default for parallel review in platform-pack manifests.
- No more than one alternative lane per invocation in this subtask.
- No new provider-specific launch code. The Agent tool is the existing
  agent-dispatch abstraction available at the skill layer.
- No change to specialist selection logic. Both lanes use the same routed
  specialist set for the same repo state.

## Affected Files

- `skills/bill-code-review/content.md` — add modifier intake, validation, and
  dispatch; route modifier through to the resolved stack-specific skill.
- `platform-packs/kotlin/code-review/bill-kotlin-code-review/content.md` — add
  parallel review execution section: parse forwarded modifier, validate,
  run default specialist fan-out, delegate alternative lane via Agent tool,
  merge and present findings.
- `platform-packs/kmp/code-review/bill-kmp-code-review/content.md` — same
  addition as the Kotlin skill.
- PHP code-review skill entry point if it exists and follows the same content
  pattern.

## Dependency Notes

Depends on subtask 1 for the validated modifier syntax (`parallel_review:<agent_id>`)
and the supported-agent list contract. The implementation may reference the same
agent id validation rules but must not import Kotlin runtime types — skill content
is plain markdown.

## Acceptance Criteria

1. `bill-code-review parallel_review:codex` routes the modifier through to the
   resolved stack-specific skill.
2. `bill-kotlin-code-review parallel_review:codex` runs two independent review
   passes (default lane invoking agent + codex alternative lane), each receiving
   the same diff and specialist briefing.
3. Findings from both lanes appear in one merged output with `[agent_id]`
   provenance per finding.
4. Blank, unknown, or duplicate agent ids are rejected before any review pass
   launches, with an error message listing supported agents.
5. Invoking either skill without the modifier produces output identical to the
   current behavior.
6. The same modifier support is present in `bill-kmp-code-review`.
7. No specialist selection logic changes. Both lanes use the same selected
   specialists.
8. Architecture tests or grep-based checks confirm no concrete provider
   dependency is added to skill content files.
9. Maintainer validation passes: `skill-bill validate`, `npx --yes agnix --strict .`,
   `scripts/validate_agent_configs`.

## Validation Strategy

- Manual invocation test: `bill-kotlin-code-review parallel_review:codex` on a
  real diff — confirm two lanes appear in output and provenance markers are
  present.
- Negative test: `bill-code-review parallel_review:unknown_agent` — confirm
  rejection before any review runs.
- No-modifier regression: `bill-code-review` without modifier on the same diff —
  confirm output matches baseline.
- Skill content snapshot or render test confirming modifier intake prose is
  present in rendered skill output.
- `skill-bill validate` and `npx agnix --strict .` as closing gate.

## Next Path

Run `bill-feature-task` on this spec on branch
`feat/SKILL-70-ad-hoc-parallel-review-agent`.
