# SKILL-247 - runtime-boundary-semantics-and-simplification

## Mode

decomposed

## Intended Outcome

Make runtime ownership hold through slow delivery, cancellation, and failed cleanup, then reduce concrete run-loop and build-script duplication while preserving existing contracts.

## Overview

The runtime has a sound inward module graph and useful existing ports, schema checks, and transactional boundaries. Its remaining weaknesses are inconsistent failure semantics and concentrated helper access. This feature addresses the concrete findings in [investigation.md](investigation.md).

| Finding | Evidence | Delivery |
| --- | --- | --- |
| F-001 | A stale outbox sender clears a newer claim; reproduced against the compiled adapter | Subtask 1 |
| F-002 | Synchronous telemetry HTTP has no explicit deadline; a drain reuses its initial time | Subtask 1 |
| F-003 | autoSyncTelemetry swallows cancellation; reproduced against compiled application code | Subtask 1 |
| F-004 | Failed rollback disappears and exceptional process exits can lose cleanup diagnostics | Subtask 2 |
| F-005 | 122 extension functions across 15 files receive a 17-field shared run-loop context | Subtask 3 |
| F-006 | 32 Copy task registrations repeat resource setup in a 914-line build file | Subtask 4 |

The four subtasks ship independently. They have separate boundary contracts and validation needs: delivery ownership, exceptional teardown, behavior-preserving engine refactoring, and build-resource packaging. Tests belong to each implementation commit. No subtask exists only to add a field or prepare an unused abstraction.

Investigated at commit 45cc6b1cf099f44a9e0822e8721119d352eb4f8f on 2026-09-15. The working tree was clean before preparation. SKILL-238 and SKILL-239 already fixed several earlier findings; preserve those changes. SKILL-236 landed the outbox claim mechanism; subtask 1 closes the observed settlement and cancellation gaps without redoing its delivery-identity work.

The issue key is SKILL-247, as specified by the user.

Next command after spec review: `skill-bill goal SKILL-247`.


## Acceptance Criteria

1. Outbox settlement checks the current claim identity for every accepted, rejected, and unconfirmed result. A stale sender cannot alter another sender's claim, error, attempt count, or acknowledgement. Claims for later batches use current time.
2. Each telemetry HTTP request has a finite connect and response deadline. Cancellation and interruption propagate through manual and automatic sync without consuming a delivery attempt or becoming ordinary absence. Ordinary auto-sync failures emit a bounded diagnostic independent of the failed outbox.
3. Failed SQLite rollback and process teardown retain the primary failure and expose secondary failure evidence. Process cleanup records survive exceptional exits and endpoint diagnostic failure, while normal output capture and cancellation semantics remain intact.
4. The named run-loop helpers use their required inputs directly. Planning-branch and gate/settlement helpers no longer receive the entire run loop or its shared context. Duplicate public collaborator forwarding on FeatureTaskRuntimeRunLoop is removed or narrowed to demonstrated external API needs.
5. One module-local declaration owns governed resource copying in runtime-infra-fs. All current task names, classpath destinations, required inputs, missing-source failures, and runtime resource contents remain compatible.
6. Existing transaction, schema, lease, process lifetime, and resumed-workflow tests remain green. New tests reproduce the stale-owner, cancellation, deadline, exceptional-cleanup, and affected resume regressions through actual boundaries.
7. Update the owning architecture documentation with implemented behavior and its tested limits. Record before/after helper dependency and build resource inventories without claiming universal SOLID or YAGNI compliance.

## Constraints

- Keep the eleven declared modules, current dependency direction, SQLite authority, and manifest-driven packs. Follow runtime-kotlin/ARCHITECTURE.md design principles and docs/code-principles.md.
- Preserve wire contracts, extension maps, typed errors, schema parity, process identity checks, lease fencing, and compatibility recovery. Any required wire change starts in its canonical schema and updates its owning keys, version, and parse tests together.
- Use the existing HTTP, diagnostics, transaction, and Gradle facilities. Add no framework, ORM, connection pool, event bus, general retry library, or service layer.
- Keep authored source and generated output boundaries intact. This spec authorizes no generated artifacts for commit. Run ./install.sh during implementation if renderer, support pointer, or skill source changes require it.
- Name the realistic regression before adding each test. Keep behavior tests with the implementation they validate. Do not expand baselines, add suppressions, or split classes merely to pass size limits.
- Follow the goal runtime commit ownership rules in AGENTS.md. Implementation agents do not issue their own git commit or push commands for goal subtasks.

## Non-Goals

- An internal Reddit standards certification, a rewrite of runtime-kotlin, or a new architecture layer.
- A blanket port deletion, identifier-wrapper migration, conversion to coroutines, or replacement of open extension payloads with a closed model.
- Remote telemetry schema changes, exactly-once delivery claims, performance targets unsupported by measurements, or removal of current recovery safeguards.
- Broad test deletion, repository-wide catch rewriting, and unrelated IDE, CLI product, or platform-pack feature work.

## Validation Strategy

Preparation ran 99 selected existing tests with zero failures and three isolated probes against compiled production code. Implementation must turn the reproduced failures into passing boundary regressions, retain prior recovery coverage, and run the applicable routed checks. See investigation.md and evidence/validation.md for exact scope and limitations.
