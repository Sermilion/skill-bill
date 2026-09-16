# SKILL-347 - runtime-application-ownership-and-simplification

## Mode

decomposed

## Intended Outcome

Make runtime-application preserve cancellation, resource lifetime, and durable state ownership, then remove redundant wiring and forwarding without changing its public behavior.

## Overview

The application has a sound module graph, useful ports, and extensive existing behavior tests. The investigation found seven actionable gaps in failure semantics, state ownership, and unnecessary forwarding. See [investigation.md](investigation.md) for source references, rejected refactors, coverage, and primary-source comparisons.

| Finding | Current behavior | Delivery |
| --- | --- | --- |
| F-001 | Provider staging can throw before endpoint cleanup begins | Subtask 1 |
| F-002 | Several optional-result paths swallow cancellation or interruption; telemetry defaults select a concrete JVM implementation housed in runtime-ports | Subtask 1 |
| F-003 | Activity writes use a static cache and silently fail after advancing it | Subtask 2 |
| F-004 | Issue-key continuation loses the parent workflow identity before projection settlement | Subtask 2 |
| F-005 | Update-check parsing keeps per-call flags and errors on the service instance | Subtask 3 |
| F-006 | Review dependency groups duplicate bindings and reconstruct the collaborator graph inside the runner | Subtask 3 |
| F-007 | Dead or forwarding-only telemetry, review, and preparation entry points remain | Subtask 3 |

The three commits can ship independently. Subtask 1 changes cancellation and resource-release contracts. Subtask 2 repairs state ownership across activity publication and durable projection settlement. Subtask 3 removes redundant review wiring and per-call service state after the behavioral contracts are explicit. Each commit includes its own tests and documentation; no commit introduces an unused foundation for a later task.

Current source baseline: commit 2ef12ad7e76b8282a1cfa2bb8cdfd795d3e79c7f on 2026-09-15. The refreshed census covers 173 production Kotlin files and 16,593 lines. Of the original 173 files, 170 retain their recorded hashes. The three changed telemetry files received a fresh source review. The earlier investigation and its six defect probes remain applicable, with the telemetry boundary finding revised below.

This updates the existing SKILL-347 bundle at the user's request. Local spec mode was resolved through the runtime. All three subtasks remain pending; implementation has not started.

Next command: `skill-bill goal SKILL-347`.

## Acceptance Criteria

1. Every successfully bound review evidence endpoint has a cleanup owner before provider staging begins. Staging, launch, cancellation, and teardown failures preserve the primary failure and close the owned endpoint exactly once.
2. Application optional-result boundaries propagate cancellation and interruption. A cancelled spec read, review binding or launch, activity callback, update check, or persistence operation cannot become ordinary absence, UNKNOWN, a fallback value, or an ordinary failed review lane.
3. The existing InterruptSignalPort remains an inward contract. Its concrete JVM implementation lives in an outer adapter module and runtime-core supplies it. Application sync and drain entry points do not import or default to the JVM implementation. Cancellation identity and interruption restoration remain observable through substituted ports and adapter tests.
4. Activity throttling belongs to the correct writer or runtime lifetime and database scope. A failed write does not count as persisted activity, independent writers do not suppress each other, and retained workflow entries have a bounded lifetime. Ordinary activity failures emit a bounded diagnostic independent of the failed write.
5. Decomposition continuation carries the authoritative parent workflow identity with the pending projection. Both workflow-ID and issue-key entry paths record or clear projection failure on that parent after commit. Projection-only retry does not replay the workflow transition or create another child.
6. Update-check response parsing and failure selection use invocation-local values. Concurrent calls on one UpdateCheckService cannot exchange malformed-payload flags or failure reasons, and existing stable, prerelease, ahead-of-release, and UNKNOWN results remain compatible.
7. Review wiring has one authoritative launcher and evidence-reader binding per review graph. ParallelCodeReviewRunner accepts cohesive executable collaborators instead of unwrapping the two all-access boundary groups and building a second graph. Delete unused copied fields and keep extracted implementation details as narrow as real callers allow.
8. Remove the unused TelemetryConfigMutationRuntime and production-unused ReviewCommitSequenceResolver adapter, retarget their useful behavior tests to live owners, and remove TelemetryConfigRuntime forwarding in favor of the existing domain functions. Remove the two production-unused feature preparation aliases while keeping the active injected preparation seam.
9. Existing application behavior tests remain compatible, and new regressions cover the failures named in investigation.md. Update the affected architecture checks without weakening their semantic scope, and document the implemented ownership and its tested limits.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles and docs/code-principles.md. Keep the existing modules, inward dependencies, manifest-driven routing, and runtime-core composition root.
- Preserve wire shapes, typed contract errors, schema validation, evidence authorization and budgets, lease fencing, SQLite authority, compatibility recovery, and runtime-owned goal commits. A required wire change starts in its canonical schema and updates version, keys, parsers, and parity tests together.
- Use existing ports and concrete collaborators. Add no framework, general retry abstraction, event bus, coroutine migration, new module, dependency bag, or cache library. Do not expand architecture baselines or add suppressions.
- Keep the landed SKILL-247 outbox claim, deadline, cancellation, cleanup, and engine helper fixes. Complete interruption adapter ownership without weakening cancellation propagation or stale-owner protection. Reuse InterruptSignalPort; do not add another thread-control interface.
- Name each concrete regression before adding its test. Test observable behavior with the production entry point and a substituted boundary. Keep tests in the same commit as the behavior they protect.
- Preserve the documented SKILL-238 decision to retain InstallAgentService while it adapts request DTOs and defaults. Keep genuine adapter and test-substitute ports even when there is one production implementation.
- This preparation changes only the spec bundle. Implementation updates owning architecture documentation and records material decisions through the established area logs. Skill or renderer changes, if later necessary, follow the authored-source rules and ./install.sh requirement.

## Non-Goals

- A rewrite of runtime-application, a certification against private Reddit standards, or a claim that passing scanners proves universal SOLID compliance.
- New product behavior, changes to review tier selection or bounded remediation, broad public-API renames, blanket identifier wrappers, or closing manifest-owned extension vocabularies.
- Redesigning database transactions, Git continuation, telemetry delivery, or file-bundle recovery beyond the observed findings. Do not move snapshot-dependent work out of its transaction to improve a diagram.
- Deleting meaningful tests, removing contract guards, or restructuring files merely because they are long.

## Validation Strategy

The refresh at 2ef12ad7e76b8282a1cfa2bb8cdfd795d3e79c7f ran the complete runtime-application test task: 592 tests passed. Five selected runtime-core architecture classes ran 68 tests, all passing. The prior application environment-boundary failure no longer reproduces. The remaining concrete JVM implementation and application defaults escape that direct-reference scanner. Six isolated probes reproduced existing defects without changing repository production or test sources. These probes assert the broken baseline, so their passing result is evidence of the defects, not remediation success. See [evidence/validation.md](evidence/validation.md).

Implementation converts the relevant probes into intended-behavior regressions and adds the source-traced projection identity and concurrent update-check cases. Run the application suite, affected engine or adapter tests, and the existing architecture guards for dependencies, package cycles, environment access, wire keys, and public APIs. Run the routed quality gate under the workflow's validation rules after the implementation set is complete. A build-only goal gate still runs only its declared pack commands. Do not claim a full repository quality pass from these preparation checks.
