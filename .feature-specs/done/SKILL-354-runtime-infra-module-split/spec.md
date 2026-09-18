# SKILL-354 - runtime-infra-module-split

## Mode

decomposed

## Intended outcome

Nest the infrastructure modules under `runtime-infra/` as `:runtime-infra:<name>` and replace `runtime-infra-fs` with five modules cut along its measured dependency edges: `host` (JDK, process, and host-config adapters), `contracts` (schema validators, phase-output engines, validator port implementations), `skills` (author, render, validate, install, register, remove), `launcher` (child processes and agent launching), and `workflow` (git, review evidence, feature-task and decomposition stores, goal planning, validation gate). Rename packages and classpath resource prefixes to match, move governed resource copies into one build-logic convention plugin, delete the ten area source sets, relocate tests and fixtures with the code they exercise, narrow visibility per module, re-record the architecture baselines, and update the catalog, guards, and documents, while keeping the infra to ports, domain, and contracts direction, the `implementation` edges from `runtime-core`, port semantics, packaged resource bytes, git and process observable behaviour, and every passing test's assertion unchanged.

## Scope

The investigation covers the three infrastructure modules, `settings.gradle.kts`, the root and module build scripts, `build-logic/convention`, the architecture tests and baselines in `runtime-core` that name infrastructure modules or paths, the resource-path constants in `runtime-contracts`, and the documents that record module names. See [investigation.md](investigation.md) for nine findings, the placement rules, the consumer census, rejected cuts, public engineering references, and limits.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-002, F-004, F-007 | Three modules nested under `runtime-infra/` with nested-id support in the catalog and guards, governed resource copies as a convention plugin with a data-only list, dead dependency dropped | 1 |
| F-001, F-003, F-005, F-006, F-008, F-009 | `fs` split into host, contracts, skills, launcher, and workflow with renamed packages and resource prefixes, area source sets deleted, tests and fixtures relocated, visibility narrowed per module, baselines and documents updated | 2 |

Two subtasks. The first changes no compilation unit and ships on its own: directories, project paths, one convention plugin, and the tests and documents that name modules. The second cannot be halved: a module split is either complete and compiling or it is not. Each subtask is one commit on the feature branch.

This goal runs after SKILL-353 lands in full. SKILL-353 subtasks 1 and 2 decide where validators, primitives, and process owners live; its subtask 3 dissolves `install/support`, removes the engine test dependency and `friendPaths`, and corrects the scaffold documentation. SKILL-353 no longer clusters the root package, moves the layer proof, or narrows visibility; this goal owns those outcomes (SKILL-353 findings F-001, F-009, F-011), and the 2026-06-12 decision that kept one adapter module is superseded by a decision entry written in subtask 2.

Prepared in local mode on 2026-09-17. SKILL-354 follows SKILL-353, the highest existing local spec key; the user authorised the next available key. Baseline HEAD is `66b39806dff8a9d5461993c575faad615cded32a` with a clean tracked tree. This bundle prepares work only; all subtasks start pending.

## Acceptance Criteria

1. `settings.gradle.kts` includes exactly `runtime-infra:host`, `runtime-infra:contracts`, `runtime-infra:skills`, `runtime-infra:launcher`, `runtime-infra:workflow`, `runtime-infra:http`, and `runtime-infra:sqlite` as infrastructure modules; no `runtime-infra-*` directory or project remains; `RuntimeModuleCatalog.declaredGradleModules`, the settings include list, and the `ARCHITECTURE.md` module list agree.
2. Every production file of the five new modules is under `skillbill.infrastructure.<module>` with the area sub-packages kept; the only cross-module `implementation` edges among them are skills to contracts and host, launcher to skills and host, and workflow to skills, contracts, and host; each new module also depends only on `runtime-ports`, `runtime-domain`, and `runtime-contracts`; `RuntimeModuleCatalog.moduleEdgeExpectations` records exactly these edges and `RuntimeCoreCompositionOnlyTest` pins `runtime-core` to seven infrastructure `implementation` edges and no `api` edge.
3. The ten `infraFs*Area` source sets, their compile tasks, and `verifyInfraFsAreaCompile` are deleted; the package order inside skills (agentaddon, nativeagent, scaffold, install, skillremove, module root) is recorded in `ARCHITECTURE.md` and asserted by an import-direction test in `runtime-core` with an empty violation list.
4. A `skillbill.governed-resources` convention plugin in `build-logic` registers every validate-and-copy task from a data-only list with one message template; host, contracts, and workflow declare their copies through it; `GovernedResourceCopyParityTest` passes against a golden manifest that differs from the current one only in module and resource-path prefixes, and every copied resource is byte-identical to its source.
5. Classpath resources live under `skillbill/infrastructure/contracts/`, `skillbill/contracts/`, `skillbill/infrastructure/host/jvm/`, and `skillbill/review/`; the 31 resource-path literals in `runtime-contracts` and `GateJvmResolver.GUARD_CLASSPATH_RESOURCE` name the new prefixes; no `skillbill/infrastructure/fs` or `skillbill.infrastructure.fs` string remains in `runtime-kotlin`, `../../../docs`, or `AGENTS.md`.
6. Module archives are named `runtime-infra-<name>`; `:runtime-cli:installDist`, `:runtime-mcp:installDist`, and `./gradlew check` on `runtime-kotlin` succeed with the empty `:runtime-infra` parent project present.
7. Every current `test` and `repoTest` file of `runtime-infra-fs` exists in exactly one new module; the shared helpers are `java-test-fixtures` on host and skills; the total test count across the five modules is at least 1,518 in `test` and 180 in `repoTest`; `RuntimeModuleCatalog.testFixturesProjectDependenciesByModule` records the new fixture edges and `RuntimeAdapterDependencyAllowlistTest` passes.
8. After a per-module reference census, every public top-level name with no consumer outside its module is `internal` or deleted, and no `friendPaths` entry exists in any infrastructure module.
9. Each new module has package-cycle, inject-defaults, ambient-clock, and ambient-environment baseline files named with `-` in place of `:`; the sum of ambient-environment rows across the five modules does not exceed the 104 rows of the `runtime-infra-fs` baseline they replace, and the package-cycle baselines are empty.
10. `../../../runtime-kotlin/ARCHITECTURE.md` (module list, graph, Gradle Modules, Package Ownership), `docs/code-principles.md`, `docs/internal-skills-architecture.md`, `docs/skill-source-generation.md`, and `docs/agent/history.md` name the new modules and paths; `runtime-infra-fs/agent/history.md` is relocated to `runtime-infra/agent/history.md`; `runtime-kotlin/agent/decisions.md` records the decision that supersedes the 2026-06-12 single-adapter-module entry with the measured edges as its reason; `RuntimeArchitectureDocumentationTest` passes.
11. No infrastructure module declares `kotlinx-serialization-json` unless a production file in it imports `kotlinx.serialization`.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles, docs/code-principles.md, and AGENTS.md. Keep the infra to ports, domain, and contracts direction; no infrastructure module depends on `runtime-application`, `runtime-engine`, `runtime-core`, `runtime-cli`, or `runtime-mcp` in main sources.
- Cut only along the edges the investigation measured. A module exists because it has its own dependency profile or sits on a measured seam; do not add a module for a folder, and do not merge the five into fewer without a decision entry.
- No behaviour change: port success and failure semantics, git argv and observable results, process deadlines and teardown, packaged resource bytes, and every passing test's observable assertion are unchanged. Moves, renames, build, visibility, baselines, and documents only.
- Respect recorded decisions other than the superseded 2026-06-12 single-module entry: validators reached through domain ports (2026-05-28), external schemas as the source of truth copied at build time (2026-05-29), shrink-only infra ambient baselines (2026-09-03), guard ceilings move by decision not by baseline (2026-09-04).
- No `subprojects {}` or `allprojects {}` configuration in `runtime-infra/`; per-module configuration lives in the module script or a convention plugin.
- Kotlin under `runtime-kotlin` and `build-logic` carries no `//` comments and no non-KDoc block comments; the convention plugin follows the existing `build-logic/convention` patterns and passes `:convention:check`.
- Re-read the owning documents and the branch head before each subtask; SKILL-353 changes the files this goal moves.

## Non-goals

- Changing any adapter's behaviour, any port, any schema, or any pack manifest.
- Renaming the `FileSystem*`, `Jdk*`, or `Git*` prefixes, or renaming `http` and `sqlite` internals.
- One module per area, a `common` or `base` module, or a `runtime-infra` parent that configures children.
- Re-doing SKILL-353 work: the schema loader, typed failures, process owners, agent vocabulary, environment reads, primitive owners, scaffold documentation, substance report, or the engine test dependency.
- Rewriting tests beyond relocation and the fixture wiring relocation requires.
- Changing `../../../install.sh`, the release workflows, or the runtime image beyond the archive-name convention.

## Validation strategy

Name the regression before each test: a project dependency that reintroduces an upward edge, a nested project id the catalog resolves to the wrong directory, a packaged resource whose path changed but whose reader constant did not, a golden manifest regenerated with changed bytes, a public name made `internal` that the generated component constructs, a test file lost in relocation, the empty parent project breaking root `check`. Rely on compilation of all modules, `RuntimeGradleModuleLayeringTest`, `RuntimeAdapterDependencyAllowlistTest`, `RuntimeCoreCompositionOnlyTest`, the new import-direction test, `GovernedResourceCopyParityTest`, `RuntimeCompositionGuardArchitectureTest`, `RuntimeArchitectureDocumentationTest`, the per-module census tests against re-recorded baselines, `:runtime-cli:installDist` and `:runtime-mcp:installDist`, and `./gradlew check` on runtime-kotlin including `repoTest` and `:convention:check`. Compare test counts per source set before and after relocation. Run the pack-declared quality gate and bill-unit-test-value-check for changed tests. The SKILL-353 baseline (1,518 tests) is evidence about `main`, not a future review receipt.

## Next path

Run `skill-bill goal SKILL-354` after SKILL-353 has landed. The prepared manifest is the goal runner's input.
