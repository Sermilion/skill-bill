# SKILL-358 - runtime-ports boundaries and simplicity

Issue key: SKILL-358
Mode: decomposed (two dependency-ordered subtasks)
Investigation: [investigation.md](investigation.md)

## Intended outcome

`runtime-ports` becomes what its recorded rule already says it is: interfaces and DTOs. Behaviour that the inside
cannot substitute leaves for the adapter that owns it or the domain that owns the rule; every interface member is
either abstract or a delegation to another member of the same interface; every wire token and payload key has one
owner in `runtime-domain` or `runtime-contracts`; no public port type is a raw map by another name; the dead
declarations, the duplicate policy, the census-only fixtures, and the second constructor go; and an architecture
guard fails on the next object, cast, or throwing default that lands in the module.

## Findings to subtasks

| Finding | Subtask |
| --- | --- |
| F-001 behaviour in the substitution layer | 1 |
| F-002 port extension shadows the journaled adapter write | 1 |
| F-003 duplicate policy, two-package payload mapper | 1 |
| F-004 partial-implementation defaults, fabricated results, swallowed load | 1 |
| F-006 dead declarations | 1 |
| F-007 protocol rename adapter, provider vocabulary | 1 |
| F-008 exceptions outside the taxonomy | 1 |
| F-009 two constructors for `RuntimeContext` | 1 |
| F-010 packaging, fixtures, tests (module-owned part) | 1 |
| F-005 wire tokens restated, keys inlined, raw-map wrappers | 2 |

## Acceptance Criteria

1. `runtime-ports/src/main` declares no top-level `object`, no top-level `class` other than `data`, `enum`,
   `sealed`, or `value` classes, no `(this as` cast, and no interface default body that calls `error(` or
   `throw`; a `runtime-core` architecture test enforces each of those four counts at zero with no baseline and is
   listed in `PrincipleEnforcementInventory.enforceableRules`.
2. The governed review evidence codec, its schemas, parsing, and payload builders live in `runtime-infra-fs`
   beside `GovernedReviewEvidenceEndpoint`; the names, environment variables, byte caps, and operation list it
   exported live as constants in `runtime-contracts`; `runtime-mcp/src/main` imports nothing from
   `skillbill.ports.review.model.GovernedReviewEvidence*`.
3. `writeBundleAtomically` is an abstract member of the decomposition manifest persistence port implemented by
   `FileSystemDecompositionManifestFileStore`, so a caller holding the interface reaches the bundle lock and
   journal; the ports extension and its private snapshot type are deleted; the `*WithoutRecovery` members are
   abstract.
4. The execution-lease algorithm and the runner-interrupted-pause reset live in `runtime-infra-sqlite`; the
   `GoalRunnerControlRepository` file declares only abstract members.
5. `FeatureTaskExecutionIdentity`, its enums, and `FeatureTaskExecutionIdentityPolicy` are declared exactly once,
   in `runtime-domain`; the `skillbill.ports.continuation` package does not exist.
6. Every interface default that threw "not implemented" is abstract; test fakes that relied on a default extend a
   `*Defaults` class in `runtime-ports/src/testFixtures`; `WorkflowGitRemoteOperations` has no default that
   returns `Ok`; `TelemetrySettingsProvider.loadOrNull` is gone and its caller records the absence.
7. `NativeReviewOperationProtocol`, `BrokerBackedNativeReviewOperationProtocol`, `ReviewEvidenceLaneAccounting`,
   `ConversationIsolation`, the discovery operation and its types, and every declaration listed in F-006 are
   deleted; `SkillRunRequest` carries one review evidence collaborator.
8. Every exception declared under `runtime-ports` moves to `skillbill.error` in `runtime-contracts` and extends
   `SkillBillRuntimeException` or a subtype; `GoalRunnerWedgeClass.fromWire` returns a nullable value or throws a
   typed error.
9. `RuntimeContext` has one constructor and no companion aliases.
10. `GoalChildWorkflowDeletionScope` carries `WorkflowStatus` values; `WorkflowGitOperationStatus` is the only
    declaration of the `ok`/`error` tokens; every payload key written or checked in `runtime-ports` is a constant
    in `runtime-contracts`, and a repo-contract test pins the review accounting and review-finished key sets to
    their YAML schema branches.
11. `IdeStatusWireMap`, `GoalSubtaskReviewInputWireMap`, and `ReviewAccountingBoundedPayload` are deleted;
    `ReviewAccountingRecord` carries `ReviewAccountingSummary`; `IdeStatusValidator` accepts a typed model; the
    raw-map scanner no longer exempts a public class in `runtime-ports` by name suffix.
12. `./gradlew :runtime-ports:test` and `./gradlew check` on `runtime-kotlin` pass; the four `runtime-ports`
    architecture baselines stay empty; every consumer module compiles against the narrowed surface.

## Constraints

- Follow `../../../runtime-kotlin/ARCHITECTURE.md` design principles, `docs/code-principles.md`,
  `docs/observability-policy.md`, and AGENTS.md. No `//` comments, KDoc only on interfaces.
- Keep the recorded decisions: `GoalRunnerManifestStore` stays five seams; `WorkflowFamily` extensions stay
  beside `WorkflowStateRepository`; `Path` stays an inert value in DTOs and the `FileLocation` migration stays
  deferred; `Noop*`/`Unavailable*` substitutes stay in `testFixtures`; `ReviewFinishedTelemetryPayload` stays in
  `ports.telemetry.model`.
- No new module, no new runtime dependency, no change to SQL, schema files, or persisted formats.
- Behaviour observable through the CLI, MCP, and persisted records is unchanged for well-formed input. The one
  intended behavioural change is that bundle writes through the interface now take the lock and journal.
- Each subtask ships alone as one commit with its bindings, tests, documentation, and decision entries.

## Non-goals

- The `FileLocation` migration.
- Merging or re-splitting any recorded interface composite.
- Re-packaging the 41 `model` sub-packages beyond deleting the dead package and folding alias files.
- Changing `runtime-mcp`'s stdio framer or bridge structure; SKILL-357 owns that. This goal only changes which
  symbol the bridge imports for the review-evidence constants and where it gets tool specs.

## Validation

Name the regression before each test. Run `:runtime-ports:test`, the `runtime-core` architecture guards
(`RuntimeContractModuleImportRulesTest`, `RuntimeRawMapArchitectureTest`, `RuntimeLayerBoundaryArchitectureTest`,
`PortNullObjectAbsenceArchitectureTest`, `PrincipleEnforcementInventoryTest`, and the new ports declaration guard),
the test suites of every module that changed a signature (`runtime-application`, `runtime-engine`,
`runtime-infra-fs`, `runtime-infra-sqlite`, `runtime-mcp`, `runtime-cli`), and `./gradlew check` on
`runtime-kotlin`. Run the pack-declared quality gate and `bill-unit-test-value-check` for changed tests.

## Next path

```bash
skill-bill goal SKILL-358
```
