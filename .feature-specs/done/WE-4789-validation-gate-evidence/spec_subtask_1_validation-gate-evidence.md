# Subtask 1: Persist truthful validation gate evidence

## Scope

Trace validation gate command execution, work-unit accounting, check-name
collection, settlement, persistence, and status projection. Repair the
discarded evidence so a durable passed or failed validation record explains
what the gate actually ran, including the reported Gradle compile and test
case. Keep the existing validation verdict behavior unchanged.

## Acceptance Criteria

1. When the validation gate executes one or more checks, its settled artifact
   contains the stable identities of those executed checks and a work-unit
   count whose semantics match the work actually executed.
2. A cache-eligible failed run followed by a forced-full passing run preserves
   the evidence for both attempts, including cache mode, outcome, executed
   work, and check identities.
3. A zero-work run is represented truthfully and is distinguishable from a run
   that executed checks but lost their evidence.
4. The validation artifact remains schema-valid and round-trips through the
   workflow persistence store without losing executed checks or work counts.
5. Workflow inspection and status projections expose the same evidence as the
   settled artifact, including the names of checks that ran.
6. Regression tests reproduce the current false-zero case where Gradle writes
   compile and test outputs while the record reports no work and no checks,
   and prove the repaired projection records the executed work.
7. Tests cover passing, failing, cache-eligible, forced-full, multiple-check,
   and zero-work gate outcomes without changing issue 341 verdict behavior.
8. The touched modules pass the dominant-stack quality check.

## Non-Goals

- No change to the required validation suite or platform-pack command
  declarations.
- No redesign of issue 341's verdict or completion enforcement.
- No filesystem-timestamp-based evidence collection.
- No separate validation ledger or unrelated quality-check telemetry changes.

## Dependency Notes

This subtask is self-contained and has no predecessor. Execution, contract,
persistence, status, and regression coverage must land together because
partial evidence repair would leave the durable audit record misleading.

## Validation Strategy

- Run focused validation contract and gate settlement tests for executed,
  failing, forced-full, multiple-check, and zero-work outcomes.
- Run persistence round-trip tests for the validation artifact and workflow
  status projection tests for exposed check identities.
- Run the regression test for the reported Gradle compile and test evidence
  gap.
- Run the dominant-stack quality check for all touched modules.

## Next Path

After this subtask is reviewed and validated, finalize its commit and advance
the goal to its terminal state.
