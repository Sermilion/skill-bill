# WE-4789: Validation gate execution evidence

## Intended Outcome

Make a passed feature-task validation gate auditable from its durable record.
The validation artifact must describe the work the gate actually performed,
including executed work units and check identities, instead of recording an
empty or misleading evidence projection.

## Acceptance Criteria

1. A validation gate that executes checks persists evidence identifying the
   executed checks and the actual executed work-unit count.
2. `executed_work_units` either counts the work units actually executed by the
   gate or is replaced by a field whose name and value semantics are truthful
   in the durable record; skipped, cached, and forced-full behavior remains
   distinguishable.
3. `checks` contains the names or stable identities of checks that actually
   ran, and the projection records an explicit, auditable representation when
   no checks ran.
4. Evidence remains correct across cache-eligible failures, forced-full
   retries, passing validation, failing validation, and zero-work executions.
5. The persisted validation artifact survives database round-trip and is
   exposed consistently through workflow inspection and status projections.
6. Regression tests reproduce a gate that runs Gradle compilation and tests
   while the durable record currently reports zero work units and no checks,
   and prove that the repaired record exposes the executed work.
7. The change does not alter the validation verdict rules addressed by issue
   341; it improves evidence for both passing and failing verdicts.
8. Focused contract, persistence, runtime, and status tests pass, and the
   dominant-stack quality check reports no new findings on touched modules.
9. The feature-spec manifest and executable subtask spec remain schema-valid
   and acceptance-criteria extractable by the goal runtime.

## Constraints

- Preserve the existing runtime-owned validation gate and workflow artifact
  as the authoritative evidence path.
- Derive evidence from the gate's actual command/task execution results, not
  from agent prose or inferred filesystem timestamps.
- Keep cache mode, retry behavior, and validation verdict semantics intact
  unless a change is required to expose truthful evidence.
- Prefer stable check identities and typed fields over provider-specific output
  parsing.
- Add boundary regression tests at the gate settlement and persistence seams.

## Non-Goals

- Reopening or redesigning issue 341's validation verdict enforcement.
- Changing which validation commands a platform pack declares.
- Treating build-directory mtimes as runtime evidence.
- Adding a second validation ledger outside the existing workflow artifact.
- Reworking unrelated quality-check session telemetry.

## Affected Areas

- `../../../runtime-kotlin/runtime-contracts` validation gate progress schema
  and payload keys.
- `../../../runtime-kotlin/runtime-domain` validation evidence models and
  projections.
- `../../../runtime-kotlin/runtime-engine` gate execution and settlement.
- `../../../runtime-kotlin/runtime-infra-sqlite` workflow artifact persistence.
- `../../../runtime-kotlin/runtime-cli` workflow/status inspection.
- Focused contract, runtime, persistence, and regression tests.

## Validation Strategy

- Trace the production gate from task execution through artifact settlement to
  identify where executed work and check names are discarded.
- Add tests for executed checks, multiple checks, cache-eligible failure,
  forced-full retry, passing runs, failing runs, and zero-work runs.
- Verify persisted artifacts and status projections retain the evidence after
  a database round-trip.
- Run the dominant-stack quality check for all touched modules.

## Delivery Plan

1. Repair the validation evidence projection so it reflects actual gate work.
2. Preserve the evidence through persistence and status/reporting paths.
3. Add regression coverage for the reported false-zero evidence case and the
   relevant cache and retry boundaries.
