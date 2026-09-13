# Subtask 1: Validate evidence integrity

## Scope

Trace the runtime-owned validate gate from command execution through phase
settlement, persistence, subtask completion, resume, and goal status. Close the
path that can mark a subtask complete after the required validation suite was
red or did not leave durable command evidence. Keep one authoritative
validation-evidence projection and make the completion boundary consume it.

## Acceptance Criteria

1. A completed validate settlement contains schema-valid results for every
   validation command used by the gate, with the command identity and integer
   exit code preserved.
2. The required validation command must be present with exit code `0` before
   the runtime advances the subtask to complete.
3. Red or missing required evidence produces a typed blocked or rejected
   repair signal and cannot be bypassed by agent prose or an untrusted success
   field. An initial red result must not immediately block the subtask.
4. The required evidence shape stays minimal: command identity and exit code
   are the only gate-required values. Command strings, signal strings,
   provider metadata, and additional fields are opaque and do not require
   provider-specific schemas or allowlists.
5. After a red result, the runtime re-enters the existing repair path, gives
   the agent the remaining findings, reruns validation, and completes only
   after the required result exits `0`.
6. If the same finding remains after its repair opportunity, or the bounded
   repair budget is exhausted, the runtime blocks the subtask with durable
   evidence and does not mark the manifest complete.
7. A persisted red settlement cannot be accepted during resume, and a
   completed manifest cannot be produced from that settlement.
8. Goal status displays the command and exit code for valid completed evidence
   and reports an integrity problem when completed evidence is absent or
   invalid.
9. Tests reproduce the issue's red-suite boundary, cover successful and
   multiple-command evidence, and cover missing, malformed, persistence,
   resume, provider-extended, and status cases. Optional malformed metadata
   does not block a valid settlement. Tests also prove red-then-pass repair and
   repeated-unchanged-finding blocking.
10. Existing valid records remain readable under the current versioned contract
   rules, while missing or unusable required evidence fails loudly and remains
   incomplete.
11. The touched modules pass the dominant-stack quality check.

## Non-Goals

- No changes to platform-pack validation command declarations or routing.
- No replacement of review, audit, build, or planning evidence contracts.
- No automatic re-execution for already finalized subtasks.
- No cleanup of unrelated historical manifests.
- No provider-specific schema or allowlist for command and signal strings.
- No unbounded retry loop; repeated unchanged findings and exhausted repair
  budget remain blockers.

## Dependency Notes

This subtask is self-contained and has no predecessor. Contract validation,
runtime enforcement, persistence, status projection, and regression coverage
must land together because a partial change would leave the completion loophole
open.

## Validation Strategy

- Run focused contract and phase-settlement tests for valid, red, missing,
  minimally shaped, provider-extended, and multiple-command evidence.
- Run runtime repair, completion, and resume tests for red-then-pass and
  repeated-unchanged-finding outcomes at the subtask boundary.
- Run persistence round-trip and goal-status projection tests.
- Run the dominant-stack quality check for all touched modules.

## Next Path

After this subtask is reviewed and validated, finalize its commit and advance
the goal to its terminal state.
