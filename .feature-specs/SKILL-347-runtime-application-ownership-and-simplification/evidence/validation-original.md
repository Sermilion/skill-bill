# Original validation evidence

## Existing tests

Commands ran from `runtime-kotlin` at commit `669639d2b9790c0aa40d21e827832b5d7d0f75dd` on 2026-09-15.

```sh
./gradlew :runtime-application:test --console=plain
./gradlew :runtime-core:test --tests '*RuntimeLayerBoundaryArchitectureTest' --tests '*ApplicationPackageAcyclicityArchitectureTest' --tests '*InjectConstructorDefaultsArchitectureTest' --tests '*RuntimeApplicationAmbientClockArchitectureTest' --tests '*ImplementationOwnershipArchitectureTest' --console=plain
```

The application task executed 592 tests, with zero failures, errors, or skips. The five architecture classes executed 68 tests, with 67 passing and one failing. The failed test is `runtime application owns no direct timing logging or threading environment APIs` in RuntimeLayerBoundaryArchitectureTest. It names direct thread access and the JVM cancellation alias in TelemetryService, TelemetrySyncRuntime, and TelemetryOutboxDrain. The [retained command log](architecture-check.log) preserves the exact failure message and total. A concurrent runtime-core test invocation replaced the shared XML reports before archival, so no per-suite architecture counts are claimed. The JSON summary preserves application and probe per-suite counts.

These are bounded investigation checks. They are not a full repository quality gate, a live review-driver result, or evidence that implementation has been repaired. No repository fixes were attempted. The quality-check skill's repair loop was not started because the requested deliverable is an investigation and spec.

## Isolated defect probes

Six temporary JUnit probes ran against compiled production code and existing test fixtures. Their expectations describe the broken baseline. All six passed, which confirms the observed defects rather than validating a fix.

| Probe | Observation |
| --- | --- |
| Independent activity writers after a failed write | First database gets one attempt, second database gets zero at the same workflow ID and instant |
| Cancelled activity write | Call returns normally despite database cancellation |
| Cancelled spec read | Cancellation becomes SpecIntentSourceUnavailable with reason unreadable |
| Interrupted optional persistence | Operation returns its configured fallback |
| Interrupted update check | Operation returns UNKNOWN with a network-failure reason |
| Cursor staging failure after endpoint bind | One handle opens, zero close calls, no parent launch, ordinary failed-lane result |

[Probe source](probe-source.kt.txt) and [temporary Gradle source injection](probes.init.gradle.txt) are retained as evidence. Source paths in the init script point into `/tmp/skill-bill-application-audit/probes`. To reproduce, create that directory, copy the probe text there as ApplicationInvestigationProbeTest.kt, and save the init-script text as `/tmp/skill-bill-application-audit/probes.init.gradle`.

```sh
./gradlew -I /tmp/skill-bill-application-audit/probes.init.gradle :runtime-application:test --tests '*ApplicationInvestigationProbeTest' --no-configuration-cache --console=plain
```

The endpoint probe uses reflection to replace the harness's fixed staging port. It runs the production runner afterward. Permanent regression tests should expose the existing staging substitute through the harness and assert the desired closure behavior without reflection.

The failed database and endpoint substitutes are isolated. No real workflows, telemetry deliveries, remote writes, or review-agent launches occur. The probes do not exercise actual socket teardown or durable SQLite transactions. F-004 and F-005 remain source-traced findings requiring the end-to-end projection and controlled overlapping-call regressions described in the spec.

## Spec preparation

The existing FeatureSpecPreparationWriter rendered the parent, three distinct subtask specs, and manifest into an isolated staging root. Its normal path validated the bundle before writing and read the manifest back through the runtime. The rendered manifest's staging-root prefixes were removed for repository portability and the runtime schema and coherence validator accepted that portable form unchanged.

Before the first repository write, a second artifact check verified acceptance sections, subtask ordering and paths, pending status, no workflow or commit identity, local links, and unchanged application source hashes. The final write uses atomic replacement per file. The manifest remains version 0.5 and local spec mode omits spec_source.

[Source inventory](source-inventory.csv), [test summary](test-summary.json), and [preparation receipt](spec-validation.txt) preserve the relevant evidence. Preparation did not run skill-bill goal.
