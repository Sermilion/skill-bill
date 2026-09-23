# SKILL-371 - runtime-cli-contract-ownership

## Mode

decomposed

## Intended Outcome

runtime-cli's architecture guards actually scan the code they claim to check. The CLI process emits results on stdout and diagnostics on stderr through one failure policy. Every contract the CLI shares with another module (repository identity, the goal-child launch protocol, and the scaffold payload) has exactly one owner. The module graph, public commands, JSON field names, and exit-code families stay as they are, and no new layer or framework is added.

## Overview

This bundle follows SKILL-229 and SKILL-348 and does not repeat them. [investigation.md](investigation.md) holds the census, the source anchors, the principles assessment, the rejected changes, and the public references. [evidence/validation.md](evidence/validation.md) records six reproduced defects and the static checks.

| Finding | Current state | Subtask |
| --- | --- | --- |
| F-001 | Eight architecture tests in six classes scan zero files. Two raw-map functions and 13 unpinned engine imports landed behind them. | 1 |
| F-002 | Typed and I/O errors reach the terminal as stack traces. Usage and diagnostics print to stdout. | 2 |
| F-003 | `experiments` bypasses `CliRunState`: root help is appended on success, and an unknown pair crashes. | 2 |
| F-006 | Goal and featuretask exit codes and text re-parse the JSON map. The goal exit code substring-matches prose. | 2 |
| F-008 | Restated wire tokens, two update-check shapes, three hand-rolled `--format` options, restated state-root defaults. | 2 |
| F-009 | Duplicate deprecated alias tree, seven arbitrary grouping holders, dead helpers, duplicated mappers. | 2 |
| F-004 | Repository identity is derived six times with two meanings. The CLI reimplements an injected port. | 3 |
| F-005 | The goal-child argv and env protocol is written twice and tested nowhere. | 3 |
| F-007 | The scaffold payload parser is duplicated verbatim in CLI and MCP, with divergent `repo_root` semantics. | 3 |

Why three subtasks:

1. **Subtask 1 is a prerequisite with its own commit.** Subtasks 2 and 3 need working guards to prove they did not add boundary violations. Restoring the guards also surfaces violations outside the CLI, which must be fixed in the same commit that turns the guards on. It touches only architecture tests plus the reported fixes.
2. **Subtask 2 ships a coherent change to the CLI process contract** (stdout, stderr, exit codes, presenters) that users can observe. It stays inside runtime-cli, plus one typed reason kind on the engine goal result if needed.
3. **Subtask 3 changes contracts across modules** (ports, infra/host, infra/launcher, engine, sqlite, application, MCP). Its review needs the cross-module reader, and it can land independently of subtask 2.

Sequencing: this bundle runs on the current tree. It does not wait for a subtask of another issue. Subtask 1 turns the guards on and fixes every violation they report, including experiment imports and public raw maps that are still present. Subtask 2 applies the output contract to the commands that exist, including `experiments` when that command is still there. Subtask 3 gives each shared contract one owner at the path that exists now. Recheck every anchor in investigation.md against the tree at start. Inside this bundle, subtask 1 runs first, then subtasks 2 and 3.

Prepared on 2026-09-22 in local spec mode (resolved through `skill-bill config resolve-spec-type`). Census baseline: `11d8615ba`, with 113 production files and 11,705 lines in runtime-cli. All subtasks start pending. This bundle does not authorize implementation by itself.

## Acceptance Criteria

1. Every runtime-core architecture test that names a module source root reads files from that root. A named root that does not exist fails the test, and each affected scanner asserts that it visited at least one file per named root.
2. With the restored guards, runtime-application and runtime-domain declare no public raw-map function, and runtime-cli, runtime-mcp, and runtime-application reference only pinned engine inbound types. The pinned list matches the current inbound surface and no exemption or baseline grows.
3. `CliExecutionResult` carries a stderr channel, and `Main` writes it to the process stderr. Usage errors, typed runtime errors, and command failures print their diagnostic on stderr. stdout carries only the command's requested result.
4. A `SkillBillRuntimeException` and an I/O error on a user-supplied path each produce a one-line diagnostic and a non-zero exit, with no stack trace. When the `experiments` command exists, an absent pair does the same. An unexpected non-typed throwable exits non-zero with a one-line diagnostic and a `RuntimeDiagnostics` record.
5. No production command writes output through Clikt `echo` or directly to process streams. If experiment support still exists, every `experiments` subcommand completes through `CliRunState`, uses `formatOption()`, and never prints root help after its own output.
6. Goal and featuretask exit codes derive from typed result values, not from map lookups or free-text substring matches. Their text renderers take typed presentation models. JSON field names and exit-code numbers are unchanged.
7. The F-008 and F-009 items listed in investigation.md are resolved or deleted as described there, and command names, aliases, and help order stay stable.
8. One production function produces `repo-root-realpath-v1` values. It resolves the enclosing Git top level, and the CLI, engine, and SQLite sites use it. The CLI contains no filesystem walk for repository identity.
9. One `runtime-contracts` owner declares the goal-child command tokens, flags, and env names. The launcher and the CLI both use it, and a test on each side fails if either drops or renames a field.
10. One runtime-application decoder parses the scaffold payload for both CLI and MCP, without accepting a raw map. One application operation owns session id, `repo_root` default, and opt-in external-source registration. `findRepoRoot` no longer exists.
11. `runtime-kotlin/ARCHITECTURE.md` and the runtime-cli area history describe the checks and owners that actually landed.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md` Design Principles and `docs/code-principles.md`. That means no `//` comments in Kotlin, KDoc only on interfaces, wire keys through `*Keys` owners, package sibling limits, and no `relaxed = true` mocks.
- Keep the module graph, `runtime-core` as the only composition root, Clikt, Kotlin-Inject, `CliRunState`, `CliRuntimeContext`, command-area isolation, and every existing port that has test substitutes.
- Preserve command names, aliases, options, exit-code numbers, JSON field names, dry-run behavior, confirmation gates, and goal-continuation mutation refusal. The only intended user-visible changes are the stdout-to-stderr move for diagnostics, the removal of stack traces for typed errors, the removal of appended root help, `release_url` in MCP update-check, MCP honoring an explicit scaffold `repo_root`, and removal of the no-effect `verify-workflow continue --subtask-id` option.
- Do not expand any architecture baseline or exemption. Fix what the restored guards report.
- Add no module, command bus, registry, generic result library, coroutine migration, IPC mechanism, or interface without a second implementation or a test substitute.
- Name the regression each new test catches. Test through `CliRuntime.run` or the real scanner entry point, not through helpers.

## Non-Goals

- The rejected-output use case rename and its line rendering (SKILL-370 F-004).
- Moving feature-task run preparation into the engine, splitting files by size, or a blanket typed-DTO migration of CLI maps.
- Changing exit-code numbers, replacing argv with another child protocol, or consolidating `~/.skill-bill` paths outside runtime-cli.
- Rewriting persisted repository identities or adding a migration for rows written from a subdirectory root.
- Removing `CliRuntimeContext` or `OptionalCallbacks` test seams.

## SKILL-380 coordination

This bundle does not wait for SKILL-380. If `--quality-gate-selection` still prints a usage error on stdout when subtask 2 runs, subtask 2 moves that diagnostic to stderr with the other commands.

## Validation Strategy

Each subtask runs the runtime-cli test suite and the runtime-core architecture tests. Subtask 1 also runs runtime-application and runtime-domain. Subtask 3 also runs runtime-engine, runtime-mcp, runtime-infra launcher, host, and sqlite. New tests reproduce P1–P6 from evidence/validation.md through `CliRuntime.run` and assert the corrected channel, text, and exit code. Each restored scanner gets a synthetic violation fixture that must fail. The validate phase runs the routed pack quality gate. Changed tests go through `bill-unit-test-value-check`. Preparation ran no quality gate.
