# SKILL-378 Subtask 1 - Delete experiment support and close engine guard gaps

Parent spec: [spec.md](spec.md)
Issue key: SKILL-378

## Scope

Resolves investigation F-001 and F-008.

**Delete experiment support.** Start with a fresh census:
case-insensitive `experiment` over `runtime-kotlin/**/src`, `orchestration/contracts`,
`skills`, `docs`, and `README.md`. Ignore unrelated "experimental" wording
(delegated review, feature-task runtime schema comments). Delete:

- runtime-engine: `engine/experiment/**`, `engine/goalrunner/experiment/**`, the
  experiment fields on `GoalRunnerRunRequest`/`GoalPreflightRequest` and their
  reads in preflight, launch reconciliation, and launch preparation.
- runtime-ports: `ports/experiment/**`, experiment fields on agent-run launcher
  models, the linked-worktree git operations and their two typealiases (the
  only consumers are the two experiment pair coordinators, confirmed with the
  SKILL-377 census), and the matching testFixtures.
- runtime-domain: `skillbill.experiment/**` and `experimentsAvailability`.
- runtime-contracts: `contracts/experiment/**` and experiment contract-version
  constants and error types.
- runtime-infra: the SQLite experiment stores and their `UnitOfWork` member and
  prune call, `FileMachineExperimentConfigStore`, the experiment payload schema
  validator and its copy tasks, and the launcher experiment additions.
- runtime-core: experiment providers, `experimentGoalRunnerFactory`, and the
  component mixins.
- runtime-cli: `cli/experiment/**`, its registration, and `--experiments` on
  `goal` and `goal preflight`.
- The four `experiment-*-schema.yaml` contracts, the `bill-feature` forwarding
  text, doc mentions, and all related tests and fixtures.

Append a named migration after the SKILL-366 entries that drops
`experiment_reports`, `experiment_pair_leases`, `experiment_arm_outcomes`,
`experiment_observations`, and `experiment_pairs`, children first, with
`IF EXISTS`. Keep the SKILL-366 entries.

**Close guard gaps.**

- Move the six test-only companion null objects named in F-008 to runtime-engine
  `src/testFixtures` in the same packages. Update `GoalPlanningSweepTest`,
  `GoalRunnerTestFactory`, and `GoalRunnerSharedTestFactory`.
- Inject `Clock` at `FeatureTaskRuntimeCompletedUpstreamRepairCheckpoint.kt:86,100`
  and `GoalRunnerAcceptanceCoordinator.kt:43`. Formatting stays UTC ISO-8601 with
  offset, matching today's `OffsetDateTime.now(ZoneOffset.UTC).toString()`.
- Add runtime-engine main to the filter of `RuntimeLayerBoundaryArchitectureTest`
  "application domain and ports avoid direct file IO".
- Extend `PortNullObjectCensus` (`PortNullObjectAbsenceArchitectureTest`) to flag a
  companion `val` null object (`NONE`, `IDLE`, `NOOP`, or `DISABLED` substitute
  of an interface) that no `src/main` file references. Scope the new rule to
  runtime-engine main through a module list, so SKILL-377 subtask 3 can add
  runtime-ports by adding one entry. runtime-ports has eight such objects and
  runtime-application one, and SKILL-377 and SKILL-370 own those. Data-value
  `EMPTY` constants of data classes are values, not substitutes, and the rule
  ignores them. `class` detection is SKILL-377 subtask 3's; do not duplicate it.
- Do not add an engine ambient-clock test or edit its baseline. SKILL-373
  subtask 2 adds engine coverage and drops baseline rows whose call sites are
  gone. After this subtask, the engine sites in that baseline no longer exist.
- The unused `kotlinx-serialization-json` dependency is SKILL-374 subtask 1's.

## Acceptance Criteria

1. The census finds only unrelated "experimental" wording, the SKILL-366
   migration entries, and the drop migration.
2. The four experiment schemas are gone, and no schema path, copy task, or
   contract version references them.
3. A migration test opens a fixture database that has the five experiment
   tables and shows they are dropped and the database is usable.
4. `skill-bill goal --experiments …` and `skill-bill goal preflight --experiments …`
   are rejected as unknown options, `skill-bill experiments` does not exist, and a
   repo or machine config that still contains `experiments` loads.
5. runtime-engine main contains no `java.nio.file.Files`, `kotlin.io.path` IO,
   `Clock.systemUTC()`, `Instant.now`, `OffsetDateTime.now`, or `Random.Default`.
6. The file-IO guard fails on a synthetic runtime-engine main file that calls
   `Files.readString`. The null-object census fails on a synthetic test-only
   companion `val NONE` and passes on the tree.
7. The six named null objects are declared under runtime-engine `src/testFixtures`
   and absent from `src/main`.
8. `runtime-kotlin/agent/decisions.md` records the removal: the reason, what was
   removed, the retained migrations, and the revisit condition (a defined
   experiment with a consumer).

## Non-goals

- Run-loop and goal-runner restructuring (subtasks 2 and 3).
- Raw-map and visibility guard changes (subtask 3).
- Ambient-clock, ambient-environment, and inject-default guard coverage (SKILL-373 subtask 2).
- The engine's unused serialization dependency (SKILL-374 subtask 1).
- Pruning or moving the architecture suite (SKILL-373).
- Changing goal behavior for runs without experiments.

## Dependency notes

Depends on SKILL-368 merging, because SKILL-368 rewrites the governed-resource
entries in `runtime-infra/contracts/build.gradle.kts` that copy the experiment
schemas. It does not wait for SKILL-370. Land it before SKILL-371, SKILL-373,
SKILL-374 subtask 2, SKILL-376 subtask 1, and SKILL-377 subtask 2. Each of those
carries experiment items that this subtask makes moot: 371 pins experiment types
and reworks the `experiments` command, and 376 fixes the pair store's clock.
SKILL-373, SKILL-374, and SKILL-377 have already dropped their experiment items
and defer them to this subtask. SKILL-376 may move the SQLite experiment store.
This subtask owns all experiment code. Delete whatever exists at start, wherever it lives.
SKILL-377 subtask 3 also edits `PortNullObjectAbsenceArchitectureTest`, so
whichever lands second rebases.

## Validation strategy

The regressions to catch are a goal run that breaks without experiments, an old
database that fails to open, a config file with `experiments` that fails to
load, and a timestamp format change at the three clock sites. Cover them with
the existing goal-runner suites, the migration fixture, a config load test, and
the existing checkpoint and acceptance tests with a fixed `Clock`. Run
`./gradlew check`, the engine, CLI, MCP, core, and infra-sqlite suites,
`./install.sh --from-source`, and `skill-bill doctor`. Apply
`bill-unit-test-value-check`.

## Next path

Continue to `spec_subtask_2_feature-task-run-loop-step-classes.md`.

## Spec Path

.feature-specs/SKILL-378-runtime-engine-hexagonal-boundaries/spec_subtask_1_delete-experiments-and-close-inner-layer-gaps.md
