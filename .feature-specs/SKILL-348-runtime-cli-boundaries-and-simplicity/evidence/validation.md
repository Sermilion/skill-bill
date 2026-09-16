# Validation evidence

Investigation date: 2026-09-16. Initial HEAD: `b76359a5a604fb81188bc83a9b5759293e438dc1`.

## Existing behavior and architecture checks

The final isolated test run passed 301 CLI tests. That total includes 295 existing tests and six temporary defect probes. Seven selected runtime-core architecture test classes passed 51 tests. There were no failures, errors, or skips. Counts and class names are in [test-summary.json](test-summary.json).

Command, run from `runtime-kotlin`:

```bash
./gradlew :runtime-cli:test :runtime-core:test \
  --tests 'skillbill.architecture.RuntimeCliAreaIsolationArchitectureTest' \
  --tests 'skillbill.architecture.RuntimeAdapterDependencyAllowlistTest' \
  --tests 'skillbill.architecture.RuntimeCoreCompositionOnlyTest' \
  --tests 'skillbill.architecture.RuntimeCompositionGuardArchitectureTest' \
  --tests 'skillbill.architecture.ApplicationPackageAcyclicityArchitectureTest' \
  --tests 'skillbill.architecture.AmbientEnvironmentArchitectureTest' \
  --tests 'skillbill.architecture.InjectConstructorDefaultsArchitectureTest' \
  --init-script /tmp/skill-bill-348/probes.init.gradle --console plain
```

The `--tests` options apply to runtime-core. runtime-cli ran its whole test task with the six temporary probe methods included. See [isolated-checks.log](isolated-checks.log).

Another active runtime task overwrote the standard runtime-core XML results during the initial run. The final run used dedicated report and binary-result directories under `/tmp/skill-bill-348`. Its results are the evidence reported here. An initial root-level `./gradlew` attempt failed because the wrapper lives under `runtime-kotlin`; subsequent commands used that wrapper.

No production repair or broad lint/format gate was requested. This audit ran focused behavioral and architectural evidence checks rather than invoking bill-code-check's full repair workflow. The future implementation specs require the governed quality gate.

## Defect probes

[probe-source.kt.txt](probe-source.kt.txt) and [probes.init.gradle.txt](probes.init.gradle.txt) retain the exact source and Gradle setup. The probes were compiled from a temporary directory, not added to repository test sources. These probes intentionally assert the observed faulty behavior; they are evidence, not regression tests to merge unchanged.

| Probe | Observed behavior | Finding |
| --- | --- | --- |
| failedDownloadIsCurrentlyReportedAsCompleted | Fake curl exits 22 with an error message; real update command returns exit 0 and completed | F-001 |
| interruptedInstallerLeavesItsChildAlive | Interrupting the runner after its child closes output returns a result while the owned sleep process remains alive | F-001 |
| uninstallCurrentlyContinuesAfterCancellation | A cancelled MCP target is followed by another target; both become degradation records | F-002 |
| diagnosticCleanupCurrentlyReturnsHelpAfterWritingItsOwnOutput | CLI cleanup returns exit 0 with root Usage help after its own output path | F-003 |
| configDefaultCurrentlyIgnoresInjectedRepositoryRoot | Context-only repository returns local; explicit flag for the same repository returns linear | F-004 |
| malformedStepUpdatesCurrentlyAllowWorkflowMutation | Broken JSON in step-updates is accepted with an ok result and exit 0 | F-005 |

The process probe closed its child's output before interruption so it deterministically exercised waitFor teardown. It does not establish a bound on the separate blocking readText path. The test finally block forcibly terminated only the child identified through its own temporary PID file.

The update probe used a fake local curl with no network download and no installer script body. Uninstall used a fake registration port. Other probes used temporary homes and SQLite databases. Raw-output suffix corruption and stdin-selection failures are source-traced findings; they were not represented as separately executed Main-process probes.

## Spec preparation

`skill-bill config resolve-spec-type --arg default` returned `local`. Existing local spec paths included SKILL-347 as the highest key, so the requested next key is SKILL-348. No Linear issue was created.

The final preparation called FeatureSpecPreparationWriter with DecompositionManifestValidatorAdapter, the production schema/coherence validator, and an atomic filesystem test-store adapter. The complete governed bundle rendered in a temporary staging root. It contains one parent, two distinct subtask specs, and a contract-version 0.5 manifest. The portable manifest was validated again after temporary absolute prefixes were removed.

The first preparation experiment used the application's lightweight test validator. That does not prove schema validity. It was replaced by a run using the production validator before publication. Only the latter receipt is reported as schema validation.

```bash
./gradlew :runtime-cli:test \
  --tests skillbill.application.featurespec.CliInvestigationSpecPreparationTest \
  --init-script /tmp/skill-bill-348/probes.init.gradle --console plain
```

See [preparation-source.kt.txt](preparation-source.kt.txt), [preparation-production-validator.log](preparation-production-validator.log), and [spec-validation.txt](spec-validation.txt). The preparation test ran separately from the 301-test CLI evidence run.

Publication used an atomic directory rename within `.feature-specs` after link, acceptance-list, and key-collision checks. All subtask states are pending. No implementation, commit, push, goal run, or skill installation took place.

## Coverage and limits

[source-inventory.csv](source-inventory.csv) records 175 CLI source/test files, including 114 production files. Production line count is 11,370. The final hash comparison found no changes to the recorded CLI files during the investigation.

Unrelated SKILL-248 edits were present in runtime infrastructure and contracts by the end of the task. They were not altered or reverted. This is evidence for the observed working tree, not an immutable clean-checkout certification.

The investigation did not benchmark startup, evaluate every presentation field, launch real agents, or run native Windows/macOS behavior. The architecture checks prove their declared scan scope. They do not prove universal Clean Architecture, SOLID, or YAGNI compliance.
