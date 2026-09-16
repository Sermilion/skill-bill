# Validation evidence

## Current refresh

The current source baseline is `2ef12ad7e76b8282a1cfa2bb8cdfd795d3e79c7f`, inspected on 2026-09-15. The commands below ran from `runtime-kotlin`.

```sh
./gradlew :runtime-application:test :runtime-core:test --tests '*RuntimeLayerBoundaryArchitectureTest' --tests '*ApplicationPackageAcyclicityArchitectureTest' --tests '*InjectConstructorDefaultsArchitectureTest' --tests '*RuntimeApplicationAmbientClockArchitectureTest' --tests '*ImplementationOwnershipArchitectureTest' --console=plain
```

The application task ran all 592 tests. The filters apply to the following runtime-core task, which ran 68 tests in the five named classes. All 660 passed, with no failures, errors, or skips. The former direct application thread-reference failure is resolved in this revision. See [the command log](refresh-checks.log) and [per-suite counts](refresh-test-summary.json).

The remaining F-002 adapter ownership issue is source-traced. JvmInterruptSignalPort resides in runtime-ports, and three application defaults select it. The passing direct-reference guard does not cover that route. The spec requires preserving interruption behavior while completing adapter ownership and extending the existing guard.

The same six isolated probes ran again against this revision. All six passed their assertions of broken behavior. A seventh check exercised FeatureSpecPreparationWriter, then read and validated the portable manifest through the runtime schema and coherence validator. See [the probe and preparation log](refresh-probes-and-preparation.log). These are defect reproductions and artifact checks, not seven additional intended-behavior regressions.

```sh
./gradlew -I /tmp/skill-bill-347-refresh/probes.init.gradle :runtime-application:test --tests '*ApplicationInvestigationProbeTest' --tests '*ApplicationInvestigationSpecPreparationTest' --no-configuration-cache --console=plain
```

The retained [probe source](probe-source.kt.txt) is unchanged. [The refreshed init script](refresh-probes.init.gradle.txt) adds temporary sources from `/tmp/skill-bill-347-refresh/probes`. [The preparation invocation](refresh-preparation-source.kt.txt) records the exact writer request. Reproduce by copying those two source files into that directory as ApplicationInvestigationProbeTest.kt and ApplicationInvestigationSpecPreparationTest.kt, and saving the init script at the command's path. The preparation check writes only its temporary staging bundle. No live agent launches or remote deliveries occur.

F-004 projection identity and F-005 overlapping update-check calls remain source-traced. This refresh confirms their current implementations and specifies the missing behavioral regressions without claiming dynamic reproduction.

## Artifact validation

The shared writer rendered the updated parent and all three subtask specs in an isolated staging root, validated before writing, and read the manifest back. Removing staging-root prefixes produced a portable manifest accepted unchanged by the runtime schema and coherence validator. The manifest is byte-identical to the existing one. Subtask 2 and subtask 3 are also unchanged.

Before replacing repository files, the artifact check validates all acceptance lists and sections, local links, manifest paths and pending state, source hashes, and whitespace. The source census is 173 files and 16,593 lines. Of those, 170 match the original inventory; the three telemetry files were changed before this refresh. [The new inventory](source-inventory-refresh.csv) and [refresh receipt](refresh-spec-validation.txt) bind evidence to this revision. File replacement is atomic per file. No implementation, workflow state, commit, or push is part of this update.

The full repository quality gate and its repair loop were not run. The user's deliverable is an investigation and updated spec. Bounded application tests, selected architecture tests, and isolated probes provide the evidence recorded here.

## Historical evidence

The earlier audit ran at `669639d2b9790c0aa40d21e827832b5d7d0f75dd` and finished at `8983f742ce90e2c99cd03a0908f9d4e2eab7c066`. Its one failed architecture test is historical and must not be reported as a current failure. [The original validation account](validation-original.md), original logs, inventory, test summary, and spec receipt remain available for provenance.
