# SKILL-377 - runtime-ports-contract-cleanup

## Mode

decomposed

## Intended Outcome

runtime-ports keeps its module position, its `UnitOfWork` and repository shape, and every port a consumer injects. Its contracts become explicit. The git port is a flat aggregate with typed results. Defaults no longer stand in for implementations. Storage and protocol details move into their adapters. Dead ports, adapters, families, and aliases are gone. The ports declaration guard reads files again. Behavior and CLI/MCP wire output stay the same, and no new module, framework, or scanner class is added.

## Overview

[investigation.md](investigation.md) holds the census, source references, the list of what stays, and the coordination table for sibling specs. Summary:

| Finding | Current state | Delivery |
| --- | --- | --- |
| F-001 | `PortsDeclarationArchitectureTest` and one `PortNullObjectAbsenceArchitectureTest` case resolve `runtime-kotlin/runtime-kotlin/...` and pass without reading a file. | Subtask 3 |
| F-002 | `WorkflowGitOperations` exposes 8 sub-port getters and 23 forwarding extensions. | Subtask 1 |
| F-003 | NUL-joined git payloads decoded in 13 engine/application sites, a payload codec object in ports, and git stderr matching in ports. | Subtask 1 |
| F-004 | 20 default bodies overridden by the only production adapter. They return success, empty, "unsupported", or a substituted operation. | Subtask 1 (git), subtask 2 (rest), subtask 3 (guard rule) |
| F-005 | `WorkflowStateRepository` has a dead feature-implement family, a runtime alias family, and family dispatch plus a SQLite batch size in ports. | Subtask 2 |
| F-006 | Six review preparation "ports" implemented by anonymous constant objects in application. | Subtask 2 |
| F-007 | 7 unconsumed ports, 6 dead adapters, unused legacy planning members, single-adapter helpers in ports, dead declarations, and an unused dependency. | Subtask 3 |
| F-008 | Nullable `UnitOfWork` repositories hide an unrecorded degradation. | Subtask 2 |
| F-009 | `AgentRunLaunchFacts` models exclusive terminations as flags, with `ByteArray` in data-class equality. | Subtask 2 |
| F-010 | Pass-through typealiases and `Any`-typed port members still on the tree. | Subtask 3 |
| F-011 | ARCHITECTURE.md misdescribes path types and the ports module surface. | Subtask 3 |
| F-012 | A throwing `NONE` used as an identity sentinel in production, and test-only `NONE` substitutes in ports main. | Subtask 2 (sentinel), subtask 3 (rest) |
| F-013 | `GoalPlanningPreparationState.fromWireValue` throws `IllegalArgumentException`. | Subtask 2 |

SKILL-358 already ran this investigation on runtime-ports. The investigation's "SKILL-358 landing check" shows what landed. Its guard never read files, so two objects came back the next day, and several of its F-001 and F-004 items are still present. F-005 supersedes one recorded decision (2026-09-06 (c)), and the investigation gives the new evidence. The other SKILL-358 retention decisions stand.

Why three subtasks:

- Subtask 1 changes the git contract. It touches 38 engine and application consumers, the workflow git adapter, and about 20 test doubles, and one implement pass cannot also carry the persistence changes.
- Subtask 2 changes persistence, launcher, and review contracts in disjoint files.
- Subtask 3 is a mechanical delete, rename, and guard change across ports, adapters, core, and docs. It runs last so the repaired guard goes live on a tree the first two subtasks have already cleaned. Each commit builds and stands alone.

Baseline: commit `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b`: 254 production files, 7,351 lines, 571 non-private top-level declarations. The spec mode was resolved through the runtime as local.

Next command: `skill-bill goal SKILL-377`.

## Acceptance Criteria

1. `WorkflowGitOperations` declares no getters and inherits every git capability interface. runtime-ports declares no top-level extension function on `WorkflowGitOperations`.
2. No engine or application main source splits a git result value on `\u0000`. Structured git results are typed. `ReadinessTreeIdentityPayloadCodec`, `recordsNothingToCommit`, and the string-status constructors of `WorkflowGitOperationResult` do not exist.
3. None of the default bodies listed in investigation F-004 remains; the experiment rows go with SKILL-378. Outside `fun interface` declarations, any default left in runtime-ports main is written only in terms of the same interface's members and returns no constant result.
4. `WorkflowStateRepository` exposes family-keyed operations. The feature-implement family, the runtime alias family, the `WorkflowFamily` dispatch extensions, `WORKFLOW_SNAPSHOT_BATCH_SIZE`, `FeatureImplementSessionSummary`, and both `toContract` mappers are gone from runtime-ports.
5. `ReviewPreparationService` receives one preparation-facts value, and the six review preparation interfaces and `ReviewFactPorts` do not exist.
6. `UnitOfWork` declares no nullable repository. `GovernedReviewEvidenceEndpointHandle` has no `unbindListener`, and the inline coverage continuation closes every endpoint it replaces.
7. `AgentRunLaunchFacts` carries one closed termination type in place of `exitStatus`, `timedOut`, `interrupted`, and `spawnFailed`, and holds no `ByteArray` in its data-class properties.
8. The unconsumed ports, bindings, component accessor, dead adapters, and dead declarations listed in investigation F-007 do not exist, and runtime-ports declares no kotlinx.serialization dependency.
9. runtime-ports main declares no typealias and no `Any`-typed member in a port contract.
10. `PortsDeclarationArchitectureTest` and every case of `PortNullObjectAbsenceArchitectureTest` fail on a missing scan root and assert they read at least one file per named root, including each `runtime-infra` module. The ports guard rejects a synthetic constant-result default in a non-`fun` interface, the null-object census flags a synthetic `class Noop…`, and both report no violations on the tree.
11. `runtime-kotlin/ARCHITECTURE.md` and `runtime-kotlin/agent/decisions.md` describe the landed contracts, including `Path` as the port path type and an entry that supersedes decision 2026-09-06 (c).
12. runtime-ports main declares no `NONE` companion that throws, is compared by identity, or has no main-source reference.
13. No runtime-ports wire decoder throws a JDK exception type for an unknown token.

## Constraints

- This bundle runs on the current tree. It does not wait for a subtask of another issue. Do the work in the acceptance criteria here.
- Edit guards where they live. If the suite is still in `src/test`, edit it there.
- If an experiment field, alias, or linked-worktree operation still blocks a criterion, remove it in this bundle.
- If a scanner this bundle's criteria rely on still reads zero files, repair that scanner here.
- Fix every violation this bundle's guards report, including public raw maps when those guards report them.
- Follow `runtime-kotlin/ARCHITECTURE.md` Design Principles and `docs/code-principles.md`: no `//` comments in Kotlin, KDoc only on interfaces, wire keys through `*Keys` owners, and package sibling limits.
- CLI and MCP wire output, persisted bytes, schemas, and contract versions stay unchanged.
- Keep every port that a consumer injects, the `UnitOfWork` shape, the goal-runner store role splits, and every SKILL-358 retention decision except 2026-09-06 (c).
- Add no module, framework, dependency bag, parameter object beyond the one review-preparation value in AC 5, or architecture-test class. The scanner changes are extending the existing interface-default rule, adding `class` detection to the null-object census, and repairing a scanner that this bundle's criteria show is not reading files. Don't expand any baseline.
- Test doubles live in testFixtures and compose with `by` delegation. Name the regression each new or changed test catches.

## Non-Goals

- Moving goal-runner store implementations, or collapsing their role interfaces (SKILL-376).
- Moving `RuntimeContext` override slots (SKILL-373) or `ReviewMetricsDatabasePolicy` (SKILL-370).
- Replacing `java.nio.file.Path` in port signatures, repackaging ports for granularity, or enabling `explicitApi()`.
- Anything under experiment support that this bundle's criteria do not trip over. If a criterion fails because experiment code is still present, remove that code here.
- Typed results for single-scalar git operations.

## SKILL-380 coordination

This bundle does not wait for SKILL-380. Type the git results in the commit_push and checkpoint code where it lives. Keep the learnings resolver that is on the tree.

## Validation Strategy

Implementation runs the runtime-ports, runtime-engine, runtime-application, runtime-cli, runtime-mcp, runtime-infra workflow, sqlite, launcher, and skills test suites, plus the architecture suite. Existing git adapter, goal-runner finalization, workflow CLI and MCP, review preparation, and launcher tests are the behavior baseline. Wire and row fixtures are compared before and after. The validate phase runs the routed pack quality gate. Preparation ran only the four ports-related architecture tests, to confirm F-001.
