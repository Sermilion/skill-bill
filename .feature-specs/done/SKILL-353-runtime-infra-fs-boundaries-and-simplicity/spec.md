# SKILL-353 - runtime-infra-fs-boundaries-and-simplicity

## Mode

decomposed

## Intended outcome

Replace the twenty copies of the schema-loading skeleton and the fifteen forwarding validator adapters with one loader and validators that implement their ports, route every child process through one of the existing lifetime owners, declare the supported agents once, resolve home and environment once from the injected context, write atomic-file, digest, and containment primitives once, report every fallback through the diagnostics port or a typed failure, correct the scaffold documentation, settle the substance report's home, and move adapter tests off the engine, while keeping the module graph, the composition surface `runtime-core` consumes, port semantics, packaged schema resources, git and process observable behaviour, and passing tests unchanged.

## Scope

The investigation covers all 408 production Kotlin files in runtime-infra-fs, its 209 test files and 33 `repoTest` files, its build script, the architecture guards and baselines that scan it, the recorded decisions that govern its shape, and the consumers named per finding. See [investigation.md](investigation.md) for twelve findings, the principles assessment, rejected refactors, public engineering references, the test baseline, the census methods, and limits.

| Findings | Outcome | Subtask |
| --- | --- | --- |
| F-002, F-007, F-008 | One schema loader, validators implement their ports, typed failures at durable and external seams, every fallback recorded through one channel | 1 |
| F-003, F-004, F-005, F-006 | One cleanup owner per process, one agent vocabulary, one home and environment seam, one owner per filesystem primitive | 2 |
| F-010, F-012 | Documentation corrected, substance report settled, adapter tests off the engine | 3 |
| F-001, F-009, F-011 | Deferred to SKILL-354, which splits the module into Gradle modules along its measured edges after this goal lands | — |

Three subtasks ship independently. The first changes how validation and failures are shaped without changing which inputs pass. The second changes who owns processes, vocabularies, environment reads, and primitives without changing what is written to disk or launched. The third corrects documentation, settles the substance report, and moves adapter tests off the engine without changing behaviour. Subtasks 1 and 2 touch disjoint concerns but share the root package; each rebases on the branch head before it starts. Subtask 3 edits the build script's test dependencies and documents that 1 and 2 touch, so it runs after both land. Root clustering, the layer-order proof, and visibility narrowing (F-001, F-009, F-011) belong to SKILL-354, which splits the module into Gradle modules along its measured edges once this goal has landed; doing them here first would redo the work.

Prepared in local mode on 2026-09-16. SKILL-353 follows SKILL-352, the highest existing local spec key; the user authorised selecting the next available key. Baseline HEAD is `66b39806dff8a9d5461993c575faad615cded32a` with a clean working tree; the sorted production-file digest is `c2f276daf6061adb829cfc4434a17145f60a2312662b52efb79d523bd0920c53`. This bundle prepares work only; all subtasks start pending.

## Acceptance Criteria

1. One internal schema loader under `contracts/` compiles a Draft 2020-12 schema once from a classpath resource, asserts `$id` and `contract_version.const` against the `runtime-contracts` constants, and returns ordered violations with dotted field path and offending value. Every `*SchemaValidator` in the module uses it; no validator declares its own `JsonSchemaFactory`, `ObjectMapper`, `by lazy` schema, identity assertion, or three-way load `catch`. `Path.of("")` working-directory schema fallbacks are zero and the ambient-environment baseline shrinks by at least those 17 rows.
2. The fifteen forwarding `*ValidatorAdapter` and `*ValidatorInfraAdapter` classes are deleted; each validator implements its domain or port interface directly and is bound in `RuntimeValidatorProvides`. `DecompositionManifestValidatorAdapter` and `FeatureTaskRuntimePhaseOutputValidatorAdapter` keep their adaptation logic under whatever name the area uses. Domain and application still reach validation only through the ports named in `ARCHITECTURE.md`.
3. Non-object input to a validator, evidence-endpoint request decoding, link-inventory decode, staging manifest decode, and agent-id parsing raise typed errors in the `skillbill.error` taxonomy; the 45 `catch (…: IllegalArgumentException|IllegalStateException)` recoveries at those seams catch the typed error instead; `require` remains only on constructor invariants of value types; no module exception extends `IllegalStateException`. Tests that pinned `IllegalStateException` or `IllegalArgumentException` at changed seams pin the typed error.
4. Each of the `runCatching` fallbacks the investigation lists is a typed failure, a diagnostics-port record naming seam, expected value, and used value, or a documented legitimate absence; the module has one degradation channel, with any remaining `java.util.logging` site recorded with its reason in `ARCHITECTURE.md`.
5. Every `ProcessBuilder` site in the module runs through `invokeGitProcess`, `ProcessRunLifetime`, or one bounded runner shared with the installer adapter; `GitWorkflowSelectedDiff` has no private deadline, drain thread, or teardown; `GeneratedArtifactGuardReport` has a deadline and teardown; `GitProcessLifetimeBehaviorTest` or its sibling covers each migrated caller for timeout and interruption.
6. One domain enum with `wireValue` and `fromWire` declares the supported agents; `InstallAgent`, `AgentSymlinkProvider`, and `NativeAgentProvider` are that enum or typed views of it; `AgentAddonAgentIds` is deleted; the fifteen `when (provider)` sites read per-agent facts from one declaration; `parseEmbeddedLogicalName` exists once. Provider-specific command builders and output decoders remain.
7. Adapters resolve `userHome` and `environment` from the injected `EnvironmentContext` or `HostPlatformPort`; `"user.home"` is read in at most one production file in the module; `install/plan/InstallPrimitives.kt` selects config roots from passed facts, not ambient reads; the recorder confirms the ambient-environment baseline shrank and no row was added.
8. Atomic write, atomic move, directory replacement, SHA-256 digest, and path containment each have one internal owner in the module and the eight named copies are deleted; rollback stays per transaction owner but each uses the shared primitives; `discover*` walkers over the same tree with the same predicate are merged where the census names them.
9. `ARCHITECTURE.md` describes `ScaffoldGateway` as a typed port consumed by the CLI and inventories the adapter-internal raw maps under `scaffold/`; the substance report is either a `skill-bill` CLI command over the catalog gateway or deleted with its Gradle task, with the decision recorded.
10. The `runtime-engine` test dependency and the `friendPaths` entry are removed from the test source set, with engine-dependent tests relocated to the module whose behaviour they prove and application types reaching this module's tests only through `runtime-ports` test fixtures or public application API; all modules compile and their tests pass.

## Constraints

- Follow runtime-kotlin/ARCHITECTURE.md design principles, docs/code-principles.md, docs/observability-policy.md, and AGENTS.md. Keep the eleven Gradle modules, the composition root, the `implementation` edge from `runtime-core`, and the infra → ports, domain, contracts direction.
- Respect recorded decisions: one adapter module (2026-06-12), validators in this module reached through domain ports (2026-05-28), external schemas as source of truth copied at build time (2026-05-29), shrink-only infra ambient baselines (2026-09-03), load-bearing thin ports retained (2026-09-03), and the 2026-09-04 guard recalibration (ceilings move by decision, never by baseline). A threshold or channel change lands as a decision entry.
- No new module, DI graph, process framework, logging library, result framework, or port per validator. One production adapter or one test substitute justifies an interface; nothing less.
- Preserve packaged resource paths and bytes (golden manifest), port success and failure semantics for well-formed input, git command argv and observable results, process deadlines and teardown guarantees, and every passing test's observable assertion. Only malformed input changes its failure identity.
- Do not split files by count, add `*Helpers`-style siblings, or hide dependencies in bags to satisfy a threshold. Ceilings are 1,200 lines and 40 functions; merge or split by responsibility under them.
- Deletion follows a fresh reference census plus compilation and the full runtime-kotlin test suite including `repoTest`; the recorded census is evidence, not authority.
- Re-read the owning documents and current source hashes before each subtask. Subtask 3 runs after subtasks 1 and 2 land; the area-edge and public-name censuses re-run in SKILL-354, not here.

## Non-goals

- Splitting `runtime-infra-fs` into Gradle modules, clustering the root package, moving the layer-order proof, or narrowing visibility: SKILL-354 owns those after this goal lands. Renaming the `FileSystem*`, `Jdk*`, `Git*` prefixes.
- Rewriting the scaffold pipeline, the native-agent renderer, install staging, or the review evidence broker; new product behaviour; changes to pack manifests or schemas.
- Deleting the twelve `gitops` role ports or the injected agent-run strategies.
- Replacing every adapter-internal `Map<String, Any?>` with typed models; subtask 3 documents the inventory and narrows visibility only.
- A coroutine migration of process handling.
- Rewriting the test suite beyond the classes a subtask touches.
- Certification against private Reddit, Microsoft, or Meta standards.

## Validation strategy

For the loader and validators, keep every existing `*SchemaValidatorTest` and `*SchemaContractVersionTest` green, add one test per family that a malformed input still yields the family's typed error with the same field path, and one test that a missing classpath resource is a typed schema error rather than a working-directory read. For failure identity, drive each changed seam with the malformed fixture its tests already use and assert the typed error class. For process lifetime, extend `GitProcessLifetimeBehaviorTest` to the migrated callers and assert the child is gone after timeout and interruption. For vocabulary and environment, assert install plans, MCP config paths, and native-agent link targets are byte-identical before and after through the existing install, launcher, and native-agent suites. For primitives, assert promoted files and digests match the pre-change fixtures. For structure, rely on compilation, `GovernedResourceCopyParityTest`, `RuntimeArchitectureDocumentationTest`, and `./gradlew check` on runtime-kotlin including `repoTest`. Run the module suite, `runtime-core` architecture guards, `runtime-cli`, `runtime-mcp`, and the pack-declared quality gate during implementation, and bill-unit-test-value-check for changed tests. The preparation baseline (1,518 tests passing) is evidence about `main`, not a future review receipt.

## Next path

Run `skill-bill goal SKILL-353` when implementation is intended. The prepared manifest is the goal runner's input.
