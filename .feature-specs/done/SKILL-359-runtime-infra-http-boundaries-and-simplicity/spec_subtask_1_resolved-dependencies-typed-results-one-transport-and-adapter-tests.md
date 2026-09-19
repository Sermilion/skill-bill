# SKILL-359 Subtask 1 - Resolved dependencies, typed results, one transport, and adapter tests

Parent spec: [.feature-specs/SKILL-359-runtime-infra-http-boundaries-and-simplicity/spec.md](spec.md)
Issue key: SKILL-359

## Scope

Resolve F-001, F-003, F-004, F-005, F-006, F-007, F-008, F-009, and F-010 in [investigation.md](investigation.md).

Own all six production files under `runtime-kotlin/runtime-infra-http/src/main/kotlin/skillbill/infrastructure/http/`, the module's two test files, `RuntimeBootstrapBindings`, `RuntimeTelemetryProvides`, `RuntimeInstallerProvides`, and one new `RemoteTransportPort` provider in `runtime-core` `skillbill.di`, `UpdateCheckService` in `runtime-application` (port injection and, optionally, the shared message idiom), `TelemetryHttpRuntime.kt` and its package in `runtime-application`, the typed proxy-failure declarations in `runtime-contracts` `skillbill.error`, the unused `HTTP_OK_MIN`/`HTTP_OK_MAX` in `runtime-domain` `TelemetryConstants.kt`, the `runtime-core` tests `TelemetryRuntimeTest`, `TelemetryReleaseAttributionTest`, `AbsentOptionalPortResolutionTest`, and `RuntimeLayerBoundaryArchitectureTest`, the `testImplementation` edges in `runtime-mcp/build.gradle.kts` and `runtime-cli/build.gradle.kts` with `RuntimeModuleCatalog`, the ambient-environment baseline recorder output, `../../../runtime-kotlin/ARCHITECTURE.md` (telemetry outbox transport paragraph, installer fetch section), and `runtime-kotlin/agent/decisions.md`.

Give `HttpTelemetryClient` one constructor over the resolved transport, environment or stats token, clock, and diagnostics; delete the re-resolution and the secondary constructors. Build both typed results directly from decoded response plus request. Keep one transport class with one named default and one factory. Inject the transport into the installer adapter and delete its client. Raise typed failures at the peer and configuration seams. Record the 404/405 substitution, swallowed cleanup failures, and cleanup refusals through `RuntimeDiagnostics`. Own staging teardown once and restrict `cleanup` to the adapter's own directories. Move the client tests into the module, add installer adapter tests, narrow visibility, drop the unused test edges, delete the dead forwarder and stale guard strings, and declare user-agent, header, and success-range constants once.

## Acceptance Criteria

1. `HttpTelemetryClient` has exactly one constructor; `withProcessDefaults`, the `?: JdkHttpRemoteTransport.create` fallback, and both secondary constructors are gone; `RemoteTransportPort` is provided once in `skillbill.di` from the bootstrap-resolved `TransportContext` with a typed error on null; `UpdateCheckService` takes `RemoteTransportPort`; `RECORD_ARCHITECTURE_BASELINES=1` leaves `runtime-infra-http-ambient-environment-baseline.txt` empty and adds no row elsewhere.
2. Neither `fetchProxyCapabilities` nor `fetchRemoteStats` mutates a decoded map; `TelemetryProxyCapabilities` and `TelemetryRemoteStatsResult` are constructed from typed reads with request-derived defaults; `defaultProxyCapabilities` returning a map is replaced by a typed factory; `preserveResponseCapabilities` and the `knownKeys` sets are gone; one request function serves GET and POST; the five moved client tests pass with their current assertions, including null `capabilities` surviving into `metrics`.
3. `JdkHttpRemoteTransport.kt` has one default instance and one factory with no `Duration` equality branch; `RuntimeBootstrapBindings` calls the factory with the two nullable timeouts; `AbsentOptionalPortResolutionTest` asserts identity against the named default; `HttpInstallerScriptFetchAdapter` takes `RemoteTransportPort` and imports nothing from `java.net.http`; the redirect policy is a decision entry; the 2xx range is declared once in the module and the two domain constants are deleted.
4. Non-2xx, invalid JSON, non-object JSON, blank 2xx body, and blank relay URL raise typed errors under `SkillBillRuntimeException`; `InvalidTelemetryTransportOutcomeError` extends that base; `grep -n "IllegalArgumentException\|IllegalStateException"` over the module's `src/main` returns nothing; tests assert the typed classes.
5. `RuntimeDiagnostics` is injected into both adapters; the 404/405 default substitution, each swallowed cleanup failure, and a `cleanup` refusal emit one record each naming seam, expected value, and used value; a test proves the 404 record and a test proves the cleanup-refusal record.
6. `fetch` has one teardown path executed on every non-`Ready` exit; `cleanup` removes only a `skill-bill-update-` staging directory containing the given script; tests prove no staging directory remains after a non-2xx, an `IOException`, and an `InterruptedException` (rethrown), that a 2xx yields an existing `Ready.scriptPath`, and that `cleanup` refuses a foreign directory.
7. `telemetryProxyBatchPayload` and the result mappers are `internal`; `TelemetryReleaseAttributionTest` no longer imports `skillbill.infrastructure.http`; `runtime-mcp` and `runtime-cli` build files declare no `runtime-infra-http` test edge and `RuntimeAdapterDependencyAllowlistTest` passes; `TelemetryHttpRuntime.kt` is deleted and the three guard strings are removed; user-agent and header names are single declarations; no request or response byte changes.

## Non-goals

No change to the set of wire keys the module spells or to where they are declared; that is subtask 2. No change to ports, `TelemetrySettings`, domain validators, delivery-outcome classification, deadlines, or the proxy worker. No module move.

## Dependency notes

Depends on: none. This commit includes every binding, call-site, test, build-file, and document change its signatures require so it ships alone. Subtask 2 rebases on it and re-censuses the remaining literals.

## Validation strategy

Name the regression before each test: an unresolved context reaching the adapter, a lost null `capabilities` metric, a silent 404 fallback, a peer fault with an argument-error identity, a partial script left on disk, a staging directory surviving interruption, `cleanup` deleting a caller's directory. Run the module suite, `runtime-core` (architecture guards including the ambient-environment recorder, `AbsentOptionalPortResolutionTest`, `RuntimeAdapterDependencyAllowlistTest`), `runtime-application`, `runtime-cli`, `runtime-mcp`, and `./gradlew check` on runtime-kotlin; run the pack-declared quality gate and bill-unit-test-value-check for changed tests.

## Next path

Continue to `spec_subtask_2_own-the-telemetry-proxy-vocabulary-once-and-register-the-seam.md` through the goal runtime after this subtask settles.

## Spec Path

.feature-specs/SKILL-359-runtime-infra-http-boundaries-and-simplicity/spec_subtask_1_resolved-dependencies-typed-results-one-transport-and-adapter-tests.md
