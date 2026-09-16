# SKILL-351 - runtime-domain-boundaries-and-simplicity

## Mode

decomposed

## Intended outcome

Make every durable decode boundary in runtime-domain fail through one typed error, replace the module's duplicated map readers, coercions, and hand-written JSON with the shared codec, put four misplaced responsibilities back with their owner, and shrink the exported surface, while keeping the module graph, the pure dependency direction, and successful wire behaviour unchanged.

## Scope

The investigation covers all 344 production Kotlin files in runtime-domain, its tests and fixtures, its build script, the architecture guards that scan it, and the consumers named per finding. See [investigation.md](investigation.md) for eleven findings, the principles assessment, rejected refactors, public engineering references, the test baseline, the reference-census method, and limits.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-001, F-002, F-003, F-004 | One typed failure per durable decode family, one raw-map reader, shared JSON codec, no silent fallback | 1 |
| F-005, F-008, F-009, F-011 | One port home and shape, owners restored, typed status, artifact keys declared once | 2 |
| F-006, F-007, F-010 | Count-driven splits merged, unused code and dependency removed, mutable state contained | 3 |

Three subtasks ship independently. The first changes how malformed durable input is reported and read. The second moves ownership and typing without changing decode semantics. The third deletes and merges without changing behaviour. Each carries its own tests and documentation. They touch overlapping files, so each subtask rebases on the branch head before it starts.

Prepared in local mode on 2026-09-16. SKILL-351 follows SKILL-350, the highest existing local spec key; the user authorised selecting the next available key. Baseline HEAD is `3f6aaff18318483771900c96b5e7cd854e7e8259` with a clean tree; the sorted production-file digest is `69c26255ac9675da036840d6cf655a0a65aaa347b95ce5ee29afc8f384511083`. This bundle prepares work only; all subtasks start pending.

## Acceptance Criteria

1. Every durable decoder in runtime-domain, including enum `fromWire` companions reached from SQLite rows or workflow artifacts and `init` invariants on decoded values, reports malformed input through its family's typed error. `error()`, `IllegalArgumentException`, `require`, and unguarded `toInt()` no longer escape those seams. Operator-input parsers reached from CLI and MCP keep their current argument-error behaviour.
2. Raw artifact maps are read through one internal reader parameterised only by the family's typed-error factory, with one exact and one documented-lenient integer coercion. The nine accessor families and fourteen coercions named in F-002 are gone. Values that decode today still decode to the same result.
3. Lane segment accounting encodes and decodes through `JsonCodec` with a typed failure. Existing rows, `[]`, and `null` decode unchanged; a `segment_id` containing control characters or a `\u` escape round-trips.
4. The quality-gate selection fallback, version fallback, and `runCatching` probes in F-004 either fail typed or emit a bounded record naming seam, value used, and expected value. `goalObservabilityLatestEventForLiveness` is removed.
5. Validator ports have one documented home, no production logic or no-op default bodies, and named payload types; the seven identical `(Any, String)` ports collapse to one port with a closed artifact-kind vocabulary or each gains a named payload type. Forwarding adapters and fixtures shrink accordingly. Narrative KDoc moves to `agent/decisions.md`.
6. Contract-DTO mapping for learnings lives in its application owner; the version resource read lives in `runtime-core` or its fallback is observable; the wrappers in `WorkflowBoundaryCollections.kt` are relocated to their area or removed; the engine's runtime-specific branch reads a `WorkflowDefinition` field instead of comparing against the concrete feature-task definition. The `engine` ↔ `taskruntime` import cycle is gone.
7. Workflow status, step status, and resume mode travel through the engine's models and definitions as their enums; `wireValue` appears only at the snapshot and acknowledgement wire seams. Snapshot JSON and acknowledgement payloads are byte-identical for supported values.
8. Each durable artifact encode/decode pair references one owning `*Keys` declaration for every governed key, and the governed-seam inventory scans those pairs. Open prose, prompt text, and pack-authored payloads remain open.
9. The canonicalizer, observability-parsing, and other families named in F-006 are merged by responsibility within the current ceilings; the unreachable closed-key branch is either populated from its schema authority with a parity test that can fail, or deleted with its `repoTest`.
10. The 24 unreferenced declarations are deleted after a re-run census, same-file-only declarations are `private`, module-only declarations are `internal`, `TelemetryRemoteStatsRuntime` is removed, and `runtime-domain/build.gradle.kts` no longer declares `kotlinx-serialization-json`. All modules compile and their tests pass.
11. `AttemptLedgerAccumulator` becomes a pure reduction returning the summary; decoders return truncation records in their result instead of appending to a caller's `MutableList`; `InstallTransaction` exposes an immutable list. No public `var` or mutable collection remains on a domain model.
12. Documentation describes the actual port home, wrapper policy, key ownership, and scanner coverage. Existing architecture guards keep their protection with no new baseline rows, exemptions, or suppressions.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles, docs/code-principles.md, docs/observability-policy.md, and AGENTS.md. Keep the eleven Gradle modules, the composition root, the pinned module edges, and the `runtime-domain` → `runtime-contracts` dependency.
- runtime-domain imports no serialization, IO, adapter, port, or application type. Do not add `@Serializable` DTOs, a schema engine, a decoding DSL, a result framework, or a port per artifact field.
- Canonical schemas own wire shape. Adding a `*Keys` declaration does not change a schema; changing accepted semantics follows the version, parity, typed-error, and quarantine rules.
- Preserve successful durable reads and writes byte for byte. Supported values decode to the same result; only malformed input changes its failure identity. Legacy-record quarantine and regeneration paths keep working and gain coverage for the newly typed failures.
- Keep `require` on constructor invariants of typed value classes. Only decode seams translate them.
- Do not split files by count, add `*Helpers`-style siblings under other names, or hide dependencies in parameter bags. Ceilings are 1,200 lines and 40 functions; merge under them.
- Deletion follows a fresh reference census plus compilation and the full runtime-kotlin test suite; the recorded census is evidence, not authority. Names without handwritten call sites may still have generated or reflective consumers.
- Re-read the owning documents and current source hashes before each subtask. Concurrent SKILL-350 work in the shared checkout is outside this spec.

## Non-goals

- Renaming the `FeatureTaskRuntime*` prefix, splitting runtime-domain into per-area modules, or moving validator ports for location alone.
- A rewrite of the workflow engine, review parsing, or goal runner; new product behaviour; changes to phase order, review policy, or telemetry semantics.
- Replacing every `Map<String, Any?>` in the module with typed models in one pass. Subtask 1 fixes the reading seam; typed replacement continues only where a finding names the wrapper.
- Certification against private Reddit, Microsoft, or Meta standards.
- Removing test coverage to improve line counts.

## Validation strategy

Exercise real decoders with malformed durable fixtures and assert the typed error identity, then the quarantine or regeneration path that catches it. Round-trip supported artifacts through `toArtifactMap`/`fromArtifactMap` and snapshot JSON before and after each subtask and diff the bytes. Exercise `JsonCodec`-backed lane accounting with control characters and escapes. For ownership moves, assert the relocated behaviour through its new owner and the absence of the old import edge through the acyclicity scan at sub-area granularity. For the surface reduction, rely on compilation of all modules plus `./gradlew check` on runtime-kotlin rather than reference greps. Run the domain suite, `repoTest`, affected consumer suites (`runtime-application`, `runtime-engine`, `runtime-infra-sqlite`, `runtime-infra-fs`), and the `runtime-core` architecture guards. Use the pack-declared quality gate during implementation and bill-unit-test-value-check for changed tests. The preparation baseline (805 + 2 tests passing) is evidence about `main`, not a future review receipt.

## Next path

Run `skill-bill goal SKILL-351` when implementation is intended. The prepared manifest is the goal runner's input.
