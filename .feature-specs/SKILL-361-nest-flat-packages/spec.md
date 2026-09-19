# SKILL-361 - nest-flat-packages

## Mode

decomposed

## Intended outcome

Every production Kotlin directory in this repository is nested by product area and noun family so a package is not a flat dump of mixed types. Public inputs and results live in area-owned `model` packages. Other types live in feature or noun-family packages under that area. Gradle modules remain the layers (domain, application, engine, infrastructure, entry adapters). The IntelliJ plugin keeps its existing `domain` / `application` / `presentation` / `ui` / `infrastructure` layout. Behaviour, module edges, phase order, and successful wire bytes stay the same.

## Scope

The investigation covers all 1,901 production Kotlin files in `runtime-kotlin`, the 27 in `intellij-plugin`, the architecture guards in `runtime-core`, and Package Ownership in `runtime-kotlin/ARCHITECTURE.md` and `docs/code-principles.md`. See [investigation.md](investigation.md) for seven findings, the sibling-count census, the feature-task target packages, rejected layer-name copies, and limits.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-001, F-006 | `engine.featuretask` nested by noun family; sibling-count and clustering scanners cover the whole production tree; documents and a decision record the ceilings | 1 |
| F-002 | `engine.goalrunner` and `goalrunner.planning` nested by noun family | 2 |
| F-003, F-004, F-005, F-007 | Remaining oversized packages in domain, application, infra, cli, contracts, and the composition/error taxonomies nested or named in a recorded decision; remainder inventory empty | 3 |

Three subtasks. Each is one compile-and-reviewable nesting of a disjoint set of packages. Combining them exceeds one reviewable implement pass because import graphs and architecture inventories are per module family. Subtask 2 starts after the engine feature-task packages and the new scanners exist. Subtask 3 starts after the engine is finished so it does not rewrite engine imports again.

Prepared in local mode on 2026-09-19. SKILL-361 follows SKILL-360, the highest existing local spec key; the user authorised the next available key. Baseline HEAD is `503e25beb2d94340791c18ec5dfb3be1ff5a1c76` with a clean tracked tree; the sorted runtime-kotlin production-file digest is `86adf9c7dc994b393f7a500110e157009588b627032fd092970393912d11c84a`. This bundle prepares work only; all subtasks start pending.

## Acceptance Criteria

1. `skillbill.engine.featuretask` has at most the inbound facade types at the area root. Run-loop, phase, review, persistence, lifecycle, prepare, and runner types live in those noun-family packages. Inputs and results that are not already there live in `featuretask.model`. Matching tests move with the production types. The non-model sibling count in each of those packages is at most 12, and `featuretask.model` is at most 20.
2. `skillbill.engine.goalrunner` and `goalrunner.planning` are nested by noun family under the same ceilings. `goalrunner.model` holds inputs and results. Matching tests move with the production types.
3. Every production package under `runtime-kotlin` and `intellij-plugin` is at or below the sibling ceilings (12 non-model, 20 model), or a recorded decision in `runtime-kotlin/agent/decisions.md` names that package as a homogeneous taxonomy that stays together and why. The remainder inventory in `PrincipleEnforcementInventory` is empty.
4. An architecture test fails a synthetic fixture that exceeds the sibling ceiling and fails a `FeatureTask*` type left loose under a parent that already has a non-generic area child. `packageClusteringSourceRoots` includes application, domain, ports, engine, infra, cli, contracts, core, and `intellij-plugin`.
5. `ARCHITECTURE.md` Package Ownership and `docs/code-principles.md` Module And Package Layout state the ceilings and that mixed-responsibility area roots nest by noun family, without file inventories or this issue key. Existing architecture guards keep their protection with no new baseline rows or suppressions.
6. All modules compile. Package-cycle baselines stay empty or shrink. No type moves across a Gradle module boundary. Successful durable reads and writes and CLI/MCP behaviour are unchanged.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md` design principles, `docs/code-principles.md`, `docs/observability-policy.md`, and AGENTS.md. Keep the current Gradle modules, composition root, pinned inbound API, and module edges.
- Cluster by product area and noun family. Do not add `domain`, `data`, `ui`, or `presentation` packages inside runtime areas; those layers are the modules (and the plugin's existing top-level packages).
- Public inputs and results stay in area-owned `*.model` packages. `@Inject` services and ports do not live under `model`. `model` does not import its parent area package.
- Move and rewrite package declarations and imports only. Do not split files by line count, rename the `FeatureTaskRuntime*` prefix, or change phase order, review policy, telemetry semantics, or wire bytes.
- Kotlin under `runtime-kotlin`, `intellij-plugin`, and `build-logic` carries no `//` comments and no non-KDoc block comments. Wire keys stay in `runtime-contracts`.
- Re-read the owning documents and the branch head before each subtask. Ceilings: 12 sibling production `.kt` files in a non-model package, 20 in a `model` package, 1,200 lines per file. Change a ceiling only with a decision entry.

## Non-goals

- New product behaviour, new modules, new ports, or a new packaging framework.
- Copying Android layer names into `runtime-engine` or `runtime-application`.
- Splitting `runtime-engine` into per-area modules.
- Rewriting run-loop, goal-runner, or review logic while moving files.
- Rewriting tests beyond relocation and import updates the moves require.
- Changing `intellij-plugin` layer names; it already matches the target shape.

## Validation strategy

Name the regression before each test: a type left in a fat parent, a test that still imports the old package, a `model` → parent import cycle, a scanner that ignores engine, a remainder inventory that still lists a finished package, a behaviour change from a move. Rely on compilation of all modules, the new sibling-count and clustering tests, existing package-cycle and layering tests, the engine/domain/application/infra/cli suites that compile against moved types, and `./gradlew check` on runtime-kotlin. Relocate tests with production code and apply bill-unit-test-value-check only to tests whose assertions change. The preparation digest is evidence about `main`, not a future review receipt.

## Next path

Run `skill-bill goal SKILL-361` when implementation is intended. The prepared manifest is the goal runner's input.
