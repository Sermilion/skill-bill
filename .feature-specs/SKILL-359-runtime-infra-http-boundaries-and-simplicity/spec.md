# SKILL-359 - runtime-infra-http-boundaries-and-simplicity

## Mode

decomposed

## Intended outcome

Reduce `runtime-infra-http` to what its three ports need: one constructor per adapter that takes the resolved transport, environment, clock, and diagnostics port instead of re-resolving them; typed results built directly from decoded responses instead of patched through raw maps; one transport construction path and one `HttpClient`; typed failures for peer and configuration faults; a record for every fallback and swallowed cleanup; one staging cleanup owner that deletes only what it created; the adapter tests beside the adapters; the dead forwarder, unused constants, unused test edges, and stale guard strings deleted; and the telemetry proxy wire vocabulary declared once in `TelemetryProxyPayloadKeys`, referenced from contracts, this module, and the CLI, and scanned by the wire-vocabulary guard, while keeping the module graph, the ports, the bytes on the wire, the delivery-outcome classification, the deadlines, interruption propagation, the open capability and stats documents, and every passing test's observable assertion unchanged.

## Scope

The investigation covers all 6 production files and 2 test files in runtime-infra-http, the ports and contracts it implements or consumes, the composition-root bindings and `runtime-core` tests that construct it, the application consumers that call its ports, the CLI mappers that re-spell its vocabulary, the guards and baselines that scan it, and the proxy worker it talks to. See [investigation.md](investigation.md) for ten findings, the principles assessment, rejected changes, public engineering references, the test baseline, and limits.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-001, F-003, F-004, F-005, F-006, F-007, F-008, F-009, F-010 | Adapters take resolved dependencies, typed results built once, one transport and one client, typed failures, recorded fallbacks, one cleanup owner, tests beside the adapters, dead code deleted | 1 |
| F-002 | Telemetry proxy vocabulary owned once in `runtime-contracts`, referenced from contracts, infra, and CLI, and registered as a wire-vocabulary guard seam | 2 |

Two subtasks. The first reshapes the module and its composition, and moves its tests; it changes no byte on the wire. The second changes where the wire keys are declared across three modules and adds a guard seam; it is specified against the key set the first subtask leaves behind (F-003 removes the echoed request keys from the client), so it runs after the first lands. Each subtask is one commit on the feature branch.

Prepared in local mode on 2026-09-17. SKILL-359 follows SKILL-358, the highest existing local spec key; the user authorised the next available key. Baseline HEAD is `d8a103a1ebf88662fc7f7db7b6efce4bfd24a905` with a clean tracked tree; the sorted production-file digest is `d1b3b7e8ac54513df6af55f4902d658d64cb63178a93379fba10c4bed56dade5`; the module suite passes with 4 tests. SKILL-354 later moves this module under `runtime-infra/` unchanged; nothing here depends on that move or blocks it. This bundle prepares work only; all subtasks start pending.

## Acceptance Criteria

1. `HttpTelemetryClient` has one constructor taking a `RemoteTransportPort`, the resolved environment or stats token, a `Clock`, and `RuntimeDiagnostics`; `withProcessDefaults`, the requester fallback, and both secondary constructors are deleted; `RemoteTransportPort` is provided once from `skillbill.di` after bootstrap resolution and `UpdateCheckService` receives the port instead of `TransportContext`; `runtime-infra-http-ambient-environment-baseline.txt` is empty and no other module's baseline grows.
2. `fetchProxyCapabilities` and `fetchRemoteStats` build `TelemetryProxyCapabilities` and `TelemetryRemoteStatsResult` directly from the decoded response and the request; no `putIfAbsent`, `knownKeys` set, `preserveResponseCapabilities` flag, or map-returning default factory remains; `additionalFields` and `metrics` hold the response minus the owned keys, and a `capabilities` entry present in a stats response with a null value still appears in `metrics`; one request function replaces `requestJson` and `requestJsonGet`.
3. `JdkHttpRemoteTransport` has one named default instance and one factory for a non-default deadline pair with no `Duration`-equality branch; `RuntimeBootstrapBindings` resolves an absent requester through that factory; `HttpInstallerScriptFetchAdapter` receives `RemoteTransportPort` and declares no `HttpClient`; the redirect policy is recorded in `runtime-kotlin/agent/decisions.md`; the 2xx range is declared once in the module and the unused `HTTP_OK_MIN`/`HTTP_OK_MAX` in `runtime-domain` `TelemetryConstants.kt` are deleted.
4. A typed proxy-failure family under `SkillBillRuntimeException` in `skillbill.error` carries status code, seam, and bounded detail; non-2xx responses, invalid or non-object JSON, and a blank relay URL raise it; `InvalidTelemetryTransportOutcomeError` extends the same base; no `IllegalArgumentException` or `IllegalStateException` is thrown or extended in the module; the moved tests assert the typed classes.
5. Both adapters inject `RuntimeDiagnostics`; the 404/405 default-capabilities substitution, every swallowed staging cleanup failure, and a `cleanup` refusal each emit one record naming seam, expected value, and used value; a 2xx with a blank or non-object body raises the typed invalid-response error.
6. `HttpInstallerScriptFetchAdapter.fetch` has one staging teardown owner through which every non-`Ready` exit passes; `cleanup` deletes only a directory carrying the adapter's staging prefix that contains the given script and records a refusal otherwise; the temp-then-`ATOMIC_MOVE` promotion is unchanged.
7. The five `HttpTelemetryClient` tests move from `runtime-core` `TelemetryRuntimeTest` into this module with a fake transport; `telemetryProxyBatchPayload` and the result mappers are `internal`; `TelemetryReleaseAttributionTest` asserts on `TelemetryOutboxRecord.skillBillVersion`; the installer adapter has tests for non-2xx and `IOException` cleanup, interruption cleanup and rethrow, atomic promotion, and `cleanup` ownership; `runtime-mcp` and `runtime-cli` no longer declare `testImplementation(project(":runtime-infra-http"))` and `RuntimeModuleCatalog` agrees.
8. `TelemetryHttpRuntime` and its package are deleted; the two `TelemetryHttpRuntime` strings and the stale `TelemetryRemoteStatsRuntime` string are removed from `RuntimeLayerBoundaryArchitectureTest`.
9. User-agent values and header names are declared once in the module and referenced from both adapters; the message-or-class-name idiom has one owner reachable from the adapter and, if a shared home exists, from `UpdateCheckService` and `TelemetryOutboxDrain`; no byte of any request or response changes.
10. `TelemetryProxyPayloadKeys` declares every capabilities, stats, batch-event, and property key the client, `TelemetryProxyContracts.kt`, and `TelemetryCliResultMappers.kt` spell today, plus `stats_auth_required` and the `remote_proxy` source value; those three files contain no inline proxy wire literal; the telemetry proxy seam is registered in `WireVocabularyGovernedSeamInventory` with those path markers and `WireVocabularyArchitectureTest` passes; `CustomFieldMap` and `TelemetryOpenDocument` stay open.
11. `./gradlew check` on runtime-kotlin passes, including `runtime-core` architecture guards, `runtime-cli`, and `runtime-mcp`; `ARCHITECTURE.md` lines on the transport, the installer fetch, and the telemetry adapter describe the new shape and name the tests that prove the fetch guarantees.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles, docs/code-principles.md, docs/observability-policy.md, and AGENTS.md. Keep the eleven Gradle modules, the composition root, the `implementation` edges pinned in `RuntimeModuleCatalog`, and the infra → ports, domain, contracts direction.
- Respect recorded decisions: reached absences as nullable ports resolved at the call site with a named fallback (2026-09-06), shrink-only infra ambient baselines (2026-09-03), guard ceilings move by decision not by baseline (2026-09-04), the telemetry user agent left as SKILL-163 recorded it. A change to the fallback-site list or the redirect policy lands as a decision entry.
- No new module, port, DI graph, HTTP library, retry layer, schema YAML for the proxy responses, or `HttpMethod` enum on `RemoteTransportPort`.
- Preserve request bytes and response handling for well-formed input: method, URL, headers, body JSON, delivery-outcome classification, deadlines, interruption propagation, the open capability and stats documents, and every passing test's observable assertion. Only malformed input and configuration defects change their failure identity.
- Kotlin under `runtime-kotlin` carries no `//` comments and no non-KDoc block comments; files stay under 1,200 lines and 40 functions without count splits; no new suppression, baseline row, or exemption.
- Deletion follows a fresh reference census plus compilation and the full runtime-kotlin test suite; the recorded census is evidence, not authority.
- Re-read the owning documents and current source hashes before each subtask. Subtask 2 runs after subtask 1 lands and re-censuses the remaining literals.

## Non-goals

- Changing `TelemetryClient`, `RemoteTransportPort`, or `InstallerScriptFetchPort` signatures, `TelemetrySettings`, the domain validators (`validateIngestCapabilities`, `validateRemoteStatsRequest`, `parseRemoteStatsWindow`), or `TelemetryOutboxDrain` delivery semantics.
- Moving the module under `runtime-infra/`; SKILL-354 owns that.
- A schema for the proxy capabilities or stats responses, or closing those documents.
- Changing the proxy worker under `docs/cloudflare-telemetry-proxy/`.
- Rewriting `UpdateCheckService` beyond receiving the port and, optionally, sharing the message idiom.
- Renaming the `Jdk*` or `Http*` prefixes.
- Certification against private Reddit, Microsoft, or Meta standards.

## Validation strategy

Name the regression before each test: a stats call that reads a stale default because the component provided an unresolved context, a null `capabilities` entry lost from `metrics`, a 404 fallback that no longer records, a peer's invalid JSON surfacing as an argument error, a failed download that leaves `install.sh.partial` on disk, an interruption that leaves a staging directory, `cleanup` deleting a caller's directory, a wire key spelled two ways after subtask 2. Keep the moved client tests green with their current assertions on well-formed fixtures; add the installer adapter tests through the injected transport; keep `JdkHttpRemoteTransportDeadlineTest`; run `runtime-core` architecture guards including the ambient-environment recorder and `WireVocabularyArchitectureTest`, `runtime-cli` and `runtime-mcp` suites, and `./gradlew check` on runtime-kotlin. Run the pack-declared quality gate and bill-unit-test-value-check for changed tests. The preparation baseline (4 module tests passing) is evidence about `main`, not a future review receipt.

## Next path

Run `skill-bill goal SKILL-359` when implementation is intended. The prepared manifest is the goal runner's input.
