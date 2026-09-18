# SKILL-359 Subtask 2 - Own the telemetry proxy vocabulary once and register the seam

Parent spec: [.feature-specs/SKILL-359-runtime-infra-http-boundaries-and-simplicity/spec.md](./spec.md)
Issue key: SKILL-359

## Scope

Resolve F-002 in [investigation.md](investigation.md).

Own `TelemetryProxyPayloadKeys.kt` and `TelemetryProxyContracts.kt` in `runtime-contracts` `skillbill.contracts.telemetry`, `HttpTelemetryClient.kt`, `HttpTelemetryResultMappers.kt` (or their subtask-1 successors), and `TelemetryProxyPayloadMappers.kt` in this module, `TelemetryCliResultMappers.kt` in `runtime-cli`, `WireVocabularyGovernedSeamInventory` and `WireVocabularyArchitectureSupport` in `runtime-core` tests, the typed capabilities factory subtask 1 introduced, and the AGENTS.md "Wire and payload keys" paragraph if it lists owning objects by name.

Re-census the proxy wire literals after subtask 1. Declare every remaining capabilities key (`contract_version` through `SharedPayloadKeys`, `source`, `proxy_url`, `capabilities_url`, `supports_ingest`, `supports_stats`, `supported_workflows`, `stats_auth_required`), stats key (`workflow`, `date_from`, `date_to`, `group_by`, `stats_url`, `capabilities`), batch key (`batch`, `event`, `distinct_id`, `properties`, `timestamp`), property key (`install_id`, `skill_bill_version`, `$process_person_profile`), and the `remote_proxy` source value once in `TelemetryProxyPayloadKeys`. Reference the constants from the contracts DTOs, the module's request and result code, and the CLI mappers. Register the telemetry proxy seam in the wire-vocabulary inventory with those files as path markers so an inline literal at any of them fails the guard, and prove the guard fires with a synthetic violation in the same way the existing seams are proven.

## Acceptance Criteria

1. `TelemetryProxyPayloadKeys` declares each proxy wire key exactly once; no key it declares is spelled as a literal in `TelemetryProxyContracts.kt`, the module's `src/main`, or `TelemetryCliResultMappers.kt`; `contract_version` continues to come from `SharedPayloadKeys`.
2. `WireVocabularyGovernedSeamInventory` registers a telemetry proxy seam whose path markers cover the three files; `WireVocabularyArchitectureTest` passes on the tree and fails on a synthetic inline `"supports_stats"` access inside a marked path, proven by the inventory's existing self-test pattern.
3. `CustomFieldMap` and `TelemetryOpenDocument` remain open; `stats_auth_required` from the proxy still lands in `additionalFields`; `TelemetryRuntimeTest`'s moved fixtures and the CLI telemetry tests pass with unchanged assertions; every request body and CLI JSON output is byte-identical before and after.
4. No new schema YAML, no closed enum for proxy fields, no change to `TELEMETRY_PROXY_CONTRACT_VERSION`'s value; if the constant moves beside the keys, the decision entry says why and the domain reference is updated.

## Non-goals

No behaviour change, no new keys on the wire, no change to the proxy worker, no change to the telemetry config document keys (`TelemetrySettingsFromStore`, `FileTelemetryConfigStore`), which are a different document owned elsewhere.

## Dependency notes

Depends on: subtask 1. Its typed-result rewrite decides which keys the module still spells, and its typed capabilities factory is where the defaults reference the constants. Rebase on the branch head and re-run the literal census before editing.

## Validation strategy

Name the regression before each test: one key spelled two ways across contracts and CLI, a guard that passes because the seam is unregistered, a proxy field dropped because a document was closed. Run `WireVocabularyArchitectureTest` and the rest of the `runtime-core` guards, the module suite, `runtime-cli` telemetry tests, and `./gradlew check` on runtime-kotlin; run the pack-declared quality gate and bill-unit-test-value-check for changed tests.

## Next path

Goal complete after this subtask settles; the runtime finalises history and decisions entries.

## Spec Path

.feature-specs/SKILL-359-runtime-infra-http-boundaries-and-simplicity/spec_subtask_2_own-the-telemetry-proxy-vocabulary-once-and-register-the-seam.md
