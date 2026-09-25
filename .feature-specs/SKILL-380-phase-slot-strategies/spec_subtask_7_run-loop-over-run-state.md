# SKILL-380 Subtask 7 - Run loop runs over PhaseRunState

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Every run is a skeleton definition driven by the one run loop (subtask 4). Subtask 8
runs short definitions with no workflow row, so the loop must not touch durable storage
directly. This subtask moves every run-loop read and write behind `PhaseRunState` and
keeps the durable implementation byte-identical. It adds no in-memory implementation.

**Census first.** List every read and write the run loop and its collaborators under
`skillbill.engine.featuretask` make outside strategies. Expected on 2026-09-25, recount
at start:

- workflow snapshot and step status (workflow state store)
- phase records and ledger entries
- run invariants (`freezeRunInvariants` and their decode on resume)
- runtime session rows (`feature_task_runtime_sessions`)
- checkpoints (review, remediation, audit-to-review git checkpoint refs)
- resume reconstruction (`FeatureTaskRuntimeRunStateReconstruction` and state
  validation)
- goal continuation reads
- activity stamps, liveness, and leases
- lifecycle telemetry (`skillbill_feature_task_runtime_finished` inputs)

**Port.** Each item becomes an operation on `PhaseRunState`, or on a cohesive sub-port
it exposes (for example records, checkpoints, telemetry) if one interface would break
the package or function ceilings. The loop receives one `PhaseRunState` per run. The
durable implementation delegates to today's stores, writers, and git operations
unchanged.

**Entry.** The loop's entry takes the skeleton definition, the `PhaseRunState`, and the
per-call facts. `FeatureTaskRuntimeRunRequest` stays the durable entry request: it
builds the durable state and resolves the definition (subtask 4), then calls the loop.
No other code path drives the loop.

**Guard.** Add a rule to the engine boundary suite: no class in the run-loop packages
depends on the durable stores, writers, or git checkpoint operations directly; only the
durable `PhaseRunState` implementation does. It asserts it read at least one file and
fails on a synthetic violation.

## Acceptance Criteria

1. Every run-loop read and write in the census goes through `PhaseRunState`, and the durable implementation is the only production class under `skillbill.engine.featuretask` that depends on the durable stores, writers, and checkpoint git operations.
2. The loop's entry takes a skeleton definition, a `PhaseRunState`, and per-call facts, and `FeatureTaskRuntimeRunRequest` is only the durable entry that builds them.
3. The new guard fails on a synthetic run-loop file that imports a durable store and passes on the tree.
4. Every subtask 1 fixture matches its latest baseline unchanged, and resume from durable records reconstructs the same state as before (existing reconstruction suites plus one parity test per census item that has none).

## Non-goals

- The in-memory `PhaseRunState`, short definitions, and the `phase` CLI (subtask 8).
- Changing any stored byte, checkpoint ref name, lease rule, or telemetry payload.
- Goal-runner code outside the run request it already builds.

## Dependency notes

- Depends on subtask 5 (strategies own their state through `PhaseRunState`) and subtask
  4 (definitions and traversal). Subtask 6 lands first by id order.

## Validation strategy

Catch: a write that bypasses the port; a resume that reconstructs different state; a
checkpoint ref written under a different name. Cover with the guard, the fixture
comparison, and the reconstruction and resume suites over real SQLite. Run
`cd runtime-kotlin && ./gradlew check`, plus the engine, core, and infra-sqlite suites.
Run `bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_8_phase-review-and-validation.md`.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_7_run-loop-over-run-state.md
