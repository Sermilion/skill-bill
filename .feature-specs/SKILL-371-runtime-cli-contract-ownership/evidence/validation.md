# Preparation evidence

This file records what preparation observed. It is not a review, quality-gate, or validate receipt.

## Build state

- Shared checkout HEAD moved during preparation from `a9c9f4ba0` to `11d8615ba`, then to `dbf9f4830`, through concurrent SKILL-368 commits. No `runtime-cli/src/main` file changed across those commits.
- `./gradlew :runtime-cli:installDist` failed on the shared working tree, which had uncommitted SKILL-368 edits.
- A clean `git archive 11d8615ba` snapshot also failed: `runtime-engine/.../FeatureTaskRuntimeFindingVerificationBoundaryMemoryProbe.kt:24:12 Return type mismatch: expected 'String?', actual 'Unit'`.
- Probes therefore used the existing distribution `runtime-kotlin/runtime-cli/build/install/runtime-cli/lib` (built 2026-09-22 16:26), invoked as `java -cp "lib/*" skillbill.cli.core.MainKt --db <tmp>/p.db --home <tmp>/home …` with OpenJDK 21. Stdin was `/dev/null`.

## Behavior probes

| # | Invocation | Exit | stdout | stderr | Finding |
| --- | --- | --- | --- | --- | --- |
| P1 | `experiments stats` | 0 | result line plus 88 lines of root `Usage:` help | empty | F-003 |
| P2 | `experiments report nope` | 1 | empty | `IllegalStateException: unknown pair nope` stack trace | F-002, F-003 |
| P3 | `import-review /nonexistent --format json` | 1 | empty | `java.nio.file.NoSuchFileException` stack trace (16 lines) | F-002 |
| P4 | `feature-task rejected-output --workflow nope` | 1 | empty | `RejectedOutputDiagnosticError$Absent` stack trace (9 lines) | F-002 |
| P5 | `goal status NOPE-1 --format json` | 1 | `Usage: …` plus `Error: no such option --format` | empty | F-002 |
| P6 | `feature-task repair-identity wfl-x SKILL-1 /etc/passwd --reason r --format json` | 1 | `Usage: …` plus the domain refusal | empty | F-002 |
| P7 | `verify-workflow show wfl-nope --format json` | 1 | JSON error payload | empty | Reference: the one consistent error shape |

## Static checks

- `ArchitectureScanSupport.runtimeRoot` resolves to the repository root (it walks up to the first directory containing `runtime-kotlin/`). `ls <repo>/runtime-cli/src/main/kotlin` fails with "No such file or directory".
- `runtime-core/build/test-results/test/TEST-skillbill.architecture.RuntimeEngineInboundApiTest.xml` (2026-09-22T15:29:42Z): 2 tests, 0 failures, 0.002 s total.
- Public raw-map functions present in inner layers: `runtime-domain/.../review/context/model/accounting/ReviewAccountingPayload.kt:5` and `runtime-application/.../review/stats/ReviewAccountingOutput.kt:5`.
- runtime-cli `import skillbill.engine.*` census: 63 distinct types in 23 files. 13 are absent from `PINNED_ENGINE_INBOUND_API_TYPES`.
- `grep -rl goal-review-base-sha --include=*.kt runtime-kotlin` returns only `AgentRunCommandBuildersLaunch.kt`, `FeatureTaskRuntimeCliCommands.kt`, and `FeatureTaskRuntimeRunRequestAssembly.kt`. No test file.
- The CLI and MCP scaffold parser files are identical after replacing `args` with `payload` and stripping package and import lines.
- `repo-root-realpath-v1:` producers in main source: CLI 1, runtime-infra/host 1, runtime-infra/sqlite 1, runtime-engine 4 (including the status-reason coherence site).
- `feature-task-runtime` invocations outside Kotlin sources, archived specs, and history files: none in `skills/`, `orchestration/`, `docs/`, `platform-packs/`, `intellij-plugin/`, or `install.sh`.
- runtime-cli: 16 text renderers take `Map<String, Any?>`, 10 `Map<String, Any?>.…ExitCode()` functions, 62 `as? Map<*, *>` / `as? List<*>` casts.
- `--format`: 58 `formatOption()` uses, plus 3 hand-rolled declarations.

Probe P1 through P6 each reproduce the observed faulty behavior. Their passing proves reproduction, not correctness.
