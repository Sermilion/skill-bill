# Issue 341: Validate evidence integrity

## Intended Outcome

Prevent a decomposed goal from recording a subtask as complete when the
validation strategy required at that subtask boundary is red or was never
executed. The validate phase must leave durable, inspectable command evidence,
and completion must be derived from that evidence rather than an agent's prose
claim.

## Acceptance Criteria

1. The validate settlement records every validation command used by the
   runtime-owned gate, including the command identity and integer exit code.
2. A subtask can become complete only when the required validation command
   result is durably recorded with exit code `0`; prose, an untrusted output
   field, or a missing command result cannot satisfy the gate.
3. The gate requires only the command identity and exit code needed for the
   pass/fail decision. Command text, signal text, provider metadata, and
   additional evidence fields remain opaque strings or values without
   allowlists, regular-expression formats, or unrelated required fields.
4. A nonzero required result enters the existing repair path instead of
   immediately blocking the subtask. The runtime asks the agent to address the
   remaining findings, reruns the required validation command, and repeats
   until all required results exit `0`.
5. A repeated unchanged finding after its repair opportunity, missing or
   unusable required evidence, or an exhausted bounded repair budget leaves the
   subtask incomplete and surfaces an actionable blocker. Malformed optional
   metadata does not block settlement.
6. Goal status reports the recorded validation command and exit code for each
   completed subtask and identifies completed records whose validation evidence
   is absent or invalid.
7. A regression test reproduces a red validation command at the subtask
   boundary and proves that the manifest cannot report that subtask as
   complete.
8. Validation evidence remains contract-versioned, persists through the
   existing phase-settlement store, and follows the established loud-fail
   behavior only for missing or unusable required evidence.
9. Focused contract, runtime, persistence, and goal-status tests pass, and the
   dominant-stack quality check reports no new findings on touched modules.
10. The feature-spec manifest and executable subtask spec remain schema-valid
   and acceptance-criteria extractable by the goal runtime.

## Constraints

- Use the existing runtime-owned phase-settlement and goal-status seams; do not
  create a second validation ledger.
- Keep command execution and evidence collection in the existing validation
  gate path.
- Preserve the current platform-pack command declarations and validation
  routing.
- Treat the minimal pass/fail evidence as a runtime contract, not an
  agent-authored assertion.
- Do not make provider-specific signal formats, command grammars, metadata
  fields, or arbitrary string values schema-required.
- Reuse the existing bounded repair and retry machinery; do not introduce an
  unbounded validation loop.
- Prefer boundary regression tests over broad refactoring.

## Non-Goals

- Changing which validation command a platform pack declares.
- Replacing review, audit, build, or planning evidence contracts.
- Re-running validation automatically after a finalized goal.
- Repairing unrelated historical manifests outside the affected status or
  resume paths.

## Affected Areas

- `../../../runtime-kotlin/runtime-contracts` validation evidence and phase-settlement
  contracts.
- `../../../runtime-kotlin/runtime-domain` validation evidence models and wire keys.
- `../../../runtime-kotlin/runtime-engine` validation settlement, completion, resume, and
  subtask-boundary invariants.
- `../../../runtime-kotlin/runtime-infra-sqlite` settlement persistence and round trips.
- `../../../runtime-kotlin/runtime-cli` goal status validation projections and
  diagnostics.
- Focused tests for contract, runtime, persistence, and status behavior.

## Validation Strategy

- Run focused validation-evidence and phase-settlement tests covering passing,
  red, missing, minimally shaped, provider-extended, and multiple-command
  results.
- Run runtime repair, completion, and resume tests proving an initial red
  result re-enters repair, a later pass completes, and a repeated unchanged
  result blocks without marking the subtask complete.
- Run persistence and goal-status tests proving command identity and exit code
  survive storage and are reported after completion.
- Run the dominant-stack quality check for all touched modules.

## Delivery Plan

1. Trace the existing validation evidence and completion paths to locate the
   loophole that permits a green manifest after a red required suite.
2. Repair the shared settlement and completion boundary, preserving the
   existing contract and status seams.
3. Add regression coverage for red, missing, malformed, successful, persisted,
   resumed, and status-projected validation outcomes.
