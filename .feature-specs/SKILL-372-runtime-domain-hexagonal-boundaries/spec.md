# SKILL-372 - runtime-domain-hexagonal-boundaries

## Mode

decomposed

## Intended outcome

- `WorkflowStateSnapshot` becomes a typed domain aggregate: decoded once and strictly at the port mapping, and always enforcing its own update rules.
- Each durable workflow artifact gets one domain owner that other modules must go through.
- JSON-schema validation moves out of domain rules into the adapter seams.
- Rules now copied across engine, application, and SQLite move into domain where they compile.
- Integer coercion is unified as SKILL-351 intended. Time values and the skill-kind vocabulary become typed.
- Domain packages become acyclic at real package granularity, with the existing cycle guard able to see it.
- Alias, package, resource, and surface debt is removed.

The module graph, dependency direction, stored bytes, and successful behaviour stay unchanged.

## Scope

[investigation.md](investigation.md) holds:

- the census tables and guard-validity table
- the SKILL-351 landing check
- the 13-item checklist
- fourteen findings
- the over-engineering register
- the What stays unchanged list
- coordination with SKILL-370, 371, 373, 374, 375, 376, 377, and 378

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-001, F-002, F-003, F-009 | Typed aggregate with `Instant` time. One strict decode and encode at the port mapping. Always-on update validation. Snapshot schema validation at the write seam. | 1 |
| F-005, F-006, F-007, F-008 | One domain owner per artifact with `internal` keys. Copied rules in domain and duplicate shells collapsed. Validators called at adapters, ports in `runtime-ports`, forwarding layers deleted. One exact and one lenient integer coercion. | 2 |
| F-004, F-010, F-011, F-012, F-013, F-014 | Acyclic packages with an exact-granularity scan. Skill-kind enum. Aliases removed, packages flattened, test packages aligned. Dead code and resource deleted, visibility narrowed. | 3 |

**Why three subtasks:**

- Subtask 1 changes the aggregate shape that subtask 2's accessors and validation moves build on.
- Subtask 2 collapses duplicate shells wherever they live on the current tree.
- Subtask 3 is mostly mechanical package moves and renames across many files. Keeping it separate keeps the semantic diffs of subtasks 1 and 2 reviewable.

Each commit builds and stands alone.

This bundle runs on the current tree. It does not wait for a subtask of another issue. Do the work in the acceptance criteria here. If another bundle already changed a name or moved a file, use what is there.

Prepared in local mode on 2026-09-22. The key was rechecked after `git fetch` against `.feature-specs/`, `.feature-specs/done/`, `git branch -a`, and `git log --all`; SKILL-372 is held only by this bundle. Baseline HEAD `dbf9f4830a019441f94eb7d04f7fbb6402aa5f8b`, runtime-domain digest `b7799fd116e4ffd9a8533d85861d8a3df44564d1188c10de04cb2f4107c727dd`, 844 domain tests passing. This bundle prepares work only; all subtasks start pending.

## Acceptance Criteria

1. `WorkflowStateSnapshot` exposes `steps: List<WorkflowStepState>`, `artifacts: DurableWorkflowArtifacts`, `mode: FeatureTaskWorkflowMode?`, and `Instant?` timestamps. It has no `stepsJson` or `artifactsJson` field and no `""` timestamp sentinel. No domain production file parses or serialises workflow steps or artifacts JSON.
2. `WorkflowStateRecord.toSnapshot()` decodes strictly and raises `InvalidWorkflowStateSchemaError` for malformed JSON, a wrong root, or a malformed entry. `toRecord()` reproduces today's column bytes and timestamp text for every supported snapshot.
3. No production path turns unparseable workflow JSON into an empty map. `DurableWorkflowArtifacts.fromJson`, SQLite `decodeArtifacts`, `decodeWorkflowArtifacts`, and `artifactsFrom`/`artifactsFromJson` do not exist.
4. `WorkflowEngine.openRecord` and `updateRecord` always run the existing open and update validation and raise the typed workflow-state error. The validation entry point is not public, and the rules are unchanged.
5. Every domain timestamp that domain or a consumer compares, orders, or does arithmetic on is `Instant`, and consumers no longer re-parse domain timestamp strings.
6. Each artifact family read or written outside domain has a typed domain accessor built on its existing decoder and encoder. Domain `*_ARTIFACT_KEY` constants are `internal`. No main source outside runtime-domain indexes the artifact map with a domain key or a literal key.
7. The goal-continuation artifact decodes only through `FeatureTaskRuntimeGoalContinuationArtifact`. A malformed artifact raises the typed error on every path that previously returned `null`.
8. The pure rules duplicated across the file pairs in investigation F-006 live once in runtime-domain, and no two production classes in runtime-kotlin carry the same duplicated rule body.
9. No runtime-domain function takes a schema-validator port. The validator interfaces live in `runtime-ports` with no `Any`-typed member and no constant-result default body. The forwarding `validateX` extensions and `Noop*` validator fixtures are gone, and every schema rejection test still rejects at an adapter or application seam. `WorkflowEngine` has no constructor parameters.
10. Durable integer decoding in runtime-domain uses one exact coercion and one documented lenient coercion. A non-integral number on a formerly truncating path raises the family's typed error.
11. runtime-domain has no package cycle at exact package granularity, and no `model` package imports a non-model package. The existing acyclicity scanner offers an opt-in exact-package mode with cycle detection of any length. runtime-domain uses it against an empty baseline, and other modules' scans and baselines are unchanged.
12. Skill kind is an enum with one `fromWire`. No main source outside runtime-domain restates a skill-kind literal except CLI user-alias input.
13. No runtime-domain typealias and no engine alias of a domain type remain. The F-012 package merges are done, orphan test packages are aligned, and no two runtime-domain production files share a basename.
14. The unreferenced declarations from a re-run census are deleted (domain `normalizedBlockedReason` stays as the single copy). Same-file-only and module-only public declarations are narrowed. The path-filter catch is narrowed.
15. runtime-domain has no `version.properties` resource or `processResources` block, and infra-sqlite has no version resource or reader of its own.
16. `agent/decisions.md` records decisions superseding the 2026-09-06 duplicated-cluster entry and decision (a) of the 2026-09-16 runtime-domain entry. `ARCHITECTURE.md` describes the aggregate, the decode and validation seams, and the cycle scan as implemented.

## Constraints

- Follow `runtime-kotlin/ARCHITECTURE.md` Design Principles, `docs/code-principles.md`, `docs/observability-policy.md`, and `AGENTS.md`.
- Keep the Gradle modules, pinned edges, the `api` re-exports, and the `runtime-domain` → `runtime-contracts` edge.
- runtime-domain imports no `java.nio`, `skillbill.ports`, application, engine, adapter, or serialization type.
- No new modules, frameworks, dependency bags, or architecture-test classes. The only guard change is exact-granularity cycle detection in the existing scanner. No baseline rows, exemptions, or suppressions are added.
- Wire output stays byte-identical: SQLite columns, snapshot, step and acknowledgement JSON, artifact payloads, timestamp text, and skill-kind strings. Only malformed input changes behaviour, and it now fails typed and reaches the existing quarantine and regeneration paths.
- Accessors return typed values, so the raw-map guard SKILL-371 restores stays at zero violations. Validators moved into ports satisfy SKILL-377's no-`Any`-member and no-constant-default rules.
- Keep today's validation rules. Do not invent transition rules or add named transition methods.
- Transaction ownership stays where it is; only pure rules move.
- Deletion authority is compilation plus the full runtime-kotlin suite, not the recorded census.
- Before each subtask, re-read owning documents and recheck anchors against the current tree. Edit the files this bundle's criteria require, including files another spec also mentions.

## Non-goals

- Items listed in the investigation's What stays unchanged table.
- The scaffold use case hosted in `runtime-infra/skills` (unowned follow-up).
- Moving the SQLite goal-runner stores (SKILL-376).
- Application typealiases and decomposition invariants (SKILL-370).
- Raw-map and domain-rule guard path repair, and repository identity (SKILL-371).
- The acyclicity test's class and baseline restructuring (SKILL-373).
- Contracts moves (SKILL-374).
- Exact-granularity cycle baselines for other modules.
- Certification against private Reddit, Microsoft, or Meta standards.

## Validation strategy

- Capture row, wire, artifact, and timestamp byte fixtures across workflow families before each subtask, and diff after.
- Assert typed failures for malformed JSON on resume, status, and goal-progress reads.
- Send invalid updates through an engine writer and a SQLite (or post-376 engine) writer.
- Test each accessor with valid and malformed fixtures, and one real caller per consuming module.
- Move each schema rejection test to the seam that now validates.
- Run the corrected cycle scanner on the real tree and on a synthetic three-package cycle.
- Run the runtime-domain, runtime-ports, runtime-application, runtime-engine, infra suites, runtime-cli, runtime-mcp, and runtime-core architecture suites.
- Use the pack-declared quality gate during implementation and `bill-unit-test-value-check` on changed tests.

## SKILL-380 coordination

This bundle does not wait for SKILL-380. SKILL-380 reads the frozen profile field through the accessor this bundle adds when that accessor exists, and through the current artifact API when it does not.

## Next path

Run `skill-bill goal SKILL-372` when implementation is intended.
