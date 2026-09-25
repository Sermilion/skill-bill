# SKILL-376 Subtask 1 - Process ownership, single owners, and adapter seams

Parent spec: [.feature-specs/SKILL-376-runtime-infra-hexagonal-boundaries/spec.md](spec.md)
Issue key: SKILL-376

## Scope

Resolve F-002, F-004, F-005, F-006, F-007, F-008, and F-010 in
[investigation.md](investigation.md).

Process ownership (F-002):

- `runtime-infra/host/…/host/process/BoundedExternalProcessRunner.kt`:
  - Give `BoundedExternalProcessRequest` `stdin: ByteArray?` and one output mode:
    capped capture (today), streamed lines (`maxLineBytes`, `onLine`,
    `shouldStop`), or redirect to a file (today's `redirectOutputFile`).
  - Keep one session class. Stdin is written on its own thread and closed. When
    `shouldStop` returns true, streamed-line mode stops reading and terminates
    the process tree.
- `workflow/…/workflow/process/GitProcessInvocation.kt`:
  - Rewrite `invokeGitProcess` and `invokeGitProcessWithBoundedLines` over the
    host runner.
  - Delete `GitProcessBoundedLineSession` (L57) and `GitProcessSession` (L201).
  - Keep `gitTimeoutSeconds`, `GitProcessResult`, and the selected-diff parser.
    `WorkflowGitOperations` returns the same values and errors.
- `FileSystemPrCheckProcessRunner`: run through the host runner with a small
  output cap and the same 30-minute deadline. Keep exit 124 on timeout and
  measure duration with the injected `Clock`.
- `readGitTrackedFiles` (`host/jvm/GitTrackedFiles.kt`) and
  `GateJvmResolver.runGuard`: run through the host runner. Keep their current
  results, including the `null` "validate every file" fallback.
- Move `InstallerProcessAdapter` from `launcher` to `host.process` and update its
  runtime-core binding.

Single owners (F-004, F-005):

- Delete `sqlite/review/accounting/ReviewAccountingBoundedSerialization.kt`'s
  `encodeReviewAccountingBoundedPayload` and
  `ReviewAccountingWireExtensions.kt`. SQLite persists through the one
  serializer this subtask keeps for this shape. If that serializer does not
  exist yet, keep one non-public serializer in this commit and point SQLite and
  the runtime-core test at it. Keep any
  node-level helpers the SQLite decoder still needs.
- `SqliteExperimentPairStore.kt:245`: compare lease expiry with the unit of
  work's injected `Clock`. If the store is already gone, skip this line. If it
  is still there, fix it here.
- `JvmAgentRunProcessOutputDrain.kt:228`: replace the `currentTimeMillis()`
  change token with a counter. Readers compare it for inequality only.
- Add `System.currentTimeMillis()` to `AMBIENT_CLOCK_FORMS`
  (`ArchitectureScanGuardSupport.kt:20`). After the two fixes, production has no
  such call, so no baseline gains a row. SKILL-373 subtask 2 later rewrites this
  guard's iteration and baseline key and keeps its forms.

Loud validation (F-006):

- `RepoValidationRuntimeRepoChecks.kt:156`, `RepoValidationCollected.kt:31`,
  `RepoValidationRuntimeSkillDiscovery.kt:129`: build a fixture that makes each
  discovery throw.
  - If the failure already reaches `issues` through another check, delete the
    `runCatching` and rely on that report.
  - Otherwise, append an issue naming the file and cause.

Seams (F-007):

- `launcher/…/process/launch/AgentRunProcessRequestFields.kt`:
  - Keep the six group data classes and `AgentRunProcessRequest(…)` with its
    `init` validation.
  - Delete the 25 forwarding getters, `AgentRunProcessRequestDsl`, and
    `agentRunProcessRequest(...)`.
  - Callers construct with named arguments and read through the groups. The test
    helper becomes direct construction or a default-argument function in test
    sources.
- Delete `AgentRunAdapter`. `headlessAgentRunAdapters` returns
  `Map<InstallAgent, ProcessAgentRunAdapter>`.
- `GhGoalPullRequestPort`: replace the mutable `ghExecutableResolver` with a
  constructor value, primary `internal` and `@Inject` secondary. Tests pass the
  resolver.
- Delete the typealiases `FeatureTaskRuntimePhaseOutputSchemaValidator` (contracts
  `…runtime.phase`, L103), `WorkflowStateRow`, and `TelemetryOutboxRow`, and use
  the target names.
- `DatabaseRuntime.writeReadinessGate` becomes `val`.

Dependency hygiene (F-008):

- Remove the unused declarations:
  - runtime-application: `testImplementation` of infra host, contracts,
    workflow, and sqlite; `testFixturesImplementation` of infra sqlite
  - runtime-engine: `testImplementation` of infra host and skills
  - infra workflow: `libs.json.schema.validator`
  - infra launcher: `libs.jackson.dataformat.yaml`

  If a suite then fails at runtime on a missing class or resource, restore that
  edge as `testRuntimeOnly` and name the reason in the commit body.
- Move `ReviewPacketConsumerContractParityTest` and
  `InstallNativeAgentLinkApplyCodexTest` into `runtime-infra/workflow` tests, or
  drop their one workflow-dependent assertion. Then remove the
  `:runtime-infra:workflow` test edges from contracts and skills.
- `TriageAndLearningsRuntimeTest` and `ReviewStatsRuntimeTest` build input from
  domain and port types instead of `learningAppliedSessionWire` and
  `learningEntryDto`. Then remove `testImplementation(project(":runtime-application"))`
  from infra sqlite.

Documentation (F-010): rewrite the `ARCHITECTURE.md` infra passages so each
responsibility names one owning module. That covers the 20 five-module phrases,
the validator location, the `skillbill.agentaddon` entry, and the SQLite
public-surface list. The host entry states that host owns one-shot process
execution.

## Acceptance Criteria

1. `ProcessBuilder(` appears in `runtime-infra` production sources only in
   `BoundedExternalProcessRunner.kt` and `JvmAgentRunProcessRunner.kt`, and
   `GitProcessSession` and `GitProcessBoundedLineSession` do not exist.
2. Existing `WorkflowGitOperations` tests pass unchanged, and a new test covers a
   stdin-fed git command, a bounded diff read that stops early, and a git timeout.
3. A test runs `FileSystemPrCheckProcessRunner` on a command that writes 2 MiB and
   exits 3, and asserts exit code 3 within 30 seconds. A second test asserts
   that no descendant of a timed-out command is alive after the call returns.
4. `encodeReviewAccountingBoundedPayload` and the SQLite `toBoundedPayload`
   extension do not exist, and a persisted accounting row's bytes equal the
   baseline fixture.
5. No `runtime-infra` production file contains `System.currentTimeMillis()`, and
   the ambient-clock guard reports a synthetic `System.currentTimeMillis()` call.
   A pair-lease test on a fixed `Clock` observes a lease as live before
   `expires_at` and expired after it (skip if the store no longer exists).
6. For each of the three validation sites, `skill-bill validate` on a fixture that
   makes discovery throw reports an issue naming the file.
7. `AgentRunProcessRequestDsl`, `agentRunProcessRequest(`, `AgentRunAdapter`, the
   three listed typealiases, and any `var` in `GhGoalPullRequestPort` do not exist.
   `AgentRunProcessRequest` declares no property that only forwards to a group.
8. `InstallerProcessAdapter.kt` lives under `runtime-infra/host`.
9. None of the nine unused declarations remains as a compile-scope edge. The
   contracts and skills builds declare no `:runtime-infra:workflow` edge, and the
   sqlite build declares no `:runtime-application` edge.
10. `ARCHITECTURE.md` contains no occurrence of the five-module ownership phrase and
    no mention of `WorkflowGoalRunnerOutcomeStoreBridgeBuilder`.

## Non-goals

- Changing `JvmAgentRunProcessRunner`, its wait loop, or probes.
- Changing git argv, timeouts, or `WorkflowGitOperations` result semantics.
- Changing `PrCheckRunResult` or the `PrCheckProcessRunner` port.
- Moving `ContentDigest` or the launcher digest copy.
- Changing ambient-clock scanner forms beyond the one addition above.

## Dependency notes

- First subtask inside this bundle. It does not wait for another issue.
- Choose the accounting serializer in this subtask when the tree does not
  already have one non-public owner. Edit host and launcher files this
  subtask's criteria touch, at the paths that exist now.
- Fix `SqliteExperimentPairStore` when that file still exists.

## Validation strategy

- Run the host, workflow, launcher, contracts, skills, and sqlite suites, plus
  runtime-application, runtime-engine, runtime-core, and the architecture suite.
- The new PR-check tests would fail before the change: the output test waits for
  the timeout, and the tree test finds a surviving child.
- Changed tests go through `bill-unit-test-value-check`. The validate phase runs
  the routed pack quality gate.

## Next path

Subtask 2 (`spec_subtask_2_goal-runner-coordination-to-engine.md`).
