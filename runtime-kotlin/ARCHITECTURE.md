# Skill Bill Runtime Architecture

This document is the architecture guideline for `runtime-kotlin`. It states
module boundaries, ownership, and the rules new and changed code must follow.

Feature-specific file inventories, ticket histories, per-class measurements,
and case-by-case remediation notes do not belong here. The feature-task
run-loop boundary census is the one architecture contract that stays beside
its governing principle; the architecture test verifies that production
source matches its current and retained counts. Other numeric censuses and
baselines belong to architecture tests. Area `agent/decisions.md` owns why a
threshold or exception exists. Feature specs own planned work.

Kotlin coding patterns remain in [Code Principles](../docs/code-principles.md).
Degradation and fallback recording remain in the
[observability policy](../docs/observability-policy.md). The physical Gradle
split decision is in
[gradle-module-split-evaluation.md](../docs/architecture/gradle-module-split-evaluation.md).

## Design Principles

Apply these principles when designing, implementing, or reviewing runtime
changes. They are requirements for new and changed code. Existing violations
are tracked work, not examples to copy.

### Dependencies And Responsibilities

Keep domain rules independent of entry frameworks and concrete adapters.
Application and engine code coordinate use cases through ports. Adapters handle
filesystem, process, HTTP, and SQLite operations. The composition root wires
implementations. Use the declared module graph and package ownership below
rather than introducing another layer to satisfy an architecture label.

A component owns a responsibility whose changes can be understood together. A
port describes operations its consumer needs, not getters for another object's
entire dependency graph. An implementation must preserve the port's success,
failure, cancellation, and transaction semantics so a caller can substitute it
without changing its assumptions. Extend manifest-driven packs and injected
process strategies instead of adding identity branches to shared runners.

### State Ownership

Give each run one owner for coupled state transitions. Expose named transitions
and read-only results. Do not expose mutable collections or session fields to
helper objects. Model mutually exclusive outcomes as alternatives in a closed
type, not independently nullable reports with accidental precedence.

Pass helpers the facts or capabilities they use. Moving an all-access run-loop
parameter into a context, callback bag, or role interface does not narrow
access. Reconstruction from durable records must preserve the same invariants
as live execution, including retry consumption, checkpoint ownership, and phase
order.

Feature-task run-loop helpers take request, state, recorder, goal-recorder,
diagnostics, clock, or session as explicit parameters. Context extensions remain
only where an orchestration seam still needs launch, session, and phase-gate
ports together: the forward drive loop, review-generation invalidation, launch
capture, gate-cycle and fix-loop orchestration, and review driver execution. An
architecture test owns the per-file extension census and fails when it grows.
New helpers must not reintroduce run-loop or context-all-access parameters when
a narrowed overload already exists.

The SKILL-352 run-loop census is the contract for that architecture test. Counts
are `FeatureTaskRuntimeRunLoopContext.` receiver extensions in production
`FeatureTaskRuntimeRunLoop*.kt` files; the target is the retained count after
the boundary refactor.

| File | Current | Target |
| --- | ---: | ---: |
| `FeatureTaskRuntimeRunLoopAttemptSettlement.kt` | 4 | 4 |
| `FeatureTaskRuntimeRunLoopAuditRetry.kt` | 3 | 0 |
| `FeatureTaskRuntimeRunLoopBackwardEdge.kt` | 6 | 0 |
| `FeatureTaskRuntimeRunLoopCheckpoint.kt` | 10 | 0 |
| `FeatureTaskRuntimeRunLoopCheckpointRemediation.kt` | 12 | 0 |
| `FeatureTaskRuntimeRunLoopDrive.kt` | 2 | 2 |
| `FeatureTaskRuntimeRunLoop.kt` | 0 | 0 |
| `FeatureTaskRuntimeRunLoopLaunch.kt` | 6 | 2 |
| `FeatureTaskRuntimeRunLoopModels.kt` | 0 | 0 |
| `FeatureTaskRuntimeRunLoopOutputPersistence.kt` | 7 | 0 |
| `FeatureTaskRuntimeRunLoopOutputVerification.kt` | 5 | 0 |
| `FeatureTaskRuntimeRunLoopPhaseAttempts.kt` | 4 | 4 |
| `FeatureTaskRuntimeRunLoopPhaseRunner.kt` | 0 | 0 |
| `FeatureTaskRuntimeRunLoopPlanningBranch.kt` | 0 | 0 |
| `FeatureTaskRuntimeRunLoopRecordRejection.kt` | 5 | 0 |
| `FeatureTaskRuntimeRunLoopRepairReceipt.kt` | 3 | 0 |
| `FeatureTaskRuntimeRunLoopReview.kt` | 6 | 6 |
| `FeatureTaskRuntimeRunLoopSession.kt` | 0 | 0 |
| `FeatureTaskRuntimeRunLoopSharedArgs.kt` | 0 | 0 |
| `FeatureTaskRuntimeRunLoopSubtaskCommit.kt` | 0 | 0 |
| `FeatureTaskRuntimeRunLoopTransitions.kt` | 3 | 0 |
| `FeatureTaskRuntimeRunLoopValidationGate.kt` | 9 | 9 |
| **Total** | **85** | **27** |

### Resource Lifetime And Failure

Successful acquisition immediately establishes one cleanup owner for a child
process, stream, drain, endpoint, or lease. Every exit goes through that
owner's cleanup, including callback exceptions and cancellation. A shutdown
hook is a last resort, not the normal release path. Only terminate resources
the invocation owns, with the existing identity and fencing checks.

Bound cleanup and drain settlement. Do not publish mutable or incomplete
capture as settled evidence. Preserve the primary failure if teardown also
fails, retain cancellation signals, and record secondary failures through an
independent, bounded diagnostic path. Follow the observability policy for
degradation and fallback. Failure must not silently become normal absence.

SQLite write and read sessions share one rollback owner after a failed body or
commit. The primary failure still throws. A failed rollback attaches as a
suppressed exception. Process runners always release the run-local lifetime in
`finally`, keep interruption as the primary failure, and bound destroy and
drain joins. Git and installer child processes register handles before any
blocking wait, cover wait and settlement with one operation deadline, and use a
separate cleanup budget after failure. Fetch never executes a partial installer
script.

### Durable State And Projections

Name the authoritative store and the transaction owner for each mutation. Keep
related database changes atomic. SQLite and a filesystem projection do not
share a transaction: distinguish committed state from projection success or
failure. Regenerate a failed projection from authoritative state without
replaying an already committed workflow mutation.

Keep absent, completed, and failed operations distinguishable in boundary
results. An adapter callback inside a transaction must preserve that
transaction's snapshot and ownership. Do not move it outside merely to simplify
a dependency diagram.

### Database Readiness And Routine Work

Separate database readiness from opening a connection for ordinary work. Once
readiness succeeds, routine writes must not rerun historical backfills or
full-table repair scans. Keep required connection setup and the requested
transaction.

Failed initialization must not publish readiness. Concurrent initialization,
database replacement at the same path, and explicit reset must preserve
recovery. Run compatibility repair at a documented initialization or recovery
boundary. Do not remove required repair or cache success forever by pathname
alone. Measure repeated work before adding a cache, connection pool, or
replacement library. A warm-cache readiness decision uses the identity snapshot
already read for that call.

### Contract Ownership And Enforcement

Canonical schemas own wire shape. Kotlin contract owners declare keys and
versions. Consumers reference those declarations. Keep open extension payloads
distinct from governed envelopes. Type state whose invariants need compiler
protection without closing manifest-authored extension vocabularies.

A check for missing key ownership needs authority independent of existing key
declarations. A key absent from the owner inventory must not escape enforcement
because the scanner only searches that inventory. Test newly introduced schema
fields and undeclared literals, as well as valid references and allowed
extensions. State exactly which boundaries a scanner covers.

### Simplicity And Change Cost

Keep an abstraction only when a current consumer, adapter boundary, or useful
test substitute needs it. One production adapter can justify a hexagonal port.
Repeating its dependencies in a same-module interface and implementation
usually cannot. Delete dead helpers and pure forwarding layers before adding
another abstraction.

Use the existing modules and tooling unless a concrete requirement justifies a
change. Do not add speculative extensibility, a generic workflow framework, or
a blanket identifier-wrapper migration. Required schema validation, typed
failures, compatibility recovery, lease fencing, and durable evidence remain
requirements.

File size and constructor arity are signals, not definitions of cohesion. Do
not split by count, merge unrelated responsibilities, or hide dependencies in
bags to satisfy a threshold. Numeric limits and exemptions belong to their
existing enforcement owners, not another handwritten table in this document.

### Tests And Evidence

Before adding a test, name the concrete regression it catches. Prefer
observable boundaries: a child is gone after failure, a write rolls back, a
projection recovers, or resumed execution agrees with durable state. Exercise a
scanner through its real entry point instead of reproducing its algorithm in a
test.

Keep schema, compatibility, transaction, and lease tests. Remove assertions
that only pin incidental prose, trivial forwarding, or implementation structure
without protecting a contract. Do not pin exact SQL counts or use timing
assertions where the required property is absence of repeated maintenance.

### Enforcement

Review applies these requirements now. Mechanical checks prove only their
tested scope. Keep the existing enforcement inventory and baselines as the
record of implemented checks and tolerated debt. Do not expand an exemption to
make a change pass. Documentation must distinguish current enforcement from
planned coverage. Neither a green source scan nor an archived spec establishes
universal compliance with Clean Architecture, SOLID, or YAGNI.

## Runtime Graph

The runtime uses a hexagonal JVM graph with entry adapters at the outside,
application use cases in the orchestration layer, ports as the dependency
boundary, domain models and rules below the ports, and concrete infrastructure
behind those ports. `runtime-core` is only the composition root and runtime
metadata module.

```text
runtime-cli / runtime-mcp data gateways
  -> runtime-application use cases and the pinned runtime-engine inbound API
    -> runtime-ports
      -> runtime-domain models and rules
      -> runtime-contracts helpers for port-owned boundary payload contracts

runtime-engine
  -> runtime-application + runtime-ports + runtime-domain + runtime-contracts

runtime-infra-fs / runtime-infra-http / runtime-infra-sqlite
  -> runtime-ports + runtime-domain + runtime-contracts

runtime-core
  -> application services, engine services, ports, domain, and concrete adapters for DI wiring
```

## Feature-Task Continuation

Feature-task continuation is repository-scoped and database-authoritative. At
workflow creation, an immutable identity row binds the workflow id to a
normalized issue key, canonical real-path Git-root identity, repository-relative
governed spec path, persisted mode, and standalone or goal-child route scope.
Read-only lookup never chooses among multiple eligible rows by timestamp.

The feature `spec.md` remains the governed product contract. It is not a
mutable workflow ledger. A sibling `decomposition-manifest.yaml` is the sole
prepared-feature authority marker and always contains one or more executable
subtasks. A bare `spec.md` is preparation intake. Continuation lookup remains
authoritative and precedes artifact discovery. Pre-planning, planning, phase
outputs, and the phase ledger remain durable database artifacts.

Audit is stateless. Every invocation receives the complete planned
acceptance-criteria list, inspects implementation and meaningful test coverage,
repairs fixable gaps in the same agent session, re-checks the full list, and
emits only terminal completion or an ordinary blocked or failed outcome. Audit
performs no audit-specific persistence, does not route to `implement`, and does
not hand findings to downstream phases. Review remediation is a single bounded
round: `review` runs once, `changes_requested` may launch one `implement_fix`
pass, then the run advances to `validate`.

Decomposed goals execute discovery and preplan once at the parent, then persist
a distinct immutable plan checkpoint for each ordered subtask. Normalized
checkpoint tables are the continuation authority. Resume reuses compatible
checkpoints. Hard reset atomically invalidates planning and child continuation
state. Child creation hydrates the shared preplan and child's plan as completed
dependencies with goal-planning provenance. They add no child execution
duration, tokens, or agent attribution.

Runtime worker ownership is mutable state kept separately from immutable
execution identity. A worker lease records a random owner token, monotonic
fencing generation, host and boot identity, PID plus process-birth evidence,
heartbeat and expiry, and the incomplete phase attempt. Every heartbeat, phase
write, takeover reservation, transfer, and release must match both token and
generation. Process liveness is exact only when host, boot, PID, and birth
evidence agree. A non-terminal row orphaned by a killed child process
self-heals when its lease has expired and the supervisor confirms the process
dead. The pass runs at runner startup and in the goal parent's child-supervision
seam.

`bill-feature-task` is the public workflow identity for the runtime-backed
feature-task engine. Feature-verify remains a distinct workflow family and
store. Persisted rows use `workflow_name=bill-feature-task` and `mode=runtime`.
Engine continuation dispatch uses
`WorkflowDefinition.usesFeatureTaskRuntimeContinuation` rather than importing
the feature-task runtime workflow definition.

## Gradle Modules

- `runtime-contracts`: contract DTOs, JSON and ordered-map helpers, runtime
  surface contracts, `*SchemaPaths` constants, `*_CONTRACT_VERSION` constants,
  and the `skillbill.error` runtime exception taxonomy. It no longer owns the
  JSON-Schema validators or their schema-resource copy tasks. Those moved to
  `runtime-infra-fs`. It also owns the single ambient wall-clock seam, which
  cannot live in `runtime-domain` because domain effect purity forbids ambient
  time reads.
- `runtime-domain`: pure models and rules for agent-add-on, learning, review,
  telemetry, workflow, install-plan, scaffold, and skill-remove. Public domain
  data types live in area-owned `model` packages.
- `runtime-ports`: `skillbill.model.RuntimeContext`, persistence sessions,
  repositories, gateway interfaces, telemetry port interfaces, workflow git
  operations, decomposition-manifest file-store ports, and shared payload
  projection for boundary events that both application and infrastructure
  consume.
- `runtime-application`: CLI, MCP, and shared use cases outside the engine run
  loop, workflow orchestration, telemetry lifecycle orchestration,
  presenter-to-contract mapping, and validated decomposition-manifest file and
  artifact projection through workflow ports.
- `runtime-engine`: feature-task run loop, goal runner, goal planning, and
  planning projection use cases. It depends on `runtime-application` for the
  shared services those loops call today and exposes a pinned inbound API
  through `RuntimeComponent`.
- `runtime-infra-sqlite`: SQLite schema, migrations, connection and session
  behavior, SQL-backed repositories, review persistence, review stats, and
  telemetry outbox persistence.
- `runtime-infra-http`: telemetry HTTP client and requester implementation and
  telemetry proxy payload mapping.
- `runtime-infra-fs`: filesystem and process adapters, plus the concrete
  JSON-Schema validators and their schema-resource copy tasks, reached only
  through domain-neutral ports. It also owns the concrete JSON-Schema
  validators.
- `runtime-core`: Kotlin-Inject component definitions and DI providers. It may
  know concrete adapters only inside composition code. It publishes only the
  generated ABI edges that public `RuntimeComponent` exposes:
  `runtime-application` service types, the pinned `runtime-engine` inbound API,
  and `runtime-ports` context and port types. Downstream entry adapters and
  tests declare the modules they use directly.
- `runtime-cli`: Clikt command tree, option validation, terminal rendering,
  JSON output, help, completion surfaces, and CLI runtime context creation.
- `runtime-mcp`: MCP adapter surface, MCP-specific payload shaping, stdio
  server, MCP telemetry schema validation, and MCP runtime context creation.

The Gradle module set is:

```text
runtime-application
runtime-contracts
runtime-core
runtime-domain
runtime-engine
runtime-infra-fs
runtime-infra-http
runtime-infra-sqlite
runtime-cli
runtime-mcp
runtime-ports
```

## Package Ownership

- `skillbill`: runtime metadata that is safe for all runtime modules to read.
- `skillbill.di`: Kotlin-Inject composition root, owned by `runtime-core`.
  `RuntimeComponent` is the logical service surface. `@Provides` methods are
  the integration surface and are not duplicated in a second signature table.
  The component memoizes the first resolved `RuntimeContext` for its instance
  so database, telemetry config, and transport requester selection cannot drift
  mid-invocation.
- `skillbill.application`: use cases, workflow orchestration, lifecycle
  telemetry orchestration, repository-port coordination, and application-owned
  mappers. Public inputs and results live in area-owned
  `skillbill.application.<area>.model` packages.
- `skillbill.model`: shared runtime model types that are not owned by a
  narrower area, including `RuntimeContext`.
- `skillbill.config`: repo-local configuration domain models and resolution
  policy owned by `runtime-domain`.
- `skillbill.ports`: port contracts for persistence, install, scaffold,
  validation, telemetry, workflow git operations, and decomposition-manifest
  file storage. Public port DTOs live in `skillbill.ports.*.model`.
- `skillbill.contracts`: contract DTOs, JSON helpers, runtime surface
  contracts, `*SchemaPaths` constants, and `*_CONTRACT_VERSION` constants.
  Mapping from application, domain, or port models into contract DTOs belongs
  in application or adapter-owned packages. Schema validator classes compile
  into `runtime-infra-fs`.
- `skillbill.error`: runtime exception taxonomy.
- `skillbill.agent.model`: phase handoff string envelopes owned by
  `runtime-domain`.
- `skillbill.agentaddon`: governed agent-add-on filesystem discovery and schema
  validation owned by `runtime-infra-fs`, plus typed declaration models owned
  by `runtime-domain`.
- `skillbill.workflow`: workflow engine, decomposition, goal, task-runtime,
  IDE status, and spec-source models and rules owned by `runtime-domain`.
- `skillbill.workflow.verify`: Feature Verify workflow definition owned by
  `runtime-domain`.
- `skillbill.goalrunner`: pure goal-runner liveness policy, worker-subtask
  parsing, status projection, accounting, and attempt-ledger models owned by
  `runtime-domain`.
- `skillbill.idestatus`: agent activity label and stamp types for IDE status
  presentation owned by `runtime-domain`.
- `skillbill.engine`: feature-task run loop, goal runner, goal planning, and
  planning projection use cases owned by `runtime-engine`.
- `skillbill.featurespec`: feature-spec preparation policy and typed models
  owned by `runtime-domain`.
- `skillbill.install`: install-plan and install-apply domain models plus
  install-plan wire-map conversion owned by `runtime-domain`. Pure install
  policy lives in `skillbill.install.policy`.
- `skillbill.scaffold`: platform manifest, scaffold result, skill-class,
  routing, add-on, and review-composition models owned by `runtime-domain`.
  Pure scaffold policy lives in `skillbill.scaffold.policy`.
- `skillbill.domain.skillremove`: pure skill-remove service, target validation,
  rollback and refusal types owned by `runtime-domain`.
- `skillbill.learnings`: learning scope and source validation rules, payload
  helpers, and models owned by `runtime-domain`. LearningRecord is owned by the learnings domain.
- `skillbill.review`: pure review parsing, triage decision normalization, and
  review models owned by `runtime-domain`. Review parsing and triage decision
  normalization are pure surfaces.
- `skillbill.telemetry`: telemetry settings normalization and lifecycle
  telemetry records owned by `runtime-domain`.
- `skillbill.text`: UTF-8 truncation and size helpers owned by
  `runtime-domain`.
- `skillbill.infrastructure`: filesystem, HTTP, and SQLite adapters. SQL-backed review persistence lives under `skillbill.infrastructure.sqlite.review`.
- `skillbill.cli`: CLI adapter code. It validates CLI input, formats terminal
  output, maps typed results to contract payloads, and delegates behavior to
  application services or ports.
- `skillbill.mcp`: MCP adapter code. It validates MCP input, shapes MCP
  payloads, owns MCP-specific schema seams, and delegates shared behavior to
  application services or ports.

## Boundary Rules

1. CLI and MCP data gateways are entry adapters. They validate and
   translate input, then delegate to application use cases or ports.
2. Application owns workflow and use-case orchestration. It must not depend on
   Clikt, Compose, MCP adapter types, JDBC, Java HTTP clients, or concrete
   infrastructure packages.
3. Domain packages must not depend on CLI, MCP, JDBC, Java HTTP
   clients, filesystem APIs, process environment APIs, infrastructure packages,
   or application services.
4. Port packages must not depend on application, infrastructure, entry
   adapters, or composition roots.
5. Contracts packages must not depend on application, domain area packages,
   ports, infrastructure, entry adapters, or composition roots.
   `runtime-contracts` main source is a pure DTO, constants, and exceptions
   leaf. It MUST NOT contain any JSON-Schema validator, any
   `com.networknt.*` or `com.fasterxml.jackson.*` reference, or any
   `java.nio.file.Files` filesystem call. The concrete schema validators and
   their schema-resource copy tasks live in `runtime-infra-fs`, and
   `runtime-domain` / `runtime-application` reach schema validation only
   through the domain-owned ports `InstallPlanWireValidator`,
   `DecompositionManifestValidator`, and `WorkflowSnapshotValidator`.
6. Infrastructure packages implement ports and may depend on domain,
   contracts, ports, and JVM APIs. They must not depend on runtime-core or
   entry adapters.
7. `runtime-core` is the composition layer. Its source packages are limited to
   `skillbill` and `skillbill.di`. Only composition code may import concrete
   infrastructure implementations.
8. Entry adapters must not bypass application services and ports by importing
   concrete implementation packages such as filesystem install or scaffold,
   native-agent, launcher, skill-remove, SQLite, or HTTP adapter internals.
9. Application use cases access SQLite through repository and unit-of-work
   ports. Read use cases call a read session. Write use cases call an explicit
   transaction session.
10. Telemetry application use cases depend on `TelemetrySettingsProvider`,
    `TelemetryConfigStore`, `TelemetryClient`, and
    `TelemetryOutboxRepository`. HTTP request mechanics belong in
    `skillbill.infrastructure.http`. Config file IO belongs in
    `skillbill.infrastructure.fs`. Telemetry ports expose typed domain result
    models from `skillbill.telemetry.model`. Telemetry proxy wire DTOs belong
    in `skillbill.contracts.telemetry`. Telemetry proxy payload mapping belongs with the HTTP adapter.
11. JSON maps, YAML maps, MCP payloads, CLI JSON payloads, and terminal strings
    are boundary concerns. Internal use cases expose typed models.

    **Raw Map Boundary Rule (zero-tolerance):**
    public declarations on `runtime-application`, `runtime-domain`, and
    `runtime-ports` MUST NOT return or accept `Map<String, Any?>`,
    `Map<String, Any>`, `Map<String, *>`, string-keyed `MutableMap`,
    `HashMap`, or `LinkedHashMap` variants, or type aliases to those
    shapes. There is no curated FQN allow-list and no production
    annotation escape hatch.

    Contain wire maps in `private` or `internal` adapter serializers, or
    replace them with typed models at the port or application boundary.
    Inner-layer test sources in `runtime-application`, `runtime-domain`, and
    `runtime-ports` must not import `skillbill.infrastructure.*`,
    `skillbill.cli.*`, or `skillbill.mcp.*`.
12. `java.nio.file.Path` is allowed in application, domain, and port public
    models and contracts only as an inert value type: callers may carry,
    compare, resolve, normalize, and render path values as data. Filesystem IO,
    home-directory expansion, `System.getProperty`, and process environment
    reads are adapter or composition concerns. Application, domain, and port
    code must not call `Files`, `kotlin.io.path` IO helpers, `System.getenv`,
    or `System.getProperty`, and domain review parsing must stay limited to
    pure string and regex parsing.
13. Public data, enum, and sealed declarations in application, domain, and port
    modules live under explicit `model` packages. Services, runtimes, and port
    interfaces import those models instead of declaring public models inline.
14. SQLite schema changes are append-only versioned migrations recorded in
    `schema_migrations`, keyed by migration name. Version numbers order the list
    but do not identify a migration: branches assign them independently, so two
    lineages can ship different migrations under the same number.

The subsystem package set is:

```text
skillbill.agent.model
skillbill.agentaddon
skillbill.application
skillbill.cli
skillbill.config
skillbill.contracts
skillbill.di
skillbill.domain.skillremove
skillbill.engine
skillbill.error
skillbill.featurespec
skillbill.goalrunner
skillbill.idestatus
skillbill.install
skillbill.infrastructure
skillbill.learnings
skillbill.mcp
skillbill.model
skillbill.ports
skillbill.review
skillbill.scaffold
skillbill.telemetry
skillbill.text
skillbill.workflow
skillbill.workflow.verify
```

## Runtime Contract And Schema Seams

Runtime contract schemas live in `orchestration/contracts/`. The `*SchemaPaths`
constants and `*_CONTRACT_VERSION` constants stay in `runtime-contracts`. The
JVM JSON-Schema validators, their typed schema errors, and their classpath
resource copy tasks live in `runtime-infra-fs`, reached only through the
domain-neutral ports `InstallPlanWireValidator`,
`DecompositionManifestValidator`, and `WorkflowSnapshotValidator`.

- Workflow-state schema validation is owned by the infra-fs workflow validator,
  compiled into `runtime-infra-fs`, and reached through the domain-owned
  `WorkflowSnapshotValidator` port. The engine never builds a snapshot map.
- Install-plan schema validation is owned by the infra-fs install validator
  and reached through the domain-owned port
  `skillbill.install.model.InstallPlanWireValidator`.
- Decomposition-manifest schema validation is owned by the infra-fs
  decomposition validator, compiled into `runtime-infra-fs`, and reached
  through the domain-owned port
  `skillbill.workflow.decomposition.DecompositionManifestValidator`. The owning
  parse and emission seam is
  `skillbill.application.decomposition.DecompositionManifestFileWrites`.
  Repo-local manifest text persistence is owned by
  `FileSystemDecompositionManifestFileStore` behind
  `skillbill.ports.workflow.decomposition.DecompositionManifestStore`.
- Feature-task runtime wire artifact schema validation ports live in
  `runtime-domain` as `FeatureTaskRuntimeWireArtifactValidator` keyed by
  `FeatureTaskRuntimeWireArtifactKind`. Infra implements them. Goal progress,
  observability, and planning-preparation validator names are type aliases to that same port without default port bodies. Goal-continuation artifact keys
  declare in `FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys`.
- Platform-pack manifest schema validation is owned by the platform-pack
  validator in `runtime-infra-fs`. The owning parse seam is pack loading.
- Native-agent composition schema validation is owned by the native-agent
  composition validator in `runtime-infra-fs`.
- Telemetry-event schema validation is owned by the MCP adapter because the MCP
  tool registry is the event-name source of truth.
- Goal declared-progress and IDE status schema validation follow the same
  domain-port plus infra-adapter pattern. IDE status selection has a retention
  ceiling against authoritative `updated_at`. The IDE surface reports work the
  runtime is currently reporting on, never a ledger of every unresolved row.

`SkillBillVersion` reads packaged version metadata from `runtime-core`. Its
`getResourceAsStream` call is the single documented ambient-environment
exception for that metadata. A missing-resource fallback emits a
durable-decode substitution record.

## Phase Context Boundary

Complete producer output is private evidence. A named consumer projection is
the only prompt-visible derivative. Repository state has an immutable
checkpoint identity. Phase-local instructions use workflow-owned invariant
allowlists. Declaration and persistence wires have independent contracts.
Unknown fields, missing record identity, and unsupported versions are
incompatible rather than defaulted.

Budgets are enforced before launch against serialized UTF-8 bytes and
collection items. The runtime never truncates, drops fields, or falls back to a
complete artifact. Measurements contain identifiers, counts, token estimates,
and failure classifications only, never prompt or evidence bodies.

**Private evidence.** Complete validated phase output stays on the durable
phase record. Nothing reads it into a prompt directly.

**Consumer projection.** What a phase receives is declared, not inferred. A
source with no declaration is never delivered. The validator rejects, never
truncates, on a missing required source, malformed or undeclared field,
unsupported contract version, duplicate name, budget overflow, invalid compact
reference, or checkpoint-policy violation. Delivered envelopes persist
separately from private evidence. The two artifact keys must never merge.

**Repository-derived context.** A repository checkpoint carries a
deterministic fingerprint and working-tree ownership. The domain stays
git-agnostic. The application layer resolves the checkpoint through the
workflow git port. Shared review evidence is derived once per fingerprint into
a repo-local artifact. The delivered projection is a reference, never inlined
diff bytes. Ownership never derives from a path prefix alone.

The `build` phase runs only the pack `validation_gate.build_command` for
compile and buildability proof. It never invokes the collect-all validation
gate. Default standalone runs skip `build`. Goal continuation stamps which
quality gate a child runs.

**Phase-local instructions.** Run identity remains durable state on every
briefing, but prompt rendering is selected per phase by an invariant allowlist.
Identity, ceremony, and policy mandates reach every phase. The acceptance
contract is withheld from the finalization phases.

A completed phase that owns a bounded planning projection must emit one its
consumer can parse, checked at the producing phase's own schema gate so a
violation re-enters that phase's bounded fix loop. When a consumer still sees a
malformed upstream record, the runtime quarantines the rejected bytes as
private evidence and regenerates over a pinned consumer-to-producer edge.
Static declaration drift and briefing overflow keep their first-occurrence
durable block.

## Install And Scaffold Ownership

Install request validation and pure install-plan construction live in
`skillbill.install.policy` inside `runtime-domain`. The policy consumes typed
snapshots and produces a typed draft without touching filesystem, process
execution, staging hashes, symlink checks, binary discovery, or rollback
mechanics. `runtime-infra-fs` remains the owner of those mechanics and converts
facts into typed snapshots before calling the policy.

Scaffold payload-shape rules, kind discriminators, platform-pack selection, and
manifest rendering that have no filesystem dependency live in
`skillbill.scaffold.policy` inside `runtime-domain`. Scaffold IO is split
across capability-named ports under `skillbill.ports.scaffold`. Matching
filesystem adapters live in `runtime-infra-fs`. `ScaffoldGateway` is a typed
port consumed by the CLI.

## Architecture Guardrails

The architecture tests enforce the following rules:

- `ARCHITECTURE.md`, Gradle settings, and the architecture-test module catalog
  describe the same module and subsystem graph.
- `runtime-core` contains only `skillbill` and `skillbill.di` source packages.
- `runtime-core` does not directly re-export contract or concrete
  infrastructure modules as adapter API, and its transitive API closure stays
  limited to the documented Kotlin-Inject generated ABI closure.
- Top-level runtime modules do not depend upward or on sibling concrete
  adapters where forbidden.
- Infrastructure modules do not depend on runtime-core, CLI, MCP, or
  sibling concrete infrastructure adapters.
- CLI and MCP adapters declare direct runtime dependencies and do not
  use runtime-core as an implementation umbrella.
- CLI and MCP adapters call application services and ports instead
  of importing concrete install, scaffold, native-agent, launcher,
  skill-remove, SQLite, HTTP, validation, or filesystem implementation
  internals.
- MCP workflow calls must use application services.
- Application services remain independent from entry-point frameworks,
  concrete persistence, direct filesystem access, Java HTTP clients, and JDBC.
- repository and unit-of-work ports are the persistence boundary.
- versioned database migrations are recorded in `schema_migrations`.
- learning application use cases return typed results.
- Domain and port layers remain independent from adapters, infrastructure,
  composition roots, and implementation details.
- Public application, domain, and port model declarations live under `model`
  packages.
- Learning, review, telemetry, workflow, install, scaffold, and skill-remove
  ownership stays in the packages named above.
- Workflow-state, install-plan, decomposition-manifest, platform-pack,
  native-agent composition, and telemetry-event schema validators are exercised
  at their owning parse seams.
- typed CLI presenter models are the input to CLI text rendering.
- Every `runtime-cli` command area's transitive `skillbill.cli` import closure
  contains only the shared `kernel` and `model` leaves, never a sibling command
  area and never the composition root `skillbill.cli.core`.
- No runtime module source file, and no main-source file, type, or member
  declaration, carries the spillover signature (`*Extras`, `*Continued`,
  `*Helpers`, `*Support`, `*Misc`, `*Fns<N>`, letter-plus-digit, or bare
  trailing-digit siblings) outside a named exemption. The bare `Support`,
  `Helpers`, `Misc`, and `Extras` forms apply to `src/main` only, the numbered
  forms to every `src` tree.
- No main-source site outside `skillbill.di` constructs a concrete class
  censused from `@Provides` parameter types and explicit Provides
  constructions. `RuntimeCompositionGuardArchitectureTest` matches import
  aliases, ignores comments and string literals, skips unrelated same-named
  functions, and names sanctioned second entrypoints explicitly.
- `RuntimeComponent` logical service properties are pinned separately from
  `@Provides` generated wiring. `RuntimeComponentInboundApiArchitectureTest`
  rejects any other public function on `RuntimeComponent` or a
  `Runtime*Provides` mixin even when the abstract property set is unchanged.
- `skillbill.infrastructure.fs.scaffold.runtime.ScaffoldStandaloneEntrypoint`
  is the sanctioned second scaffold entrypoint for in-tree parity and rollback
  tests that cannot reach `RuntimeComponent`. Production paths use
  `FileSystemScaffoldOrchestrator`.
- Production files stay under the logical-type line ceiling. Package-import
  cycles, ambient clock reads, ambient environment reads, and `@Inject`
  constructor defaults use shrink-only baselines per module. Empty baselines
  stay empty by rule.
- `RuntimeCliAreaIsolationArchitectureTest` proves command-area isolation
  beyond an empty cycle baseline.
- `RuntimeContractModuleImportRulesTest` pins inward-layer import bans.
- The Raw Map Boundary Rule is enforced with zero-tolerance: no allow-list and
  no annotation grandfather path.

### Port null-object classification

`PortNullObjectAbsenceArchitectureTest` requires that no `Unavailable`, `Noop`,
`Empty`, or `Unconfigured` object is declared in any runtime module's main
source. A port whose absence a production call site actually reaches is
nullable, and the reached site names its fallback or returns the absent answer.
The substitutes that tests still need live in the owning module's
`src/testFixtures`. `data object` cases of a sealed hierarchy are not
substitutes.

### Destructive command failure policy

`uninstall` is the runtime's only destructive command. A mutation it cannot
apply is a recorded degradation with a non-zero exit code, never a warning
string on a zero exit. Launcher removal, desktop removal, recursive tree
removal, agent-target cleanup, native-agent unlinking, and MCP unregistration
share that one policy through `UninstallMutationRecorder`. No mutation site
formats its own warning or decides its own severity.

The completion telemetry drain is the one deliberate swallow that stays: it
must not change the run's exit code and must not reach the run's stdout or
stderr. It is not silent. Every abandonment path, including a worker still alive
after the join timeout, an interrupted join, and the worker's own failure, emits
a `RuntimeDiagnostics` warning.

Runtime database selection belongs to the bound `EnvironmentContext`. The
session factory resolves and retains one normalized path for its component
lifetime. Application and port operations do not accept database path
overrides.

## Wire Vocabulary

Runtime-domain wire-token declarations own closed enum tokens and their
aliases. Runtime-contracts `*Keys` declarations own durable and wire payload
keys. `SharedPayloadKeys` is the shared owner for workflow phase-output
envelope keys. Enum wire tokens use `wireValue` on the owning enum. Downstream
modules reference those constants. They do not restate wire strings.

Closed workflow and decomposition decisions use domain-owned status
vocabularies. Their `wireValue` members are the only declarations of the
supported tokens, and `fromWire` is used at durable-map seams. Provider, pack,
and versioned durable payloads whose vocabulary is owned by that boundary stay
open at that boundary. Snapshot and observability payloads stay byte-identical
for supported values.

`WireVocabularyGovernedSeamInventory` is the independent expected-key
authority. It reads canonical schema YAML for decomposition manifests, the
decomposition bundle journal, and the workflow phase-output envelope, and
declares the closed goal-continuation artifact vocabulary independently from
its Kotlin owner. Literal payload-key enforcement runs only on production
sources whose paths match the inventory's governed markers. A green scan
does not prove every `String` in the runtime is typed. The architecture test
includes a fixture that fails when a governed seam accesses a literal key instead of its owner.

| Seam | Schema authority | Open extension (not key-owned) |
| --- | --- | --- |
| Decomposition manifest | Root, subtask, dependency, stack branch, and current-intent closed objects | N/A at manifest root (`additionalProperties: false`) |
| Decomposition manifest bundle journal | Bundle-journal root and entry closed objects | None |
| Workflow phase-output envelope | Top-level envelope fields only | `produced_outputs` entry maps (phase-specific keys stay open) |
| Feature-task runtime goal-continuation artifact | `FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys` | None |

Workflow Git results are a closed `Ok` or `Failed` vocabulary owned by the
workflow git-ops port model. Recognized no-change failures may normalize to
empty `Ok` in the adapter, while goal finalization may accept a marker-bearing
`Failed`. Both paths are intentional.

## Native-Agent Installation

Native-agent rendering promotes artifacts atomically into the installed cache
and records each Skill Bill-managed link in the user-home inventory.
Reconciliation uses the complete prior inventory to remove obsolete or dangling
managed links. It never deletes a regular file or a symlink that no longer
resolves to its recorded managed target. Install verifies the linked artifact's
logical name, digest, target, and readability before committing the inventory.

## Delegated Code Review

`ParallelCodeReviewRunner` resolves scope, diff, dominant stack, rubrics, and
project rules once for the whole review, then hands every top-level lane the
same immutable parent packet. A lane never re-resolves a fact the parent
already established.

Manifest composition flattens to one direct specialist lane per selected area.
The nearest owning layer wins. A composed root expands to its own specialists
plus required baseline specialists. The baseline review skill is never launched
as a nested orchestrator.

`ReviewOperationPolicy` classifies every operation a specialist requests
without consulting platform, pack, or provider identity. Repository status,
scope discovery, diff recomputation, build and test invocation, pack
resolution, routing, learnings, telemetry ownership, and opaque searches are
refused because the parent packet already carries those facts.

`ReviewEvidenceBroker` is the single measured surface a specialist may act
through. Assigned paths are served in bounded batches. Anything outside the
assignment needs an authorized expansion whose record belongs to the parent
packet. When delegated execution selects provider-native specialists, every
assignment is verified against the managed native-agent link inventory before
any worker starts. A missing, stale, or dangling link fails the whole review.
There is no generic-worker fallback.

Independent parallel lanes share the parent packet and nothing else. One lane's
budget termination, timeout, or process failure never disturbs its sibling.
