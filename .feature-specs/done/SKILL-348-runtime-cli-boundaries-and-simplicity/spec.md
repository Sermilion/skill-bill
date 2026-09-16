# SKILL-348 - runtime-cli-boundaries-and-simplicity

## Mode

decomposed

## Intended Outcome

Give runtime-cli one authoritative invocation contract and move maintenance policy to its application owners, while removing unused command layers and preserving existing public behavior.

## Overview

The module has a defensible dependency graph and command-area isolation. The investigation found failures at invocation and maintenance boundaries, plus obsolete command generalizations. Keep the existing architecture and repair those owners.

See [investigation.md](investigation.md) for the architecture and SOLID assessment, seven source-anchored findings, public engineering references, rejected refactors, and coverage limits. See [evidence/validation.md](evidence/validation.md) for checks and six defect reproductions.

| Finding | Required outcome | Subtask |
| --- | --- | --- |
| F-001 | Accurate update failure and bounded installer lifetime | 1 |
| F-002 | Application-owned uninstall that propagates cancellation | 1 |
| F-003 | One diagnostic output contract and application transaction owner | 2 |
| F-004 | One repository root throughout each invocation | 2 |
| F-005 | Reject malformed workflow updates before mutation | 2 |
| F-006 | Read stdin only for the parsed command and preserve input text | 2 |
| F-007 | Remove unused command inheritance, parser, and result copies | 1 and 2 |

Two subtasks are justified because maintenance lifecycle changes and invocation contract changes can ship independently. Each includes its own tests and documentation. The deletion work stays with those changes and does not pay for a separate implementation cycle.

Prepared on 2026-09-16 in local spec mode. SKILL-348 follows the highest existing local key, SKILL-347. The CLI census is 114 production Kotlin files and 11,370 lines. The source baseline began at b76359a5a604fb81188bc83a9b5759293e438dc1 while unrelated SKILL-248 work continued in the shared checkout. The evidence inventory records the actual CLI file hashes. All subtasks start pending; this bundle does not authorize implementation by itself.

## Acceptance Criteria

1. Update reports download and execution failures accurately and leaves no owned installer process or drain running after interruption, timeout, or failed output capture.
2. Uninstall cancellation stops later mutations. Application code owns maintenance policy while the CLI owns parsing, prompting, and presentation.
3. CLI invocation inputs own repository selection and stdin consumption. Feature-task discovery, durable identity, resume, and launch use the same resolved request.
4. Diagnostic output is byte exact where requested, completes once, and never acquires trailing root help. Its application entry point owns database and retention work.
5. Malformed workflow step updates fail before durable mutation. Existing successful wire shapes, public commands, aliases, and refusal behavior remain compatible.
6. Remove the six unused workflow generalizations, one code-review inheritance layer, dead prelaunch parser, duplicate installer result, and unused input injection in affected paths. Keep current adapter ports and useful sharing.
7. Existing architecture rules remain enforced without broader baselines. Tests cover the concrete failures described in investigation.md and documentation states the checked scope.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles and docs/code-principles.md. Keep the current module graph, runtime-core composition root, Clikt, Kotlin-Inject, and command-area isolation.
- Preserve command names, aliases, options, exit-code families, JSON field names, dry-run behavior, goal-continuation mutation refusal, and existing confirmation gates. Changes to failure handling must correct the demonstrated failures without silently changing successful commands.
- Keep schema validation, typed errors, contract versions, managed-path checks, cancellation identity, transaction ownership, lease fencing, and diagnostics. Governed wire keys stay with their contract owners. Open presentation maps do not require a blanket schema migration.
- Use existing application areas and adapter ports. Introduce a port only for a demonstrated infrastructure or test-substitution boundary. Add no module, command registry framework, event bus, generic result library, coroutine migration, or interface for each helper.
- Tests must name and reproduce an observable regression through the real command or process boundary. Existing helper-only tests do not prove raw CLI output or process cleanup. Keep tests with their implementation changes.
- This is preparation only. Do not implement, commit, push, launch a goal, or alter the active SKILL-248 work as part of preparing this bundle. Recheck source hashes and current contracts when implementation starts.
- Do not expand architecture baselines or suppressions. Update owning documentation to match the behavior actually implemented. Run the governed quality gate during implementation, with bill-unit-test-value-check for any new or changed tests.

## Non-Goals

- A runtime-cli rewrite or a claim of certification against private Reddit, Microsoft, or Meta engineering standards.
- New CLI product capabilities, replacement of the dependency injection or parsing libraries, and changing goal/review phase orchestration.
- Uninstall transactionality across the filesystem and agent configurations, recovery journals for every command, or speculative rollback of all installer operations.
- Deleting required contract checks, aliases with current consumers, byte validation, permission enforcement, retention behavior, or useful single-adapter ports.
- Optimizing CLI startup or adding caches without a measurement that justifies the change.

## Validation Strategy

Run behavior tests for the named failures, existing CLI compatibility coverage, and the relevant application/infrastructure suites. Preserve the current architecture guard scope and add targeted enforcement only where it tests a real boundary. Use the pack-declared bill-code-check gate during implementation and bill-unit-test-value-check for changed tests. Preparation evidence is recorded separately and is not a future review or quality-gate receipt.
