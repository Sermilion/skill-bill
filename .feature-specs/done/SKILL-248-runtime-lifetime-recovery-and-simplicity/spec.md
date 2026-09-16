# SKILL-248 - runtime-lifetime-recovery-and-simplicity

## Mode

decomposed

## Intended Outcome

Close the demonstrated resource-lifetime and recovery-validation gaps in runtime-kotlin, then remove redundant persistence forwarding and readiness work while preserving the existing architecture.

## Overview

The runtime already has a defensible hexagonal architecture. Keep its module graph, composition root, schema boundaries, and transactional model. The whole-tree investigation found four behavioral gaps and two small persistence simplifications. It does not justify an architectural rewrite.

See [investigation.md](investigation.md) for the architectural assessment, six source-anchored findings, rejected refactors, public engineering references, and coverage limits. See [evidence/validation.md](evidence/validation.md) for commands and defect reproductions.

| Finding | Required change | Subtask |
| --- | --- | --- |
| F-001 | Establish goal lease cleanup before heartbeat and hook acquisition | 1 |
| F-002 | Bound Git input, process, and output lifetimes | 2 |
| F-003 | Validate journal content and cleanup ownership before recovery | 3 |
| F-004 | Preserve goal callback cancellation and distinguish failed watermark reads | 1 |
| F-005 | Remove the forwarding-only SQLite progress bridge | 3 |
| F-006 | Reuse already-read database identity observations | 3 |

The three subtasks ship independently. Each closes an observable boundary and carries its own tests. Goal supervision, Git process I/O, and durable recovery have separate failure mechanisms. The small SQLite simplifications share the durable-adapter pass instead of becoming separate subtasks.

Investigated at b4e72a95fc33f95a42367d21b883ce4c5aa1f295 on 2026-09-16. The census contains 1,866 production Kotlin files and 170,041 lines across eleven declared modules. Automated source checks cover all declared module roots; manual investigation traces selected high-risk paths and does not claim line-by-line review of every file.

Issue key SKILL-248 is the user's explicit assignment. Spec source mode is local. This preparation creates no implementation, workflow, commit, push, or external issue.

Next command: `skill-bill goal SKILL-248`.

## Acceptance Criteria

1. Every acquired goal lease, heartbeat, and shutdown hook has one cleanup owner across startup, normal completion, cancellation, and failure. Cleanup preserves the primary failure and attempts all owned releases.
2. Git process execution has bounded input, wait, drain, and cleanup lifetimes. Interruption cannot leave owned commands running or return unfinished capture as success.
3. Manifest-journal recovery validates contract shape, transaction-owned paths, and content digests before destructive operations. Invalid recovery data cannot overwrite a target or delete an unrelated directory.
4. Goal progress and ledger paths preserve cooperative cancellation and interruption. Failed authoritative watermark reads cannot silently become an empty history, and optional failures emit bounded diagnostics.
5. The forwarding-only SQLite progress bridge and redundant private progress contracts are removed in favor of the existing ports and behavior owner. Database readiness compares already-read identity values instead of rereading them.
6. Existing dependency direction, transactions, lease fencing, wire contracts, manifest-driven behavior, and landed ownership repairs remain intact. Regression tests protect the observable failures identified in investigation.md.
7. Owning architecture documentation describes the implemented boundaries and tested limits. No new framework, layer, dependency bag, architecture exemption, or speculative extension point is introduced.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles and docs/code-principles.md. Keep the eleven modules, inward dependencies, existing ports, and runtime-core composition root.
- Preserve SQLite authority, workflow transactions, process identity checks, lease fencing, governed schemas, typed failures, manifest-driven packs, open extension vocabularies, and runtime-owned goal commits.
- Keep the landed SKILL-238, SKILL-239, SKILL-247, and SKILL-347 repairs. Do not reproduce resolved findings or expand architecture baselines.
- Add no workflow framework, event bus, coroutine migration, generic resource-manager framework, connection pool, cache library, or new module. Prefer local ownership and existing capabilities.
- Any new persisted contract starts with a canonical Draft 2020-12 YAML schema, owning Kotlin version and keys, typed parse errors, and parity tests. Preserve recoverable historical journal records through an explicit compatibility path.
- Name the concrete regression before adding each test. Test behavior through production entry points with controlled failures. Retain meaningful contract, transaction, recovery, and process tests.
- This bundle authorizes future implementation through the goal runtime. Preparation changes only specification artifacts. Do not commit generated support files. Run ./install.sh only if implementation changes authored skills, renderer behavior, or support generation.

## Non-Goals

- A runtime rewrite, a private Reddit standards certification, or a claim that passing architecture scanners proves universal SOLID compliance.
- Changing review tiers, phase ordering, goal commit ownership, public command behavior, or the existing expired-lease takeover policy.
- Blanket interface deletion, identifier wrappers, package moves, dependency replacement, or file splitting to meet a numerical target.
- A repository-wide exception rewrite, broad test deletion, speculative scalability work, or throughput claims without measurements.

## Validation Strategy

Preparation ran 394 existing tests with no failures or skips, including 248 architecture tests. Nine isolated probes reproduced current defects against production code. Passing those nine probes demonstrates broken baseline behavior, not remediation. See evidence/validation.md for exact classes, command filters, source hashes, and scope. Implementation must reverse the relevant defect expectations, add the named failure-path regressions, and run affected tests and the applicable routed quality gate. This investigation did not run the full repository quality gate or certify every execution path. Build-only goal validation remains limited to the pack-declared build commands.
