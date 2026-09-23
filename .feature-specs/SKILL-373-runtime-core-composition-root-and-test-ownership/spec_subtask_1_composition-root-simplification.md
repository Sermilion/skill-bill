# SKILL-373 subtask 1 - Composition root simplification

Parent spec: [spec.md](spec.md). Findings F-004, F-005, F-006, F-011, and F-012
in [investigation.md](investigation.md). F-009 belongs to SKILL-378 subtask 1,
which deletes experiment support before this subtask runs.

## Scope

Production wiring only. Behavior stays the same. Every call-site change a
signature requires ships in this commit. Regenerate KSP before and after; the
generated components are evidence, never hand-edited.

Parameter bags (F-004, `di/goal/RuntimeGoalRunnerStoreProvides.kt`,
`di/core/RuntimeComponent.kt`):

- Add `goalRunnerManifestStore: GoalRunnerManifestStore` and
  `goalRunnerWorkflowOutcomeStore: GoalRunnerWorkflowOutcomeStore` accessors.
- Bind both ports to `WorkflowGoalRunnerManifestStore` and
  `WorkflowGoalRunnerOutcomeStore` through their `@Inject` constructors.
- Delete the five `GoalRunner*Dependencies` classes and their five providers.
- If SKILL-376 subtask 2 has already moved coordination out of these stores, apply
  the same rule to whatever classes it left.

`@JvmSynthetic` (F-005):

- Remove every `@JvmSynthetic` from runtime-core.
- Delete `runtimeComponentInternalProviderJvmLeaks` and its assertion in
  `RuntimeImplementationImportRules`.
- Keep the SKILL-350 public-callable classifier.

Second composition root (F-006):

- Move `ScaffoldStandaloneEntrypoint.kt` from runtime-infra/skills `src/main` to
  its `src/test`, keeping the package.
- Delete `PrincipleEnforcementInventory.sanctionedCompositionEntrypoints` and its
  handling in `RuntimeCompositionGuardArchitectureTest`.

Composition inputs (F-011):

- After SKILL-378 subtask 1 has removed `ExperimentGoalRunnerFactory` (the engine's
  only reader), move `RuntimeContext`, `TransportContext`, `WorkflowOpsContext`, and
  `OptionalCallbacks` from runtime-ports `skillbill/model/RuntimeContext.kt` into
  runtime-core `skillbill.di.core`. `EnvironmentContext` stays in runtime-ports.
- Update runtime-cli `CliRuntimeContext`, runtime-mcp `Main.kt`, and the tests
  that construct these types (runtime-core 8 files, runtime-cli 2, runtime-mcp 1).
- Delete `OptionalCallbacks.reviewNativeAgentPreflight`, its provider branch, and
  `CliRuntimeContext.reviewNativeAgentPreflight`.
- Update `RuntimeArchitectureTest.runtime context does not depend on infrastructure
  defaults` to the new path if it survives.

Small defects (F-012):

- Replace `skillBillVersion(): String` with a single-field value type in
  runtime-ports `skillbill.model`, so application (`SystemService`) and
  infrastructure (`SQLiteDatabaseSessionFactory`, which SKILL-372 subtask 3 gives a
  version input) can both take it. The provider still reads
  `SkillBillVersion.VALUE`. `McpProtocolFramer` keeps reading `SkillBillVersion.VALUE`
  directly: `GovernedReviewEvidenceBridge` calls `McpProtocolFramer.initialize` in a
  process that builds no component, and `SkillBillVersion` is the documented
  packaged-metadata source for every entry point. The missing-resource fallback
  keeps its substitution record. If SKILL-372 subtask 3 has landed with a
  `String` parameter, convert it in this commit.
- Delete `featureSpecPreparationCore()`. `FeatureSpecPreparationRuntime` calls
  `FeatureSpecPreparationPolicy.prepare`; update
  `FeatureTaskRuntimeRunnerTestSupport.kt` accordingly.
- Delete the discarded `GoalPlanningDiscoveryExclusions.excludedRoots` statement
  unless SKILL-374 already has.
- Break the `{di.core, di.telemetry, di.workflow}` cycle: the version and
  repository-root providers stop importing from `di.core` back into their area
  packages. Empty `runtime-core-package-cycle-baseline.txt`.
- Delete the accessors `goalPlanningPreparationCheckpoint`, `uninstallPathsPort`, and
  `installedWorkspaceBaselineStatusPort` (SKILL-377 subtask 3 later deletes that
  port and its binding) after confirming
  in freshly generated `InjectCliComponent` and `InjectMcpComponent` that nothing
  reads them. Update `PrincipleEnforcementInventory.runtimeComponentInboundApi` to
  match.

Documentation: rewrite the runtime-core bullets in `runtime-kotlin/ARCHITECTURE.md`
(Gradle Modules, Package Ownership) as current state. State that accessors are the
export list for child components, and remove the `@JvmSynthetic` sentences. Add
the subtask's decisions to `runtime-kotlin/agent/decisions.md`.

## Acceptance Criteria

1. `RuntimeGoalRunnerStoreProvides.kt` declares no class, and `GoalRunnerManifestStore`
   and `GoalRunnerWorkflowOutcomeStore` are bound to `@Inject` implementations and
   exposed as `RuntimeComponent` accessors.
2. The generated `InjectCliComponent` and `InjectMcpComponent` reference no
   `skillbill.infrastructure` type, and runtime-cli and runtime-mcp main still
   declare no infrastructure dependency.
3. runtime-core main contains no `@JvmSynthetic`, and no architecture rule
   requires it.
4. `ScaffoldStandaloneEntrypoint.kt` is absent from every `src/main`, its tests
   still pass, and the composition guard has no exemption list.
5. runtime-engine main does not reference `RuntimeContext`, and
   `RuntimeContext`, `TransportContext`, `WorkflowOpsContext`, and
   `OptionalCallbacks` are declared under runtime-core `skillbill.di.core`;
   `OptionalCallbacks` has no field that no test sets.
6. No runtime-core `@Provides` returns `String` or a function type other than
   the `(String) -> ParallelReviewParseResult` binding.
7. `runtime-core-package-cycle-baseline.txt` has no rows.
8. Every remaining `RuntimeComponent` accessor has a reader in handwritten or
   generated code.
9. The composition suites and the runtime-infra/skills scaffold suites pass; any
   test deleted with its subject is named in the commit body.

## Non-goals

- Architecture-suite pruning or relocation beyond the rules this subtask's
  deletions touch (subtasks 2 and 3).
- Any CLI or MCP output, schema, or wire-contract change.
- Converting hand-constructed adapters in providers to `@Inject`.
- Experiment product behavior outside the runtime-core bindings removed above.
- `EnvironmentContext` sentinels and the remote-transport typed error.

## Dependency notes

Depends on nothing outside this bundle. No dependency inside this bundle. Subtask 2 depends on this subtask.
If experiment bindings are still in runtime-core, remove them here. Apply one rule to
`RuntimeGoalRunnerStoreProvides` as it exists: one `@Inject` binding per port, an
accessor when a child needs it, no bag. This subtask does not touch `McpProtocolFramer`
or runtime-mcp's bridge path.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check`.
- Record in the commit body the accessor census (handwritten and generated
  readers) and the tests that set each `OptionalCallbacks` field.
- Regressions the kept suites catch: a store binding that loses a collaborator
  (goal-runner suites over real SQLite); two components sharing scoped state or
  one rebuilding it (`RuntimeComponentScopedIdentityTest`,
  `RuntimeComponentInvocationSnapshotTest`); a scaffold test losing its harness
  (runtime-infra/skills test and repoTest).
- `./install.sh --from-source`, then `skill-bill doctor` succeeds and the MCP
  `initialize` response carries the packaged version.

## Next path

Continue with subtask 2 (`spec_subtask_2_guard-coverage-and-pruning.md`).
