# SKILL-241: Stateless acceptance-criteria audit

## Intended outcome

Audit uses a simple runtime-owned retry loop. Each fresh audit agent takes the planned acceptance criteria, checks implementation and test coverage, repairs gaps in its own session, and returns the remaining ACs. A normally completed attempt with an explicit empty list finishes audit; a nonempty list starts another audit agent. Process failure, interruption or missing output stops the loop; resume checks every criterion from the beginning.

The current repository is the evidence. The agent needs no saved audit progress, repair protocol, or continuation session. Repairs already present after interruption remain in the repository and are checked like any other code.

## Mode

single_spec

One subtask replaces the audit flow, removes its obsolete dependencies, and includes regression coverage and documentation. Splitting removal, launch behavior, and completion into separate commits would leave an incomplete execution path.

## Context and relationship to SKILL-240

This requirement supersedes SKILL-240's durable audit-and-repair cycle design. The SKILL-240 investigation found rejection paths for exact assessment values, checkpoint scope and identity, provider continuation, and missing cycle authority. Those mechanisms are outside the requested audit behavior.

Preparation is based on local main at eb6c2ad5d. This main branch still has the older audit-gap handoff to implementation and does not contain the cycle implementation on feat/SKILL-240-audit-repair-cycle. Implement against the actual starting tree. Remove the older handoff on main; remove cycle integrations too if they are present when implementation begins. Do not merge or cherry-pick SKILL-240 merely to remove its machinery.

SKILL-241 has no dependency on completion of SKILL-240. Preparing this spec does not reset, abandon, resume, or alter that run or its records.

## Behavior

1. Runtime launches a fresh audit agent with all planned ACs and the current repository. After an attempt reports unresolved ACs, it also supplies that result verbatim.
2. The agent checks every AC for implementation and meaningful test cases, repairs gaps itself, and checks the result. It cannot delegate or start another agent.
3. The agent returns its remaining ACs. It returns an explicit empty list if it checked every criterion and none remain unresolved.
4. Runtime first checks whether the process finished normally. If it did, an explicit empty list finishes audit and nonempty result text launches the next audit attempt. Each attempt receives all planned ACs to check for regressions.
5. Process failure, interruption or missing final output stops the loop. An explicit resume starts a fresh full audit without saved audit progress.

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

## Scope

- Audit prompts, process-outcome and remaining-AC result handling, sequential fresh-agent retries, phase completion, workflow transitions, and consumer projections.
- Removal of audit-gap handoffs, progress persistence, retry grants, and audit-specific checkpoint or session requirements where present.
- Existing status and telemetry readers that would otherwise depend on removed audit state.
- Resume compatibility, regression tests, and governed documentation for these changes.

## Acceptance criteria

1. Audit receives the complete acceptance-criteria list established during planning, with its existing identifiers and wording. Every attempt checks all criteria against the current repository. On an automatic retry it also receives the previous attempt's unresolved-AC text as a focus hint, never as a replacement for the complete list or proof that other criteria remain satisfied.
2. For every criterion, the audit agent locates the implementation that provides the required behavior and test cases whose assertions verify it. Missing implementation, missing tests, and tests that do not exercise the criterion are gaps. The agent writes or corrects code and tests in its own session and checks the result before returning. One meaningful test may cover several criteria; names, empty tests, and tautological assertions are not coverage.
3. Each audit attempt runs in one fresh agent session. The agent cannot spawn nested agents or delegate repair. It returns the remaining ACs, or an explicit empty list when none remain. Runtime alone owns the sequential retry loop: a normally completed attempt with nonempty result text starts a new audit agent with that text and the full planned AC list. No audit failure routes back to implement or to a separate repair agent.
4. Process outcome and audit content are separate signals. A normally completed process with an explicit empty AC list finishes audit; a normally completed process with nonempty remaining-AC text requests the next audit attempt. Accept ordinary bullets, numbered lists, plain text and empty-list notation with normal whitespace or Markdown presentation. Require no exact status words, versioned JSON envelope, AC-item schema, identifier membership check, evidence fields, exact saved value, or checkpoint proof. Forward nonempty result text unchanged for the next agent to interpret.
5. Cancellation, timeout, abnormal process exit, absent final output and whitespace-only output stop through ordinary interruption or failure handling with a concrete reason. They never count as an empty AC list and never trigger an automatic retry from an unproven result. Process outcome takes precedence over text emitted before a crash. Read the provider's completed final response, not incidental list syntax in progress messages. Missing planning input or an external blocker uses the existing workflow interruption path without requiring an agent-emitted status word.
6. An interrupted audit resumes with a fresh session and the full planned AC list, starting verification from the beginning. Existing repository edits remain for inspection and reconciliation. No saved result, partial criterion check, provider continuation session, repair receipt, or checkpoint may skip verification or settle the resumed audit. The previous failed attempt body is an in-memory hint only during an uninterrupted retry loop; losing it does not prevent resume.
7. Audit uses no audit-specific database or file persistence for progress or retries. Remove cycle revisions, launch bindings, gap memory, repair plans, pause grants, audit checkpoints, audit-stage acknowledgements, and per-stage telemetry from its execution path. Ordinary workflow status, attempt attribution, terminal outcome and process diagnostics remain allowed, but are not audit continuation inputs. No approval gate is inserted between audit attempts.
8. Review remains gated by a normally completed audit with an explicit empty remaining-AC list. Only the next audit attempt receives nonempty result text. Review, validation, implementation and other downstream agents receive no audit findings, repair instructions, or audit result projection. Ordinary resume of old incomplete audit or audit-gap runs uses the new loop without requiring legacy audit state or a reset; already completed workflows retain their historical status. Delete obsolete active audit machinery and retain only compatibility needed to open existing databases.
9. Audit inspects implemented behavior and written test cases; it does not require tests or builds to execute during audit. Their execution remains with existing downstream owners. Preserve review behavior, validation routing, worker cancellation and lease safety, and runtime-owned commits and pushes. Update source documentation and regenerate affected skill output when implementation changes governed source or rendering.
10. Boundary regressions prove an explicit empty list after normal completion advances, nonempty AC text launches exactly one next audit attempt with unchanged text and all planned ACs, and interruption or missing output stops without advancement or automatic relaunch. They cover ordinary list and plain-text presentation without status words, interrupted processes that printed an empty list, repair within each session, regression detection across attempts, full restart after interruption, no audit-state access, and no audit-to-implement or downstream findings handoff. No test requires a structured audit report validator.

## Constraints and non-goals

- Use the existing workflow and phase-output adapters. Keep ordinary workflow bookkeeping separate from audit content. Stateless audit does not remove the workflow database, planning authority, worker leases, or downstream review checkpoint identity.
- Use process outcome and empty versus nonempty final AC output; require no agent status line. Do not replace the deleted machinery with a structured audit receipt, approval gate, per-criterion database table, sidecar file, event log, or fingerprint protocol.
- Repair scope follows the planned criteria and normal repository permissions. An implementation dirty-file inventory is not an audit repair allowlist that excludes newly needed code or tests.
- Do not weaken criteria or tests to obtain completion. Shared tests may cover several criteria when their assertions prove each behavior.
- Do not change other phases' review, build, validation, commit, or push responsibilities. Audit result content is not an input to those agents; normal repository changes remain visible to them.
- Do not introduce a feature flag, arbitrary repair-round cap, new workflow framework, or separate audit service.
- Follow runtime-kotlin/ARCHITECTURE.md and docs/code-principles.md. Add no code comments and do not expand architecture baselines to accept new violations.
- Do not modify the user's live workflow database as part of implementation or validation. Exercise migration and resume with disposable copies or fixtures. Preparation writes specifications only.

## Validation strategy

Use a small number of boundary tests, each tied to a realistic regression. A launcher fixture should receive a planning list with one implemented criterion missing test coverage and another missing implementation. It must repair both in one launch, leave the expected files, and allow the ordinary workflow to advance without audit-stage calls.

Interrupt an audit after it edits a file, reopen the workflow, and verify that the next request contains the full criterion list and starts a new session. It must inspect the existing edit and recheck a previously satisfied criterion whose implementation or test was changed before resume. No prior result may bypass the check.

Exercise the runtime with audit-specific repositories unavailable or configured to fail on access. Seed legacy pause, gap, or dangling cycle data where supported and prove ordinary resume and status still work. Record persistence operations at the boundary to distinguish allowed phase bookkeeping from forbidden audit progress writes.

Exercise a nonempty AC result followed by an explicit empty list and assert sequential fresh audit launches, unchanged forwarding of the nonempty result, and the full planned AC list in each request. Include free-form AC descriptions without a rigid item format. Neither attempt may launch implement or nested repair agents. Review receives no audit result projection.

Exercise interruption, cancellation, timeout, abnormal exit after a printed empty list, missing final output and whitespace-only output. Each stops without review advancement or automatic retry. Verify that ordinary bullets, numbered lists and plain text pass through unchanged without status words, and that whitespace or Markdown around an empty list does not cause a formatting rejection. Use actual phase orchestration with controlled launcher outcomes, not tests that only match prompt sentences.

Run relevant runtime tests and the dominant pack's required checks during implementation validation. The goal build session retains its existing build-only command contract. If governed skill source or rendering changes, run ./install.sh outside any phase that forbids installation. Record commands actually run and their results without treating this preparation as implementation evidence.

## Delivery and next path

The single executable subtask is [Replace audit with stateless attempts and minimal results](spec_subtask_1_stateless-audit.md).

Run `skill-bill goal SKILL-241` when implementation should begin.
