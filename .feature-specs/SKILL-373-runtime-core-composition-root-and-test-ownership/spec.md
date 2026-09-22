# SKILL-373 - runtime-core composition root and test ownership

## Mode

decomposed

## Intended outcome

runtime-core stays the single composition root of the hexagonal runtime: one
kotlin-inject component that binds ports to adapters for the CLI and MCP entry
points. After this bundle, its providers bind adapters directly with no parameter
bags or annotation cargo, no infrastructure module hosts a second root, and the
repository architecture suite it carries covers every module, runs whenever the
files it reads change, and checks only invariants the compiler and the module
topology do not already enforce.

Behavior and wire output stay the same. No new module, framework, abstraction,
dependency bag, or architecture-test class.

## Findings

Evidence, census, and feasibility are in [investigation.md](investigation.md).

| Finding | Priority | Summary | Subtask |
| --- | --- | --- | --- |
| F-001 | High | runtime-engine is outside the ambient-clock, ambient-environment, and inject-default rules; SKILL-366 violations merged | 2 |
| F-002 | High | Repository checks run in a cached task that does not declare the files they read | 3 |
| F-003 | Medium | Two hand-listed module deny-lists duplicate the single topology | 2 |
| F-004 | Medium | Five parameter bags work around two missing accessors | 1 |
| F-005 | Medium | 137 `@JvmSynthetic` annotations required by a rule that cannot fail | 1 |
| F-006 | Medium | `ScaffoldStandaloneEntrypoint` is a second composition root in infrastructure main | 1 |
| F-007 | Medium | Prose pins, migration guards, and count pins | 2 |
| F-008 | Medium | Baselines keyed by line number churn on every reformat | 2 |
| F-009 | Medium | Experiment wiring: engine-declared factory, duplicate component, two owners of one path | SKILL-378 subtask 1 (deletes experiment support) |
| F-010 | Low | Nine test files whose subject another module owns; seven orphan test packages | 3 |
| F-011 | Low | Composition inputs and test hooks declared in runtime-ports; one unused hook | 1 |
| F-012 | Low | Bare `String` binding, function binding without substitute, discarded statement, `di` package cycle, three unread accessors | 1 |
| F-013 | Out of scope | CLI use of driven ports (unowned) | - |

The "What stays unchanged" table in the investigation lists every item
considered and kept, with its reason, including SKILL-350's retention decisions.

## Acceptance Criteria

1. runtime-core main declares no parameter-bag class and no `@JvmSynthetic`, and
   no architecture rule requires `@JvmSynthetic`.
2. No runtime-infra main source composes adapters for a caller, and the
   composition guard has no sanctioned-entrypoint list.
3. runtime-engine main does not reference `RuntimeContext`; `RuntimeContext`,
   `TransportContext`, `WorkflowOpsContext`, and `OptionalCallbacks` are declared in
   runtime-core, and every `OptionalCallbacks` field is set by at least one test.
4. The ambient-clock, ambient-environment, and inject-default rules each check
   every module in `settings.gradle.kts`, runtime-engine included, and runtime-engine
   main has no violation beyond the rows in the baseline committed before this
   bundle.
5. Baseline rows do not contain line numbers, and reformatting a file with a
   tolerated site leaves every baseline unchanged.
6. No architecture test reads `ARCHITECTURE.md` or `agent/*.md`, asserts a count
   of rules, or guards a completed migration, and module edges are checked only
   against `RuntimeModuleCatalog`.
7. The architecture suite runs in runtime-core's `repoTest` task, whose declared
   inputs include the runtime-kotlin sources, build files, Markdown, and baselines
   it reads; installer and launcher shell tests run in runtime-cli `repoTest`.
8. runtime-core `src/test` contains only tests that construct the component or
   exercise a service with two or more real adapters, all in `skillbill.di.*`
   packages.
9. `runtime-kotlin/ARCHITECTURE.md`, `AGENTS.md`, and `docs/code-principles.md`
   describe the landed composition rules, suite location, and test placement as
   current state.

## Executable scope

Three subtasks, each one commit that stands alone.

1. Composition root simplification
   (`spec_subtask_1_composition-root-simplification.md`): F-004, F-005, F-006,
   F-011, F-012. Production wiring across runtime-core, runtime-ports,
   runtime-application, runtime-cli, runtime-mcp, and runtime-infra/skills.
2. Guard coverage and pruning (`spec_subtask_2_guard-coverage-and-pruning.md`):
   F-001, F-003, F-007, F-008, in place. Includes any engine fix the restored
   coverage still requires after SKILL-378 subtask 1.
3. Test ownership and declared inputs
   (`spec_subtask_3_test-ownership-and-declared-inputs.md`): F-002, F-010. A
   mechanical move of the pruned suite into `src/repoTest` plus relocations.

Split conditions:
- 1 is a semantic production change reviewed for behavior; 2 changes rules
  and deletes tests, reviewed rule by rule.
- 3 moves about 14,000 lines and would bury 2's semantic diff if combined with it.
- Order: 1 deletes the `@JvmSynthetic` rule and edits the composition-guard
  inventory that 2 prunes; 3 moves what 2 left.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md` Design Principles and
  `docs/code-principles.md`: no `//` comments in Kotlin, KDoc only on interfaces,
  wire keys through `*Keys` owners, package sibling limits.
- No new module, framework, dependency bag, parameter object, or
  architecture-test class. Extend an existing scanner only where a criterion
  needs it (F-001 iteration, F-008 encoding).
- No baseline gains a row. Rows present in the committed baselines before this
  bundle stay tolerated.
- CLI and MCP output, database schema, and every wire contract stay
  byte-identical.
- Keep SKILL-350's retention decisions as listed in the investigation.
- Use a local clone, not a linked worktree, for Spotless.

## Non-goals

- Moving runtime-cli's use of driven ports behind application services (F-013).
- Experiment support and every experiment binding (SKILL-378 subtask 1).
- Renaming runtime-core, replacing `EnvironmentContext` sentinels, adopting
  Konsist or ArchUnit, or narrowing the ambient rules to inner layers.
- Trimming `ARCHITECTURE.md` outside the sections this bundle touches.

## Dependency notes

- Start after SKILL-368 merges, SKILL-378 subtask 1 lands (it runs before
  SKILL-370), and SKILL-370 lands.
  SKILL-378 subtask 1 deletes experiment support, including runtime-core
  `di/experiment/**` and `experimentGoalRunnerFactory` (F-009 here) and the engine
  clock and inject-default sites F-001 found; it also removes the engine's only
  `RuntimeContext` reader, which F-011 needs.
- SKILL-372 subtask 3 passes the version into `SQLiteDatabaseSessionFactory`; the
  typed version value from subtask 1 lives in runtime-ports so that adapter can
  take it. The full cross-bundle order is in the investigation's coordination
  section.
- SKILL-377 subtask 3 deletes `InstalledWorkspaceBaselineStatusPort` with its
  accessor, and several never-injected scaffold and review port bindings in
  runtime-core. Delete only what remains.
- Subtasks 2 and 3 rewrite and move the suite that SKILL-371, SKILL-374,
  SKILL-375, and SKILL-376 edit. Run them after SKILL-371 subtask 1, SKILL-374,
  and SKILL-376 subtask 3 land, and recheck every anchor at start.
- SKILL-376 subtask 2 and this bundle's subtask 1 both edit
  `RuntimeGoalRunnerStoreProvides`; see the coordination table in the
  investigation.
- Record decisions in `runtime-kotlin/agent/decisions.md`: accessors as the
  child-component export list, no `@JvmSynthetic`, one composition root, rules
  iterate the module list, baseline keys, suite location and inputs, test
  placement rule.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check` after each subtask.
- Subtask 1: the composition suites (`RuntimeComponentScopedIdentityTest`,
  `RuntimeComponentInvocationSnapshotTest`, `RuntimeDatabasePathCompositionTest`),
  and the runtime-infra/skills scaffold suites pass; regenerate KSP and confirm `InjectCliComponent` constructs no
  infrastructure type.
- Subtask 2: a synthetic `Clock.systemUTC()` default in a runtime-engine
  `@Inject` class fails the restored rules through their real entry point;
  reformatting a file with a baselined site leaves baselines unchanged.
- Subtask 3: add a `//` comment to a runtime-application main file and run
  `./gradlew :runtime-core:repoTest`; the task executes rather than reporting
  `UP-TO-DATE` or `FROM-CACHE`, and fails. Edit `install.sh` whitespace and confirm
  `:runtime-cli:repoTest` executes. Test totals per module before and after match
  except for tests deleted in subtask 2.
- The validate phase runs the routed pack quality gate.

## References

- [investigation.md](investigation.md)
- `.feature-specs/done/SKILL-350-runtime-core-composition-and-architecture-guards/`
- `runtime-kotlin/runtime-core/src/main/kotlin/skillbill/di/**`
- `runtime-kotlin/runtime-core/src/test/kotlin/skillbill/**`
- `runtime-kotlin/build-logic/convention/src/main/kotlin/dev/skillbill/runtime/buildlogic/RepoTestConventionPlugin.kt`
- `runtime-kotlin/ARCHITECTURE.md`, `runtime-kotlin/agent/decisions.md`, `AGENTS.md`, `docs/code-principles.md`

## Next path

Run `skill-bill goal SKILL-373`.
