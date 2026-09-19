# SKILL-363 - mandatory-simplification-phase

## Mode

single_spec

## Intended outcome

Replace the standalone `bill-over-engineering-review` skill with a mandatory
runtime `simplify` phase in the feature-task workflow. The phase runs after
implementation and before completeness audit, inspects only the current
subtask's diff and owned paths, applies high-confidence simplifications, and
leaves the existing audit phase responsible for proving that the final tree
still satisfies every acceptance criterion.

## Scope

The change covers the feature-task phase graph and phase IDs, phase handoff and
output contracts, prompt composition, persistence and resume behavior,
phase-specific tests, and governed removal of
`skills/bill-over-engineering-review/content.md` plus its README catalog row.
The simplify phase is a mutating single-agent session. It may remove dead
feature-local code, collapse unnecessary wrappers or one-implementation
abstractions, replace hand-rolled standard-library behavior, and shorten
equivalent local code when the evidence is clear.

## Acceptance Criteria

1. The feature-task workflow contains a `simplify` phase between `implement`
   and `audit`; `review` remains unreachable until the existing satisfied-audit
   gate has passed.
2. Simplify receives the current subtask's scoped diff and owned paths as its
   boundary and does not perform a whole-repository sweep or modify files
   outside that boundary.
3. Simplify is explicitly forbidden from changing governed contracts, typed
   errors, loud-fail seams, parity tests, validator-backed rules, security
   measures, accessibility requirements, or behavior required by the spec.
4. Simplify is a mutating, single-session phase that does not run builds or
   tests, emits a bounded change receipt, retries invalid output through the
   normal phase-output contract, and can resume from durable phase state.
5. The audit phase runs on the post-simplification tree, rechecks the complete
   acceptance-criteria list, and remains the only phase that grants audit
   satisfaction before review.
6. Phase graph, handoff, persistence, output, resume, and routing tests cover
   forward ordering, scope boundaries, invalid-output handling, interruption
   recovery, simplify edits followed by audit, and preservation of the
   audit-before-review gate.
7. `bill-over-engineering-review` is no longer a listed source or catalog
   entry, and the source install plus agent-config validation pass without
   generating an over-engineering skill entry.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md`, `docs/code-principles.md`,
  `docs/observability-policy.md`, and AGENTS.md.
- Keep the phase scoped to the feature-task subtask. It must not become a
  replacement for platform code review, correctness review, security review,
  performance review, or test-value review.
- Preserve the current audit-first cost boundary relative to code review:
  simplification completes before audit, and review still requires a satisfied
  audit.
- Preserve typed contracts, loud-fail behavior, contract versions, wire-key
  ownership, checkpoint identity, and durable resume semantics.
- Authored Kotlin under the governed runtime directories must contain no
  non-KDoc comments.
- Do not run builds or tests from the simplify agent session; validation owns
  execution evidence.

## Non-goals

- No whole-repository complexity audit in the feature-task runtime.
- No automatic simplification of code outside the current subtask's diff or
  owned paths.
- No new standalone skill, platform-pack review area, shortcut-debt ledger, or
  savings metric.
- No merge of the existing completeness audit and code-review phases.
- No changes to acceptance-criteria meaning, review specialist routing, quality
  gates, commit-before-review, or PR behavior beyond the new phase boundary.

## Validation strategy

Name the regression before each test: simplify skipped or placed after audit;
review entered without a satisfied audit; simplify edited an out-of-scope path;
the prompt allowed a governed contract or whole-repository sweep; malformed
simplify output advanced the workflow; resume lost the simplify state; audit
accepted the pre-simplification tree; or installation regenerated the removed
skill. Run focused phase graph, prompt, handoff, persistence, output-contract,
resume, and audit-routing tests, then `validate-agent-configs`, the
pack-declared quality gate, and the source installation flow.

## Next path

Run `skill-bill goal SKILL-363` when implementation is intended. The prepared
manifest is the goal runner's input.
