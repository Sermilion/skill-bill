# SKILL-242: Audit retries from remaining acceptance criteria

## Intended outcome

Add a small runtime-owned retry loop to SKILL-241's stateless audit. Each audit agent returns remaining ACs. Normal completion with an explicit empty list finishes audit; a nonempty result starts a fresh audit agent with that text and all planned ACs. Process failure, interruption or missing output stops the loop.

No exact status words or AC-item schema are required. Each attempt keeps SKILL-241's existing verification and repair behavior.

## Mode

single_spec

One subtask owns result interpretation, sequential retries, audit commit creation and amendment, interruption handling and their tests. These changes form one working execution path.

## Prerequisite and ownership

Finish SKILL-241 before starting SKILL-242. Its completed, validated stateless audit implementation must be integrated into main, the base branch declared by this manifest. A working commit on the SKILL-241 branch alone does not satisfy this prerequisite. Do not stop its active run or use SKILL-242 to take over unfinished cleanup or acceptance work.

SKILL-241 owns removal of durable audit machinery and legacy handoffs, full verification with repair in one session, stateless resume, and status and telemetry cleanup. SKILL-242 owns the additional result handling, runtime retry loop and commit creation or amendment at each completed audit round. Reuse the completed implementation and existing tests. Do not merge SKILL-240's durable cycle as a prerequisite or repeat its removal.

The manifest dependency list describes subtasks within this bundle and remains empty because there is only one. The cross-issue prerequisite is recorded here; the current manifest does not enforce it automatically. Before launching SKILL-242, confirm SKILL-241 completion and that its final changes are present on main. Create fresh SKILL-242 planning and workflow identities, never reuse SKILL-241's child or acceptance records.

This preparation leaves SKILL-241's specifications, frozen planning, implementation and live runtime state untouched.

## Scope

- Audit final-response instructions and process-outcome versus remaining-AC interpretation.
- Sequential fresh audit launches with the last result passed as a temporary hint alongside all planned ACs.
- Runtime-owned creation of the first subtask commit and amendment after later audit rounds, including the HEAD fallback.
- Interruption, resume and review advancement at the changed retry boundary.
- Focused regression coverage and documentation for these changes.

## Behavior

1. Run SKILL-241's full audit and repair in one agent session.
2. Read the process outcome and completed final response. Missing output is not an empty list.
3. After normal completion with a present final result, runtime creates or amends the subtask commit with the current changes. Then finish on an explicit empty list or launch a fresh audit agent with nonempty result text and all planned ACs.
4. Let that agent repair and recheck every criterion. Runtime owns the retry loop; the agent cannot delegate.
5. Stop on process failure or interruption. An explicit resume starts a full audit without saved retry hints.

## Minimal result

Process outcome tells runtime whether the agent finished normally, failed or was interrupted. The final response tells runtime whether a completed audit has remaining ACs. There are no required agent-emitted status words.

An explicit empty list means no unresolved criteria:

```text
[]
```

A nonempty result supplies the next audit attempt's focus:

```text
- AC-002: deletion handling still has no regression test.
- AC-005: recovery still omits newly created files.
```

Bullets are optional. Numbered lists and plain-text AC descriptions are accepted too. Runtime forwards nonempty result text unchanged and lets the next agent interpret it using the complete planned AC list. It does not validate item structure, AC membership, wording or evidence. It does not reject a result because an agent omitted a status word or used a different list format.

Empty-list notation may have ordinary whitespace or Markdown fencing; it is not an exact byte-match token. Runtime distinguishes an explicit empty list from nonempty text without imposing an audit-report schema. It must not find incidental empty brackets inside an otherwise nonempty explanation and declare success.

A normally completed process and an explicit empty final list are both required to advance. Silence, whitespace-only output, a missing final response, cancellation, timeout and abnormal exit stop through ordinary failure or interruption handling. A crash after printing an empty list cannot pass audit. Read the completed final response supplied by the provider adapter, not progress messages or arbitrary fragments of stdout.

An external blocker or unavailable planning input uses the existing workflow interruption path. No new audit status keyword or structured blocker receipt is required. A nonempty response from a normally completed process is treated as unresolved-AC text, not classified by searching for failure or interruption words.

The retry text lives only in memory for the current loop. No retry counter, per-AC progress, durable audit receipt, or provider session is required to reconstruct the audit. Normal workflow attempt bookkeeping may remain. After process interruption, resume discards old audit hints and verifies all ACs again.

## Commit after each audit round

Runtime performs the Git operation after the audit agent has finished and returned a result. This applies to both a round with unresolved ACs and the final round with an empty list. Commit or amend before starting the next audit agent or handing the final target to review. The agent itself does not run git commit or git push.

On the first round, create the subtask commit if runtime knows it has not created one yet. On later rounds, amend the existing subtask commit with the implementation and audit changes. An unchanged round need not create a new SHA. The branch keeps one commit per subtask, not one commit per audit attempt.

If runtime cannot find the current subtask commit for any reason, assume HEAD belongs to the current subtask and amend it. This explicitly overrides the existing requirement to prove ownership before amendment. Do not block or ask for confirmation because identity metadata is absent, stale or unverifiable. Emit a diagnostic when using the fallback. It can amend a commit whose ownership was not established; this is the requested fallback behavior.

Use the existing subtask change inclusion and commit machinery. Ordinary commit identity bookkeeping and existing checkpoint refs remain allowed; do not create an audit-specific receipt, checkpoint validation protocol or saved criterion state. A failed Git operation stops the loop with its error. Interrupted or failed agent processes leave their partial edits available for resume and do not count as completed audit rounds.

Review targets the commit left by the final successful audit round. Existing later finalization may amend it with authorized subsequent changes, and commit_push retains responsibility for finalization and pushing. Do not create a second subtask commit there merely because audit already created the first.

## Acceptance criteria

1. Audit prompts request only the remaining ACs as the final result, with an explicit empty list when none remain. Preserve SKILL-241's full criterion verification, code and test repair within each agent session, and prohibition on nested delegation. No exact success, failure or interruption word is required from the agent.
2. Runtime evaluates process outcome before result content. Only normal process completion with an explicit empty final AC list completes audit. Recognize empty-list notation with ordinary whitespace or Markdown fencing. Nonempty final text is accepted as unresolved-AC input without validating list structure, AC identifiers, membership, wording or evidence. Do not introduce a versioned agent envelope, report schema, schema-correction agent, saved-value comparison or checkpoint proof.
3. A normally completed audit attempt with nonempty result text starts exactly one next audit attempt in a fresh agent session after the previous process has finished. Runtime passes the returned text unchanged plus the complete planned AC list. The next agent repairs and rechecks all criteria, using the returned text only as a focus hint. Retries remain in audit and never route to implement, a separate repair phase or nested subagents.
4. Cancellation, timeout, abnormal exit, missing final output and whitespace-only output stop through existing failure or interruption handling without completing audit or automatically relaunching it. Process failure overrides any earlier printed empty list. Use the provider adapter's completed final response; empty brackets in progress messages or inside a nonempty explanation cannot signal success. Existing external-blocker handling remains available without an audit status keyword.
5. The new retry loop keeps only the latest unresolved-AC text in memory. It adds no durable audit progress, retry grants, provider continuation sessions or per-criterion state. Interruption discards this hint; explicit resume uses SKILL-241's fresh full audit behavior against the current repository and preserves existing edits. Ordinary workflow status and attempt bookkeeping remain allowed.
6. Review advances only after a normally completed audit returns an explicit empty list. Only the next audit agent receives nonempty AC output; other phases receive no new audit findings or repair projection. Preserve SKILL-241's stateless cleanup, historical workflow compatibility, code-and-test inspection responsibility, and existing review, validation, lease and cancellation behavior. Commit timing changes only as required by criterion 8; Git operations remain runtime-owned.
7. Focused boundary regressions exercise nonempty-to-empty retries, unchanged result forwarding, all-criterion input on every attempt, ordinary list and plain-text presentation, empty-list whitespace and fencing, and interruption precedence over output. They prove that retries are sequential fresh audit launches, restart does not depend on saved hints, and no implement launch or downstream audit-result projection appears. Update affected prompts and documentation and run applicable validation; reuse SKILL-241's cleanup coverage rather than reimplementing it.
8. After every normally completed audit round with a present final result, runtime commits the current subtask changes before launching the next audit agent or advancing to review. It creates the first subtask commit when that commit has not yet been created, then amends the same commit on later rounds. If it cannot locate the current subtask commit for any reason, it assumes HEAD is that commit and amends HEAD; missing or unverifiable ownership is not a blocking condition. Record use of this fallback through existing diagnostics. Preserve one branch commit per subtask, runtime-owned Git operations and existing finalization and push responsibilities. An actual Git failure stops advancement with its concrete error.

## Constraints and non-goals

- Preserve SKILL-241's stateless audit foundation. Do not reopen its cleanup, migration, status or telemetry work as new scope. A demonstrated baseline defect remains prerequisite work for SKILL-241 rather than a reason to expand this feature silently.
- No exact agent status line, structured audit report, per-criterion validation, approval gate, durable retry memory, audit checkpoint validation protocol or provider-session continuation. Existing runtime commit bookkeeping remains allowed.
- Keep ordinary workflow bookkeeping and process diagnostics. Do not add a new workflow framework, feature flag or arbitrary repair-round cap.
- Full code-and-test inspection and repair remain within each audit agent. Test execution and build proof stay with their existing downstream owners.
- Do not change unrelated review, validation, commit or push responsibilities, or modify live workflow databases for acceptance tests.
- Follow runtime-kotlin/ARCHITECTURE.md, docs/code-principles.md and governed source-generation requirements. Add no code comments or architecture-baseline exceptions.

## Validation strategy

Use production phase orchestration with a controlled launcher and disposable workflow storage. Keep the tests focused on the new behavior:

- Return nonempty AC text, then an explicit empty list. Assert two sequential fresh audit launches, verbatim forwarding of the first result, all planned ACs in both requests, and review advancement only after the second result. Include a regression in a previously satisfied criterion so the retry cannot narrow its check to the hint.
- Use a temporary Git repository to verify that the first completed audit round creates the subtask commit, the next round amends it with additional repairs, and the final successful round leaves one branch commit containing all changes before review. Remove or invalidate commit lookup metadata and verify that runtime amends HEAD and emits a fallback diagnostic. An actual Git failure must prevent the next launch or review advancement. Interrupted attempts preserve edits for resume without claiming audit success.
- Exercise bullets, numbered lists and plain-text AC descriptions without validating their structure. Exercise empty-list whitespace and Markdown fencing. A nonempty explanation containing brackets must remain nonempty.
- Exercise cancellation, timeout, abnormal exit after printing an empty list, missing final output, whitespace-only output and empty brackets in progress messages. None may advance review or automatically relaunch from an unproven result.
- Interrupt between attempts and resume with no in-memory hint. Assert a fresh full audit with existing repository edits preserved and no audit-specific persistence required. Reuse SKILL-241's stateless fixtures and coverage.

Run the relevant runtime suites and manifest-routed quality checks during implementation validation. Preserve the goal build session's build-only command contract. If governed skill source or rendering changes, run ./install.sh outside any phase that forbids installation. Record commands and actual results. This spec preparation is not implementation acceptance evidence.

## Delivery and next path

The single executable subtask is [Retry audit from remaining AC output](spec_subtask_1_ac-list-retries.md).

After SKILL-241 completes and its final changes are integrated into main, run `skill-bill goal SKILL-242`.
