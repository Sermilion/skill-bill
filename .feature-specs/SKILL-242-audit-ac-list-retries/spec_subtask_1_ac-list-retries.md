# SKILL-242 subtask 1: Retry audit from remaining AC output

## Scope

Add remaining-AC result interpretation and sequential fresh-agent retries to the completed SKILL-241 stateless audit. Preserve verification and repair within each session, full restart on resume, and the absence of audit-specific persistence. The parent's Minimal result section defines result handling, and Commit after each audit round defines runtime-owned commit creation, amendment and the HEAD fallback.

## Dependency notes

Start only after SKILL-241 completes and its validated final changes are integrated into main. This is a cross-issue prerequisite documented in the parent, not an internal subtask dependency enforced by the manifest. Do not adopt its active workflow, frozen plan, partial validation or unfinished cleanup. Create fresh SKILL-242 execution identities.

## Implementation approach

1. Locate the completed SKILL-241 launch, result, resume and review-advancement paths. Reuse its stateless behavior and tests.
2. Change audit final-response instructions and result handling to distinguish normal process completion with an explicit empty list from nonempty AC text. Accept list presentation without status words or AC-item validation.
3. Add the sequential runtime loop. Pass the prior nonempty response unchanged with the entire planned AC list to one new audit agent. Keep only the latest response in memory and keep the workflow in audit until completion.
4. Before the next audit launch or review advancement, create the first subtask commit or amend the existing one through runtime-owned Git operations. When commit lookup fails for any reason, assume HEAD is the subtask commit and amend it with a diagnostic. Reuse existing commit machinery and keep one branch commit per subtask.
5. Preserve process failure and cancellation handling. Missing output cannot pass or cause an automatic retry. Explicit resume starts a fresh full audit without the previous hint.
6. Add focused boundary tests, update affected prompts and documentation, and run applicable validation. Do not reopen SKILL-241's machinery-removal work.

## Acceptance criteria

1. Audit prompts request only the remaining ACs as the final result, with an explicit empty list when none remain. Preserve SKILL-241's full criterion verification, code and test repair within each agent session, and prohibition on nested delegation. No exact success, failure or interruption word is required from the agent.
2. Runtime evaluates process outcome before result content. Only normal process completion with an explicit empty final AC list completes audit. Recognize empty-list notation with ordinary whitespace or Markdown fencing. Nonempty final text is accepted as unresolved-AC input without validating list structure, AC identifiers, membership, wording or evidence. Do not introduce a versioned agent envelope, report schema, schema-correction agent, saved-value comparison or checkpoint proof.
3. A normally completed audit attempt with nonempty result text starts exactly one next audit attempt in a fresh agent session after the previous process has finished. Runtime passes the returned text unchanged plus the complete planned AC list. The next agent repairs and rechecks all criteria, using the returned text only as a focus hint. Retries remain in audit and never route to implement, a separate repair phase or nested subagents.
4. Cancellation, timeout, abnormal exit, missing final output and whitespace-only output stop through existing failure or interruption handling without completing audit or automatically relaunching it. Process failure overrides any earlier printed empty list. Use the provider adapter's completed final response; empty brackets in progress messages or inside a nonempty explanation cannot signal success. Existing external-blocker handling remains available without an audit status keyword.
5. The new retry loop keeps only the latest unresolved-AC text in memory. It adds no durable audit progress, retry grants, provider continuation sessions or per-criterion state. Interruption discards this hint; explicit resume uses SKILL-241's fresh full audit behavior against the current repository and preserves existing edits. Ordinary workflow status and attempt bookkeeping remain allowed.
6. Review advances only after a normally completed audit returns an explicit empty list. Only the next audit agent receives nonempty AC output; other phases receive no new audit findings or repair projection. Preserve SKILL-241's stateless cleanup, historical workflow compatibility, code-and-test inspection responsibility, and existing review, validation, lease and cancellation behavior. Commit timing changes only as required by criterion 8; Git operations remain runtime-owned.
7. Focused boundary regressions exercise nonempty-to-empty retries, unchanged result forwarding, all-criterion input on every attempt, ordinary list and plain-text presentation, empty-list whitespace and fencing, and interruption precedence over output. They prove that retries are sequential fresh audit launches, restart does not depend on saved hints, and no implement launch or downstream audit-result projection appears. Update affected prompts and documentation and run applicable validation; reuse SKILL-241's cleanup coverage rather than reimplementing it.
8. After every normally completed audit round with a present final result, runtime commits the current subtask changes before launching the next audit agent or advancing to review. It creates the first subtask commit when that commit has not yet been created, then amends the same commit on later rounds. If it cannot locate the current subtask commit for any reason, it assumes HEAD is that commit and amends HEAD; missing or unverifiable ownership is not a blocking condition. Record use of this fallback through existing diagnostics. Preserve one branch commit per subtask, runtime-owned Git operations and existing finalization and push responsibilities. An actual Git failure stops advancement with its concrete error.

## Non-goals

No durable audit cycle, status-word protocol, audit report schema, progress database, grant or audit checkpoint validation protocol. Existing subtask commit bookkeeping remains allowed. No migration or telemetry cleanup already owned by SKILL-241. No nested audit delegation, audit-to-implement repair, changes to other gate ownership, or live workflow mutation for validation.

## Validation strategy

Use production phase orchestration with a controlled launcher and disposable workflow storage. Keep the tests focused on the new behavior:

- Return nonempty AC text, then an explicit empty list. Assert two sequential fresh audit launches, verbatim forwarding of the first result, all planned ACs in both requests, and review advancement only after the second result. Include a regression in a previously satisfied criterion so the retry cannot narrow its check to the hint.
- Use a temporary Git repository to verify that the first completed audit round creates the subtask commit, the next round amends it with additional repairs, and the final successful round leaves one branch commit containing all changes before review. Remove or invalidate commit lookup metadata and verify that runtime amends HEAD and emits a fallback diagnostic. An actual Git failure must prevent the next launch or review advancement. Interrupted attempts preserve edits for resume without claiming audit success.
- Exercise bullets, numbered lists and plain-text AC descriptions without validating their structure. Exercise empty-list whitespace and Markdown fencing. A nonempty explanation containing brackets must remain nonempty.
- Exercise cancellation, timeout, abnormal exit after printing an empty list, missing final output, whitespace-only output and empty brackets in progress messages. None may advance review or automatically relaunch from an unproven result.
- Interrupt between attempts and resume with no in-memory hint. Assert a fresh full audit with existing repository edits preserved and no audit-specific persistence required. Reuse SKILL-241's stateless fixtures and coverage.

Run the relevant runtime suites and manifest-routed quality checks during implementation validation. Preserve the goal build session's build-only command contract. If governed skill source or rendering changes, run ./install.sh outside any phase that forbids installation. Record commands and actual results. This spec preparation is not implementation acceptance evidence.

## Next path

After the prerequisite is met, run this single subtask through SKILL-242's normal workflow. The runtime owns subsequent review, validation, history and commit phases. No follow-up audit-remediation subtask is planned.
