# SKILL-371 subtask 2 - One CLI process output and failure contract

Parent: [spec.md](spec.md). Findings: F-002, F-003, F-006, F-008, F-009 in [investigation.md](investigation.md).

## Scope

Give runtime-cli one policy for what reaches stdout, what reaches stderr, and which exit code a run returns. Remove the command-level copies and generalizations that exist without a current need.

- **Failure policy (F-002).** Add a stderr field to `CliExecutionResult`, and have `Main` write it to `System.err` (embedders read the field). Map failures once in `CliRuntime`:
  - Clikt usage errors: usage text on stderr, same status code as today.
  - `SkillBillRuntimeException` subtypes and `IllegalArgumentException` from `require`: a one-line message on stderr, exit 1.
  - `java.nio.file.NoSuchFileException` / `AccessDeniedException` on user-supplied paths: a one-line message naming the path, exit 1.
  - Any other throwable: a one-line diagnostic naming the type, a `RuntimeDiagnostics` error record, exit 1. `CancellationException` and `InterruptedException` keep their propagation semantics.

  Delete command-local catch blocks that only duplicate this mapping. Keep catches that produce a documented, command-specific payload. Examples are `verify-workflow` error JSON, `ReviewAggregationIntegrityError` text, and scaffold `status: error` JSON under `--format json`.
- **experiments (F-003), only if experiment support still exists** (SKILL-378 subtask 1 deletes it). Complete through `CliRunState` and use `formatOption()`. An absent pair raises a typed `SkillBillRuntimeException` subtype. The navigation spec read and acceptance-criteria validation move into the coordinator request the command calls. The command passes the path, and the engine or application side owns the read and the `ExperimentNavigationSpecError`. Stats render through `CliOutput`, not `Map.toString()`.
- **Typed exit codes and presenters (F-006).** In `goal/*` and `featuretask/*`, derive every exit code from the typed result: status enum plus a closed reason kind. If `GoalRunner` results lack a typed reason kind, add one to the engine result model. Replace `goalRunExitCode`'s substring matching. Text renderers take typed presentation models. The JSON map is produced from the same typed value, with identical keys.
- **Consistency (F-008).** Use `UpdateRunStatus.wireValue` (add it if missing) instead of the local `when`. Compare `CliFormat` values, not strings. Render update-check through `UpdateCheckContract` in both CLI and MCP, and add `release_url` to the contract. Convert `work list`, `work status`, and `experiments report` `--format` to Clikt `choice` with their current defaults. CLI install commands stop restating `~/.skill-bill/runtime` and pass `null` to the planner default. `replay-last-selection` stops fabricating a full `InstallPlanRequest` for discovery and moves the stale-slug refusal next to the selection read.
- **Cuts (F-009).** Replace the four-class `feature-task-runtime` tree with a root alias that preserves the stderr deprecation note. Replace the seven grouping holders with an ordered provider over area holders, or at most two groups named by content, and keep help order. Pass the workflow id instead of a thunk to `executeRuntimeRun`. Keep one `GoalContinuationCandidate` / `FeatureTaskContinuationCandidate` mapper in `kernel/payload`. Delete `WorkflowContinueCliBranchMapsDecomposition.kt` and the `verify-workflow continue --subtask-id` option, which cannot take effect for the `VERIFY` family. Delete `CliFormat.fromWireName` and the redundant `requireNotNull`. Leave the `WireVocabularyGovernedSeamInventory` workflow marker alone, because SKILL-375 subtask 1 removes it.

## Acceptance Criteria

1. Through `CliRuntime.run`, probes P2–P6 in evidence/validation.md return empty stdout, a one-line diagnostic on the result's stderr channel with no `at ` stack frames, and exit 1.
2. Either the `experiments` command no longer exists, or through `CliRuntime.run` `experiments stats` returns its rendered stats with no `Usage:` text and `experiments report --format yaml` fails at parse as an invalid choice.
3. `CliExecutionResult` has a stderr field, `Main` writes it to process stderr, and no production command writes to `System.err`, `System.out`, or Clikt `echo` directly.
4. An unexpected non-typed exception thrown by a command yields exit 1, a one-line stderr diagnostic, and a `RuntimeDiagnostics` error record, observed through an injected diagnostics substitute.
5. No `goal` or `featuretask` exit-code function reads a `Map<String, Any?>` or matches substrings of a free-text reason. A blocked goal whose reason text contains "failed" exits with the blocked code.
6. No `goal` or `featuretask` text renderer takes `Map<String, Any?>`, and the existing JSON goldens for those commands are byte-identical.
7. CLI and MCP update-check JSON share the same fields, including `release_url`, and `UpdateRunStatus` wire values come from the enum.
8. `feature-task-runtime run|status|resume` still works and prints the deprecation note on stderr, and `FeatureTaskRuntimeDeprecatedCliCommands.kt` no longer exists.
9. runtime-cli contains no mapping for `WorkflowContinueResult.Decomposition*` results, and `verify-workflow continue` declares no `--subtask-id` option.
10. Root `--help` lists the same commands in the same order as before, and `MiscCliCommands`, `UtilityCliCommandGroup`, `ReviewCliCommandGroup`, and `ScaffoldCliCommandGroup` no longer exist.

## Non-Goals

- Changing exit-code numbers or JSON field names.
- Adding `--format json` to commands that do not have it today.
- The rejected-output rendering (SKILL-370).
- Moving feature-task run preparation into the engine.

## Dependency Notes

Depends on subtask 1: the restored engine-pin and area-isolation guards must pass with these changes. Independent of subtask 3. If both are in flight, subtask 3's featuretask edits rebase onto this subtask's presenter changes.

## Validation Strategy

Run the runtime-cli suite (including goldens), runtime-mcp update-check tests, and the runtime-core architecture tests. Add tests only for P1–P6, the goal blocked-versus-failed exit code, the unexpected-throwable record, and the alias deprecation note, each through `CliRuntime.run`. Changed tests go through `bill-unit-test-value-check`. The validate phase runs the routed pack quality gate.

## Next Path

Continue with subtask 3 (`spec_subtask_3_single-owners-for-cross-adapter-contracts.md`).
