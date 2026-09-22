# SKILL-378 - Runtime-engine hexagonal boundaries

## Mode

decomposed

## Intended outcome

`runtime-engine` is the long-running half of the application layer: the
feature-task run loop, the goal runner, goal planning, and IDE status. The
2026-09-22 investigation ([investigation.md](investigation.md)) found its
module edges correct and its IO behind ports. It found its internals written
procedurally: stateless objects, 134 parameter bags, public getters feeding
extension files, and hand-built collaborators. On top of that sit an experiment
framework with no experiment, SQLite error text used as control flow, four
in-memory counters for one durable sequence, silent durable reads, 38
re-export aliases, and guards that skip the engine or scan nothing. Two
SKILL-352 criteria did not land as specified.

The outcome is an engine where each responsibility is a class with private
constructor dependencies and per-call parameters. No experiment code exists
anywhere in the runtime. The engine follows the same filesystem, ambient-time,
null-object, raw-map, and visibility rules as runtime-application, and those
guards read its files. Each durable sequence has one allocator. Failures are
typed or recorded, and types are imported from their owners. No new module,
framework, interface hierarchy, DI mechanism, or architecture-test class. No
change to phase order, review policy, checkpoint and commit semantics, or
stored bytes outside the dropped experiment tables and corrected duplicate
sequence numbers.

## Findings

| Finding | Priority | Summary | Subtask |
| --- | --- | --- | --- |
| F-001 | High | Experiment support has no reachable experiment | 1 |
| F-002 | High | Procedural run loop and goal runner: stateless objects, 134 bags, public getters, hand-built collaborators | 2 (feature task), 3 (goal runner) |
| F-003 | High (plausible) | Progress/ledger sequence numbers allocated by independent in-memory counters | 3 |
| F-004 | Medium | `[SQLITE_BUSY]` message text classifies live failures | 2 |
| F-005 | Medium | 265 public types with no outside consumer; visibility guard inert | 3 |
| F-006 | Medium | Four durable reads swallow failures | 3 |
| F-007 | Medium | Goal runner reads through the run loop's write facade | 3 |
| F-008 | Medium | Guards skip the engine; six test-only null objects; three ambient clock reads | 1 |
| F-009 | Low | 38 re-export typealiases | 3 |
| F-010 | Low | Inline wire keys (the unused serialization dependency belongs to SKILL-374) | 2 |

## Acceptance Criteria

1. No runtime-kotlin source, orchestration contract, skill, or doc declares or
   references experiment support, except the retained SKILL-366 migration
   entries and a new drop migration.
2. runtime-engine main performs no filesystem IO and reads no ambient time or
   randomness, and the existing file-IO guard scans runtime-engine. SKILL-373
   subtask 2 owns the ambient-clock guard's engine coverage.
3. runtime-engine main contains no companion null object used only by tests,
   and the null-object census detects that form. SKILL-377 subtask 3 adds `class`
   detection.
4. In `skillbill.engine.featuretask` and `skillbill.engine.goalrunner`, no
   top-level `object` holds run-loop or goal-runner behavior, and no
   `*Args`/`*Inputs`/`*Context` class carries a recorder, gate, validator,
   diagnostics, git, emitter, or ledger collaborator. `RuntimeEngineBoundaryArchitectureTest`
   enforces both.
5. No `@Inject` class in runtime-engine exposes a constructor parameter as a
   non-private property. SKILL-370's inject-property rule in
   `InjectConstructorDefaultsArchitectureTest` covers runtime-engine with an empty
   baseline.
6. A busy SQLite failure reaches the engine as a typed exception, and no engine
   code decides a live failure's disposition from exception message text.
7. Each durable progress, ledger, and observability sequence is allocated by
   its write. Two writers in one run produce distinct, increasing numbers.
8. Goal-runner status, stop reports, repair, and planning liveness read through a
   read-only query, not the run loop's write facade.
9. The four silent reads in investigation F-006 fail typed or emit a bounded
   record.
10. runtime-engine declares no typealias to another module's type, and the
   pinned inbound API lists only engine-declared types.
11. The engine visibility guard counts default-public declarations and passes.
    Public engine types are those another module imports or the runtime-core
    component needs.
12. The raw-map guard scans runtime-engine main and passes.
13. `runtime-kotlin/ARCHITECTURE.md` states the step-class rule and the engine's
    inner-layer rules as current state, and `runtime-kotlin/agent/decisions.md`
    records the experiment removal and the step-class rule superseding the
    2026-09-15 helper-input form and the 2026-09-17 bag census.

## Executable scope

Three subtasks.

1. Delete experiment support and close engine guard gaps
   (`spec_subtask_1_delete-experiments-and-close-inner-layer-gaps.md`).
   It is a feature removal with its own review questions (migration, CLI,
   config, docs), and it runs before most siblings: SKILL-371, 373, 374, 376, and
   377 each carry experiment items that become moot once it lands.
2. Feature-task step classes
   (`spec_subtask_2_feature-task-run-loop-step-classes.md`). `featuretask` holds
   about 26,000 lines, and the run loop alone is 14,532. That is one implement
   pass of semantic-preserving restructuring, and the busy-exception change
   lands with the phase runner it edits.
3. Goal-runner step classes, sequences, reads, and engine surface
   (`spec_subtask_3_goal-runner-step-classes-and-engine-surface.md`). It covers a
   second area of about 8,000 lines, plus the 3,877 lines SKILL-376 moves in. It
   needs subtask 2's facade shape for the read query. The alias, visibility, and
   raw-map work closes the public surface after both restructurings.

Global order across the sibling bundles (investigation, "Coordination with
concurrent bundles"): SKILL-368 → subtask 1 → SKILL-370 → (371.1, 372.1, 373.1,
374, 376.1) → 376.2 → 372.2 → 377.1 → 371.2/371.3, 373.2 → subtask 2 → subtask 3.
The two restructurings land last, so they rebase once onto settled contracts.

## Constraints

- Read `runtime-kotlin/ARCHITECTURE.md` Design Principles and
  `docs/code-principles.md` first. No `//` comments, KDoc only on interfaces,
  1,200-line and 40-function ceilings, package sibling ceilings, and wire keys
  from owning `*Keys` objects.
- No new module, framework, dependency bag, interface without a second
  implementation or test substitute, or architecture-test class. Extend an
  existing scanner only where a criterion needs enforcement. No baseline growth,
  suppression, or exemption.
- Wire and row bytes stay identical, except the dropped experiment tables and
  sequence numbers that were duplicated.
- SQLite migrations are append-only.
- Deletion follows a fresh reference census, compilation of all modules, and
  `./gradlew check`.
- Use a local clone, not a linked worktree, for Spotless.
- Recheck every anchor at the start of each subtask. SKILL-370 through SKILL-377
  edit overlapping files. Where a sibling already removed a named symbol, record
  that the criterion is met for it.

## Non-goals

- Merging or splitting runtime-engine and runtime-application, or changing
  their edge (SKILL-370).
- Domain-rule copies, `WorkflowEngine` construction, and stored-time typing
  (SKILL-372).
- Moving SQLite goal-runner coordination (SKILL-376), and collapsing the
  child-repair and planning-hydrator ports before that lands.
- Git result typing (SKILL-377) and architecture-suite relocation (SKILL-373).
- Deleting engine `fun interface` seams that have test substitutes. Typing
  `DecompositionSubtask.status`. `explicitApi()`. Renaming `FeatureTaskRuntime*`.

## Validation strategy

Each subtask runs `cd runtime-kotlin && ./gradlew check` and the engine, CLI,
MCP, core, and infra-sqlite suites. The existing run-loop and goal-runner suites
over real SQLite are the behavior baseline: phase order, resume parity,
checkpoint identity, commit finalization, status projection. Subtask 1 also runs
`./install.sh --from-source` and `skill-bill doctor` against a database created
before the drop migration. Every extended guard gets a synthetic violation
fixture that must fail. Changed tests go through `bill-unit-test-value-check`,
and the validate phase runs the pack-declared gate. No tests ran during
preparation.

## Next path

```bash
skill-bill goal SKILL-378
```
