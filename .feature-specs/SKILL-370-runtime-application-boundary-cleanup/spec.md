# SKILL-370 - runtime-application-boundary-cleanup

## Mode

decomposed

## Intended Outcome

runtime-application keeps its module graph and ports. Its remaining in-module composition, adapter and vendor-protocol leaks, misplaced domain and engine code and tests, and namespace indirection are removed. Behavior and wire output stay the same, and no new layer is added.

## Overview

[investigation.md](investigation.md) holds the census, source references, and the list of what stays. Summary:

| Finding | Current state | Delivery |
| --- | --- | --- |
| F-001 | `ParallelCodeReviewRunnerComposition` hand-builds the review graph from a 19-field `@Inject` bag. `InstallPlanningPorts` and `InstallReconcilePorts` are port bags. An architecture test pins the bag in place. | Subtask 1 |
| F-002 | `WorkflowService` exposes `goalObservabilityEventValidator`. CLI and MCP each decode goal-observability artifacts with it. | Subtask 1 |
| F-003 | Application retries writes by matching `SQLITE_BUSY` message text. | Subtask 1 |
| F-004 | `RejectedOutputDiagnosticCliSession` formats CLI lines and names a CLI flag. | Subtask 1 |
| F-005 | `AgentActivityStampWriter` reads `System.nanoTime()` directly. | Subtask 1 |
| F-006 | Engine-only helpers live in application. The engine→application edge is `api`. | Subtask 2 |
| F-007 | 14 application test files exercise engine types. Application tests depend on `runtime-engine` and its testFixtures. | Subtask 2 |
| F-008 | 20 typealiases re-export port and domain types, including two aliases for the same `WorkflowFamily`. | Subtask 2 |
| F-009 | Stutter packages (`review.parallel.core.code.review.*`, `review.review`) and 21 test files in orphan packages. | Subtask 2 |
| F-010 | 88 public declarations that only the module uses. `GoalLifecycleTelemetryEmitter.NONE` has no production caller. | Subtask 2 |
| F-011 | `UpdateCheckService` holds the GitHub releases URL, headers, and JSON field mapping, and uses a generic HTTP port. | Subtask 1 |
| F-012 | The decomposition manifest's invariants (parent-status and intent derivation, blocked and retry transitions) live in application, and the engine imports them from there. `normalizedBlockedReason` duplicates a domain function. | Subtask 2 |

Sequencing with concurrent bundles (details in investigation.md, "Coordination with concurrent bundles"): SKILL-371, SKILL-372, SKILL-373, and SKILL-374 all start after this goal and recheck their anchors. F-008 and F-012 pre-satisfy SKILL-372's application alias and blocked-reason items.

Why two subtasks: subtask 1 changes wiring and result shapes, so reviewers need to read it for behavior. Subtask 2 is a large mechanical move-and-rename across application, engine, CLI, MCP, and core. Combining them would bury the semantic diff under hundreds of import changes. Each commit builds and stands on its own. Subtask 2 runs second so it relocates the files that subtask 1 has already simplified.

Baseline: commit `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b`, 179 production files and 18,208 lines. Local spec mode was resolved through the runtime.

Next command: `skill-bill goal SKILL-370`.

## Acceptance Criteria

1. `ParallelCodeReviewRunnerBoundaries`, `ParallelCodeReviewRunnerComposition`, `InstallPlanningPorts`, and `InstallReconcilePorts` no longer exist. Each review collaborator is constructor-injected with only the dependencies it reads, and `ParallelCodeReviewRunner` receives the collaborators it calls.
2. No `@Inject` class in runtime-application exposes a constructor parameter as a non-private property. The existing `InjectConstructorDefaultsArchitectureTest` scanner enforces this with an empty baseline, and the source-shape test that required the composition root is removed.
3. Workflow get and open results carry the decoded goal-observability summary. CLI and MCP no longer read a validator from `WorkflowService` or decode goal-observability artifacts themselves, and their workflow JSON output is unchanged.
4. runtime-application contains no SQLite error-text matching. Busy retry for self-managed writes lives in the SQLite adapter with the current attempt count.
5. The rejected-output diagnostic use case has a transport-neutral name and returns typed metadata and raw bytes. CLI rendering and flag hints live in runtime-cli.
6. `AgentActivityStampWriter` reads monotonic time from an injected `TimeSource`.
7. Engine-only application code (`WorktreeEditJournalWriter`, `topLevelJsonObjectCandidates`, `stderrExcerpt`) lives in runtime-engine, and runtime-engine declares runtime-application as `implementation`.
8. runtime-application test sources don't depend on runtime-engine or its testFixtures. Tests of engine types live in runtime-engine.
9. runtime-application declares no typealiases. Every consumer imports the owning port, domain, or model type.
10. No runtime-application main package repeats a parent segment (`core.code.review`, `review.review`), and every test file sits in a package that exists in main or in its area package.
11. Declarations used only inside runtime-application are `internal` or `private`, and `GoalLifecycleTelemetryEmitter.NONE` lives in test fixtures.
12. runtime-application contains no GitHub URL, GitHub header, or GitHub JSON field name, and does not use `RemoteTransportPort`. A `ReleaseCatalogPort` returns typed releases, implemented in runtime-infra/http, and `UpdateCheckResult` values are unchanged.
13. `withParentStatus`, `intentFor`, `withBlockedSubtask`, and `withRetriedSubtask` are declared in runtime-domain `skillbill.workflow.decomposition`. runtime-application declares no `normalizedBlockedReason`. Path-based layout rules stay in application.
14. `runtime-kotlin/ARCHITECTURE.md`, `RuntimeModuleCatalog`, and the runtime-application area log describe the landed ownership.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md` Design Principles and `docs/code-principles.md`: no `//` comments in Kotlin, KDoc only on interfaces, wire keys through `*Keys` owners, and the package sibling limits.
- Keep the module graph, `runtime-core` as the only composition root, and every port that has test substitutes.
- Keep the SKILL-347 retention decisions: `AgentRunGoalRunnerSubtaskLauncher`, `InstallAgentService`, `RuntimeOwnedPersistenceBoundary`, and the `reviewevidence` diff parser.
- Wire output of every CLI and MCP command stays byte-identical. Don't change schemas or contract versions.
- Add no module, framework, dependency bag, parameter object, or new architecture-test class. The only new interface is `ReleaseCatalogPort` (F-011). Extend existing scanners only where an acceptance criterion says so. Don't expand any baseline.
- Name the regression a test catches before adding it. Move existing tests rather than rewriting them.

## Non-Goals

- Merging runtime-application and runtime-engine, or inverting their edge.
- Splitting `WorkflowService` or any file by size, or moving its three helper classes into DI.
- Replacing shared contract mappers or contract-returning telemetry and system use cases with per-endpoint typed results.
- Replacing `java.nio.file.Path` in application signatures, enabling `explicitApi()`, or introducing identifier wrappers.
- Changing engine `[SQLITE_BUSY]` checks on persisted block-reason text.
- Typealias cleanup outside runtime-application.
- Typing `DecompositionManifest.status` or `CurrentSubtaskIntent.action` as enums, which would change the codec, SQLite, and consumers.
- Inbound use-case interfaces in front of application services.
- The public raw-map accounting function and the vacuous-scanner repair, which SKILL-371 owns. The artifact-accessor migration, which SKILL-372 owns.

## Validation Strategy

Implementation runs the runtime-application, runtime-engine, runtime-cli, runtime-mcp, and runtime-infra sqlite test suites, plus the runtime-core architecture tests for module layering, package cycles, package sibling counts, composition guard, inject-constructor rules, and documentation. Existing review-runner, install, workflow CLI and MCP, and diagnostics tests are the behavior baseline. The validate phase runs the routed pack quality gate. Preparation ran no tests. The bundle was checked with `skill-bill goal preflight SKILL-370` (verdict `new_work`).
