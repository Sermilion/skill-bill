# SKILL-380 Subtask 3 - Workflow profile contract and durable selection

Parent spec: [spec.md](spec.md)
Issue key: SKILL-380

## Scope

Resolves investigation F-006 and the loud-failure half of F-003.

It turns the in-memory `ResolvedWorkflowProfile` from subtask 1 into governed,
configurable, durable run state.

**Contract.**
- Add `orchestration/contracts/workflow-profile-schema.yaml` (Draft 2020-12):
  - `contract_version` is a const.
  - An optional `slots` object has one optional property per `PhaseSlot` wire value.
    Its keys are closed: an unknown slot key is invalid.
  - Each value is a non-blank strategy-id string. Strategy ids stay open vocabulary,
    because the engine registry owns them.
- Add the matching Kotlin pieces: a `WORKFLOW_PROFILE_CONTRACT_VERSION` constant, a
  schema-paths entry staged through the existing governed-resources convention, a
  parity test, and `InvalidWorkflowProfileSchemaError`.
- Declare the wire keys once in a `WorkflowProfilePayloadKeys` object in
  runtime-contracts. Infra host and the engine both read it.

**Selection.**
- `.skill-bill/config.yaml` accepts an optional `workflow_profile` object that
  conforms to the schema. `FileSystemRepoLocalConfig` validates it and raises
  `InvalidWorkflowProfileSchemaError` naming the offending path. `RepoLocalConfig`
  carries it as a typed domain value.
- Resolution at preparation, highest precedence first:
  1. per-run quality-gate override: the goal-runner stamp,
     `--quality-gate-selection`, or `SKILL_BILL_QUALITY_GATE_SELECTION`
  2. the repo `workflow_profile`
  3. each slot's declared default strategy
- For goal children, the goal-runner stamp remains authoritative for `quality_gate`.
  Every other slot follows the repo profile.
- Every resolved `(slot, id)` pair is checked against the registry. An unknown id
  blocks preparation with `UnknownPhaseStrategyError`, naming the slot, the id, and
  the ids registered for that slot. No phase launches.
- `FeatureTaskRuntimeQualityGateSelection.fromWire` stops mapping unknown values to
  VALIDATE. The CLI option becomes a closed choice. An unknown flag or env value
  exits as a usage error naming the accepted values. A legacy continuation row with no
  selection keeps today's healed-to-VALIDATE path with its field-adoption record.
- Delete the silent defaults, or turn each into a recorded degradation:
  `orLegacyValidate()` and `?: VALIDATE` readers in `FeatureTaskRuntimeStatusService`
  and the run-loop transitions. Goal-runner repair readers stay (non-goal) unless they
  read the frozen profile.

**Durability.**
- Freeze the resolved map into run invariants in `freezeRunInvariants` under a
  `workflow_profile` key. Move every run-invariants wire key, the existing six and
  the new one, into one `FeatureTaskRuntimeRunInvariantsPayloadKeys` object. The field
  is additive and optional, following the SKILL-183 `launched_model` precedent.
  `FEATURE_TASK_RUNTIME_RUN_INVARIANTS_CONTRACT_VERSION` stays `0.1`. The decoder
  rejects any other version, so a bump would hard-block every run that is in flight at
  upgrade.
- Legacy invariants without the field take this path, in order:
  1. adopt the map resolved from today's defaults plus the goal-continuation
     selection
  2. emit a field-adoption record, following the existing goal-continuation
     adoption pattern
  3. persist the map
- Resume always runs the frozen map:
  - A per-run override that names a different strategy for a slot blocks with a
    "pinned to" reason, like `code_review_mode`.
  - A repo profile that changed since the run started does not change the run. It
    emits one record naming the slots whose configured strategy differs.
- Strategies are read from the frozen map for the run. `ResolvedWorkflowProfile` is
  built from it on resume.

**Telemetry.**
- `skillbill_feature_task_runtime_finished` gains `phase_strategies`: an object from
  slot wire value to strategy id, covering the slots the run executed.
- Follow the `resolved_agent_ids` path: an engine aggregation from the frozen map, a
  `feature_task_runtime_sessions` column added by migration, a key in
  `LifecycleTelemetryPayloadKeys`, and `docs/review-telemetry.md`.
- Runs finished before this change emit no field.

**Docs.** `AGENTS.md` and ARCHITECTURE.md describe:

- the `workflow_profile` config
- precedence
- resume pinning
- the telemetry field

## Acceptance Criteria

1. `workflow-profile-schema.yaml`, `WORKFLOW_PROFILE_CONTRACT_VERSION`, its parity test, `InvalidWorkflowProfileSchemaError`, and `WorkflowProfilePayloadKeys` exist. A config with an unknown slot key, a blank strategy id, or a wrong contract version is rejected with that error naming the path.
2. A repo `workflow_profile` that selects `agent-validate` for `quality_gate` makes a standalone run validate. With no profile, a standalone run validates, and a non-final goal child builds regardless of the repo profile.
3. A profile naming an unregistered strategy blocks preparation with `UnknownPhaseStrategyError` before any phase launches, and the error names the registered alternatives for that slot.
4. `--quality-gate-selection biuld` and `SKILL_BILL_QUALITY_GATE_SELECTION=biuld` each exit as a usage error naming `build` and `validate`, and no production code maps an unknown selection to VALIDATE.
5. Run invariants carry the frozen `workflow_profile` as an optional field under contract version `0.1`. Every run-invariants key is declared in `FeatureTaskRuntimeRunInvariantsPayloadKeys`, and invariants written before this change still decode.
6. Resuming with an override for a pinned slot blocks with a "pinned to" reason. Resuming after a repo profile change runs the frozen strategies and emits one record listing the changed slots. A legacy run without a frozen profile resumes with the adopted map and an adoption record.
7. `skillbill_feature_task_runtime_finished` carries `phase_strategies` for a new run, and the column migration is append-only.
8. Under the default profile, stored bytes and telemetry match the subtask 1 fixtures except the `workflow_profile` field and `phase_strategies`.
9. `AGENTS.md` and ARCHITECTURE.md describe the config key, precedence, resume pinning, and telemetry field.

## Non-goals

- A YAML schema for the whole run-invariants artifact.
- Goal observability and goal progress event changes.
- Per-run overrides for slots other than `quality_gate`, and profile selection through platform packs.
- Per-phase-record strategy ids.
- New strategies.

## Dependency notes

- Depends on subtask 2: the profile covers every slot's strategy ids. It does not wait for another issue.
- Read and write the frozen field through a typed run-invariants accessor when that accessor exists. Otherwise read and write it through the artifact API that exists now, and keep the bytes this subtask defines.
- Put profile keys in runtime-contracts when infra host and the engine both read them. Follow a stricter placement rule when one is already in force.
- Emit the usage error on stderr when the CLI already has a stderr channel. Otherwise emit it on the diagnostic channel the CLI uses now, and do not wait for another bundle to add stderr.

## Validation strategy

The regressions to catch:

- a typo silently running the wrong gate
- a resumed run switching strategy mid-run
- a legacy run failing to resume
- a goal child losing its build/validate stamp to the repo profile

Contract tests:

- parity
- schema rejections
- one real config parse per rejection

Engine and CLI tests:

- a preparation block for an unknown strategy
- precedence
- resume pin and change record
- legacy adoption over a pre-change fixture database
- CLI usage errors

A telemetry payload test with a golden. The migration runs over a pre-change database.

Run `cd runtime-kotlin && ./gradlew check`, plus the engine, CLI, core,
infra-host, infra-contracts, and infra-sqlite suites. Run
`bill-unit-test-value-check` on changed tests.

## Next path

Continue to `spec_subtask_4_specialist-review-strategy.md`.

## Spec Path

.feature-specs/SKILL-380-phase-slot-strategies/spec_subtask_3_workflow-profile-contract.md
