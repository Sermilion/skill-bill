# SKILL-376 - Runtime-infra hexagonal boundaries

## Mode

decomposed

## Intended outcome

`runtime-infra` is the driven-adapter layer: seven Gradle modules (`host`,
`contracts`, `skills`, `launcher`, `workflow`, `http`, `sqlite`) that implement
`runtime-ports` over the filesystem, processes, HTTP, JSON Schema, and SQLite.
The 2026-09-22 investigation ([investigation.md](investigation.md)) found the
module graph and dependency direction correct. It found ten defects inside the
modules; three are regressions of fixes earlier specs recorded as done (SKILL-353,
SKILL-356).

The outcome:

- Adapters translate between ports and technology and hold no use-case policy.
- One owner manages one-shot process lifetime.
- Each wire shape has one encoder, and time comes from the injected clock.
- Validators fail loudly on unreadable input.
- The infra package-cycle guard checks real edges.
- Packages follow noun families.

No new module, framework, abstraction layer, dependency bag, or
architecture-test class. No change to stored bytes, wire output, or successful
behavior. The only behavior changes: the PR-check runner no longer blocks on
output and now terminates the whole process tree, and repo validation reports
discovery failures.

## Findings

| ID | Priority | Summary | Subtask |
| --- | --- | --- | --- |
| F-001 | P1 | `sqlite.goalrunner` (3,877 lines, 0 SQL) is goal-runner coordination in the adapter, fed by an 11-field bag SKILL-356 was meant to delete | 2 |
| F-002 | P1 | Three process lifetimes; `FileSystemPrCheckProcessRunner` never drains output and kills only the parent | 1 |
| F-003 | P1 | Infra package-cycle guard uses prefix `skillbill.` and can never fail; 8 real cycles hidden | 3 |
| F-004 | P2 | SQLite holds a byte-identical copy of the review-accounting encoder plus a public shadow extension | 1 |
| F-005 | P2 | Lease expiry check reads `System.currentTimeMillis()` | 1 |
| F-006 | P2 | Three repo-validation sites turn discovery exceptions into empty input | 1 |
| F-007 | P2 | `AgentRunProcessRequest` in four forms; single-impl `AgentRunAdapter`; test-only mutable hook in `GhGoalPullRequestPort`; three pass-through typealiases | 1 |
| F-008 | P3 | Nine unused dependency declarations; three backward test edges | 1 |
| F-009 | P3 | 44 main packages and 38 orphan test packages (83 files) fragment noun families | 3 |
| F-010 | P3 | `ARCHITECTURE.md` misstates infra ownership in 20 places | 1 |

## Target design

- Code that reads and writes only through `DatabaseSessionFactory`/`UnitOfWork`
  and other ports is engine code. `runtime-infra/sqlite` holds schema,
  migrations, sessions, repositories, and row codecs.
- `BoundedExternalProcessRunner` owns every one-shot child process: argv,
  environment, working directory, optional stdin bytes, and one output mode
  (capped capture, streamed lines with early stop, or redirect to a file).
  `JvmAgentRunProcessRunner` owns long-running agent processes.
- A data carrier has one shape and is built with named arguments.
- A package is a noun family. A child package exists only when the parent would
  exceed the sibling ceiling. No path segment repeats its parent, and every test
  package matches a production package.

## Acceptance Criteria

1. `skillbill.infrastructure.sqlite.goalrunner` does not exist, and no class
   under `runtime-infra/sqlite/src/main` takes `WorkflowGitOperations`,
   `FeatureTaskRuntimeWorkerSupervisor`, `GoalRunnerChildRepairRunnerPort`,
   `DecompositionManifestProjectionWriter`, or `GoalChildPlanningHydratorPort` as
   a constructor parameter.
2. `WorkflowGoalRunnerOutcomeStoreDependencies` does not exist, and the four
   goal-runner port interfaces keep their signatures.
3. `ProcessBuilder(` appears in `runtime-infra` production sources only in
   `BoundedExternalProcessRunner.kt` and `JvmAgentRunProcessRunner.kt`.
4. `FileSystemPrCheckProcessRunner` returns the child's exit code for a command
   that writes 2 MiB. After a timeout, no descendant of the child is alive.
5. SQLite persists review accounting through one serializer shared with the
   rest of the runtime, and stored accounting bytes equal the baseline.
6. No `runtime-infra` production file reads `System.currentTimeMillis()`.
7. `skill-bill validate` on a fixture with an unreadable native-agent source,
   skill-class file, or pack manifest reports an issue naming that file.
8. `AgentRunProcessRequestDsl`, the forwarding getters on `AgentRunProcessRequest`,
   `AgentRunAdapter`, the mutable field in `GhGoalPullRequestPort`, and the three
   typealiases listed in the investigation do not exist.
9. The infra package-cycle tests use each module's own package root. SQLite
   has no package cycle. The skills baseline lists exactly the
   `nativeagent ↔ scaffold` cycle.
10. No infra package, in main or test, has two adjacent equal path segments.
    Every infra `src/test` package exists in main, and each subtree listed in
    investigation F-009 uses the fewest noun-family packages that keep every
    package at 12 files or fewer.
11. The nine unused declarations and the three backward test edges from F-008 are
    gone.
12. `ARCHITECTURE.md` names the single owning infra module for each infra
    responsibility and lists no deleted type.

## Executable scope

Three subtasks, one commit each. Split conditions:

- **Subtask 2** moves 20 files and about 5,200 test lines across a module
  boundary into runtime-engine. Its review needs the engine, SQLite, and DI
  readers. It follows subtask 1 inside this bundle.
- **Subtask 3** is a large mechanical move. Separating it from behavior
  changes keeps both reviewable. The guard prefix fix lands with it because
  the SQLite cycles close only after subtask 2's move and subtask 3's migration
  moves.
- **Subtask 1** is the semantic change set inside infra and its build files.

1. Process ownership, single owners, and adapter seams
   (`spec_subtask_1_process-ownership-and-adapter-seams.md`).
2. Goal-runner coordination moves to the engine
   (`spec_subtask_2_goal-runner-coordination-to-engine.md`), after 1.
3. Package layout and a live package-cycle guard
   (`spec_subtask_3_package-layout.md`), after 2.

## Dependency notes

- Read `../../../runtime-kotlin/ARCHITECTURE.md` Design Principles and `docs/code-principles.md`
  first. Infra Kotlin is subject to `CommentAndInterfaceKdocArchitectureTest`,
  `InlineFqnArchitectureTest`, the production line ceiling, and the package
  sibling ceiling.
- This bundle runs on the current tree. It does not wait for a subtask of another issue.
  Do the work in the acceptance criteria here. If another bundle already moved or
  deleted a cited file, use the path that exists.
- Record decisions in `../../../runtime-kotlin/agent/decisions.md`: adapters hold no
  port-only coordination; one-shot process ownership; the infra package-cycle
  prefix and the recorded `nativeagent ↔ scaffold` cycle; package layout rule.
- Use a local clone, not a linked worktree, for Spotless.

## Non-goals

- Splitting, merging, or renaming infra modules, or changing production module
  edges.
- Changing `JvmAgentRunProcessRunner`, its probes, or idle policies.
- Moving `ContentDigest`, promoting `skills.scaffold.platformpack`, or breaking
  the `nativeagent ↔ scaffold` cycle (considered and rejected; see
  investigation).
- Collapsing goal-runner port interfaces (SKILL-378 follow-up).
- Reconciling the 16 diverged engine/application/SQLite twins (SKILL-372
  subtask 2). Subtask 2 here only deletes identical twins.
- The five core bags and `ScaffoldStandaloneEntrypoint` (SKILL-373), busy-error
  typing (SKILL-370), guard missing-root fixes and the engine pinned list
  (SKILL-371), experiment deletion (SKILL-378).
- New architecture-test classes or baseline rows beyond the one true cycle row
  in F-003. The only scanner change is adding `System.currentTimeMillis` to the
  existing ambient-clock forms, with zero rows (F-005).
- SQLite schema, migrations' effects, stored bytes, or port signatures.

## Validation strategy

- Each subtask runs the test suites of every module it touches plus the
  architecture suite (runtime-core `test`, or its SKILL-373 location).
- Subtask 1 adds:
  - a PR-check test that writes 2 MiB and asserts the real exit code within
    seconds
  - a PR-check timeout test that finds no surviving descendant
  - a `WorkflowGitOperations` test for stdin-fed commands, bounded diff reads
    with early stop, and timeout
  - a lease test on a fixed `Clock`
  - one validate fixture per swallow site
- Subtask 2 keeps the moved goal-runner suites green. Crash reconcile,
  stale-block displacement, scoped replan, lease, and child-repair tests must
  fail when their branch is reverted.
- Subtask 3 is move-only apart from the prefix line. Equal test counts before and
  after; `git diff -M50% --stat` shows renames with package and import edits.
- The validate phase runs the routed pack quality gate. Changed tests go through
  `bill-unit-test-value-check`.

## References

- [investigation.md](investigation.md)
- `runtime-kotlin/runtime-infra/*/build.gradle.kts`,
  `runtime-kotlin/runtime-{application,engine}/build.gradle.kts`
- `runtime-kotlin/runtime-core/src/test/kotlin/skillbill/architecture/PrincipleEnforcementInventory.kt`
- `.feature-specs/done/SKILL-353-*`, `SKILL-354-*`, `SKILL-356-*`, `SKILL-361-*`
- `.feature-specs/SKILL-370-*` … `SKILL-378-*`

## SKILL-380 coordination

This bundle does not wait for SKILL-380.

## Next path

Run `skill-bill goal SKILL-376`.
