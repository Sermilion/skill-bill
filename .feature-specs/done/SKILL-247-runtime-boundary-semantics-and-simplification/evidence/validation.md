# Investigation validation

## Selected existing tests

Run from `runtime-kotlin` on 2026-09-15:

```bash
./gradlew :runtime-core:test \
  --tests 'skillbill.architecture.RuntimeArchitectureTest' \
  --tests 'skillbill.architecture.RuntimeLayerBoundaryArchitectureTest' \
  --tests 'skillbill.architecture.ApplicationPackageAcyclicityArchitectureTest' \
  :runtime-infra-sqlite:test \
  --tests '*DatabaseWriteMaintenanceRegressionTest' \
  --tests '*SQLiteDatabaseSessionFactoryTest' \
  --tests '*TelemetryOutboxStoreTest' \
  :runtime-infra-fs:test \
  --tests '*JvmAgentRunProcessRunnerTest' --console=plain
```

Result: 99 tests across seven selected classes, zero failures, errors, or skips. Gradle completed in 11 seconds with some cached build outputs. The XML counts are copied into inventory.json; focused-tests.log contains the command output. This was an evidence run, not a full quality gate or a repair run.

## Reproductions

Run after the current runtime classes and CLI dependency jars are built:

```bash
python3 .feature-specs/SKILL-247-runtime-boundary-semantics-and-simplification/evidence/run_probes.py
```

- OutboxOwnershipProbe calls the compiled TelemetryOutboxStore with an in-memory SQLite database. A claims, B reclaims after expiry, A settles late, then C claims B's still-in-flight row. It does not contact the telemetry receiver. The temporary schema contains only the outbox columns consumed by the real adapter, so this is not a migration test.
- CancellationProbe calls compiled TelemetrySyncRuntime.autoSyncTelemetry with a repository proxy that throws CancellationException. It returns null. A transport proxy fails if invoked, so no network call can occur.
- RollbackEvidenceProbe invokes the compiled private transaction helper through reflection, with JDBC proxies that fail the body and rollback. The primary exception survives with no suppressed rollback failure. This isolates failure precedence and evidence; it is not a database-corruption or transaction-isolation test.

These probes are investigation artifacts, not additions to the product test suite. Each temporary compilation uses a disposable directory. The recorded results describe the current bugs; implementation tests should assert the corrected behavior.

## Spec preparation

`skill-bill config resolve-spec-type --arg default` returned `local`.

A temporary Java adapter passed the in-memory parent/subtask request to the compiled FeatureSpecPreparationWriter with DecompositionManifestValidatorAdapter, FileSystemDecompositionManifestFileStore, and DecompositionManifestWriter. The runtime validated the full manifest before its atomic bundle write and loaded it again afterward. spec-validation.txt records successful schema and coherence validation plus four executable subtask paths. The writer emitted absolute subtask paths. Preparation normalized them to repository-relative paths, schema/coherence-validated the complete manifest again, and atomically replaced it. No implementation command ran.

Post-write validation checks every acceptance section, subtask path, local source link, source hash, and manifest status. Generated spec headings retain the runtime writer's format.

## Limits

No full check, detekt pass, dependency vulnerability scan, install, production latency measurement, live endpoint request, or complete semantic review of every Kotlin function ran. Source-traced findings are identified separately from reproduced behavior. Runtime and test source files remain unchanged.
