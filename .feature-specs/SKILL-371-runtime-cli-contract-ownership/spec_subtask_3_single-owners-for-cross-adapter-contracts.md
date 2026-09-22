# SKILL-371 subtask 3 - Single owners for cross-adapter contracts

Parent: [spec.md](spec.md). Findings: F-004, F-005, F-007 in [investigation.md](investigation.md).

## Scope

Three contracts that runtime-cli shares with other modules each get one owner. The CLI keeps only argument translation and `UsageError` wording.

- **Repository identity (F-004).** `RepositoryEnclosingRootPort.repositoryIdentity` becomes the only producer of `repo-root-realpath-v1` values. It resolves the enclosing Git top level, matching `FeatureTaskExecutionIdentityPolicy`'s definition, and builds the value from `REPOSITORY_IDENTITY_PREFIX`. Replace these sites with calls to the port, or with the identity the caller already resolved:
  - `cli/featuretask/FeatureTaskRuntimeCliFormatting.kt` `repositoryIdentity` / `canonicalGitRoot`;
  - the four engine sites (`GoalPlanningSweep.kt`, `GoalPlanningSharedPreplanProduction.kt` ×2, `GoalPlanningStatusReasonCoherence.kt`);
  - `runtime-infra/sqlite/.../goalrunner/control/GoalRepositoryIdentity.kt`. SKILL-376 subtask 2 moves that package into runtime-engine. If it has landed, the SQLite derivation survives as a private function in the moved engine file, so replace it there.
  - `runtime-engine/.../goalrunner/GoalRepositoryIdentity.kt` already forwards to the port. Collapse it into direct port calls, or keep it as the single engine entry point, but do not leave both.

  Governed spec path normalization (inside-root check, `.feature-specs/…md` rule) moves beside the domain policy that validates it, and the CLI translates its failure to `UsageError`. Persisted rows are not rewritten.
- **Goal-child launch protocol (F-005).** Add one owner in `runtime-contracts` for the child command tokens (`feature-task`, `run`, `resume`), every goal-continuation flag name, and the env names `SKILL_BILL_GOAL_CONTINUATION` and `SKILL_BILL_QUALITY_GATE_SELECTION`. `AgentRunCommandBuildersLaunch.kt` / `AgentRunCommandBuilders.kt` build from it. `FeatureTaskRuntimePhaseAgentCommand`, `InstallCliMutations.kt`, `UninstallCommand.kt`, and `FeatureTaskRuntimeRunRequestAssembly.kt` declare and read from it. Build the add-on selection JSON with the existing agent add-on selection contract keys and codec instead of an ad-hoc `ObjectMapper` map.
- **Scaffold payload (F-007).** Move the parser into runtime-application's scaffold area as one decoder that accepts a `JsonObject` or payload text, not `Map<String, Any?>`, and returns `ScaffoldCommandRequest` with the existing typed errors. Add one application scaffold operation that owns:
  - session id generation (UTC);
  - the `repo_root` default: an explicit payload value wins, otherwise the invocation repository root;
  - opt-in external add-on source registration after success, reporting a registration failure as a partial outcome.

  The CLI opts into registration, as it does today, and MCP does not. Delete both adapter parser copies and `findRepoRoot`. MCP stops overwriting an explicit `repo_root`.
- Update `runtime-kotlin/ARCHITECTURE.md` (repository identity owner, launch protocol owner, scaffold decoder owner), `orchestration/shell-content-contract/SCAFFOLD_PAYLOAD.md` if its `repo_root` or registration wording changes, and the runtime-cli area history.

## Acceptance Criteria

1. Main source contains exactly one function that concatenates the `repo-root-realpath-v1:` prefix, and the CLI, engine, and SQLite callers obtain identity through it.
2. For an invocation root one directory below a Git top level, `feature-task lookup` and goal planning compute the same repository identity, which names the top-level directory.
3. runtime-cli main source contains no `.git` filesystem walk, and no `toFile().exists()` or `toRealPath()` call used for repository identity or governed spec path resolution.
4. Every goal-continuation flag and env name appears as a string literal only in the `runtime-contracts` owner. The launcher and CLI reference it.
5. A launcher test fails if any `SkillRunGoalContinuationContext` field is not emitted. A CLI test parses an argv assembled from the contract through `CliRuntime.run` with a substituted runner, and fails if any field is missing from the resulting `FeatureTaskRuntimeRunRequest`.
6. runtime-cli and runtime-mcp contain no scaffold payload parser. Both call the one runtime-application decoder, and its public signature accepts no raw map.
7. For the same payload, CLI and MCP scaffolding resolve the same `repo_root`: the explicit payload value when present, otherwise the invocation repository root. `findRepoRoot` no longer exists.
8. When external-source registration fails after a successful scaffold, the CLI result reports the scaffold as written and the registration as failed, with a non-zero exit.

## Non-Goals

- Migrating or rewriting persisted repository identities.
- Replacing argv with another child-process protocol, or adding a JSON-over-stdin channel.
- Consolidating `~/.skill-bill` layout paths across application and infrastructure.
- Changing the scaffold payload schema or `scaffold_payload_version`.

## Dependency Notes

Depends on subtask 1: restored guards must pass, and the raw-map rule must accept the new decoder signature. Independent of subtask 2. If subtask 2 has landed, rebase the featuretask presenter edits.

## Validation Strategy

Run runtime-cli, runtime-mcp, runtime-application, runtime-engine, and the runtime-infra launcher, host, and sqlite suites, plus the runtime-core architecture tests. Existing goal-runner child tests, scaffold CLI/MCP tests, and feature-task lookup tests are the behavior baseline. New tests only for criteria 2, 5, 7, and 8. Changed tests go through `bill-unit-test-value-check`. The validate phase runs the routed pack quality gate.

## Next Path

Final subtask. After it completes, the goal finishes. Record landed owners in the runtime-cli `agent/history.md`.
