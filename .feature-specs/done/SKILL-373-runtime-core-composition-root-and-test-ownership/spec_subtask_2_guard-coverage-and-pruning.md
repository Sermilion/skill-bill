# SKILL-373 subtask 2 - Guard coverage and pruning

Parent spec: [spec.md](spec.md). Findings F-001, F-003, F-007, and F-008 in
[investigation.md](investigation.md).

## Scope

Change rules and delete tests in place under
`runtime-core/src/test/kotlin/skillbill/architecture/`. The only production
changes are any runtime-engine fixes the restored coverage still requires. Subtask 3
moves files; this subtask does not.

Coverage (F-001):

- In `RuntimeApplicationAmbientClockArchitectureTest`,
  `AmbientEnvironmentArchitectureTest`, and `InjectConstructorDefaultsArchitectureTest`,
  replace the per-module test methods with one test per rule that iterates
  `PrincipleEnforcementInventory.moduleArchitectureScanCases` and reports every
  module's drift in one failure message. Keep each rule's existing rejection
  fixtures. Keep SKILL-370's inject-property extension.
- Engine coverage for the ambient-clock, ambient-environment, and inject-default rules is in this subtask. If experiment coordinators or direct clock calls are still in the engine and the restored rules report them, inject the dependency here. Do not record the site, and do not wait for another issue to delete it.
- Keep the inject-property rule that exists. If this subtask's criteria require it on engine types, extend it here. Only the inject-default check needs to iterate every module.
- Drop any baseline row whose call site no longer exists.
- Carry forward rows other bundles committed before this subtask unchanged, for
  example SKILL-376 subtask 3's single skills `nativeagent|scaffold` package-cycle
  row. Package-cycle baselines keep their current key format; only the ambient
  baselines are re-keyed.
- Keep every ambient-clock form present when this subtask starts, including
  `System.currentTimeMillis`, which SKILL-376 subtask 1 adds to `AMBIENT_CLOCK_FORMS`.
- If the restored rules report more than 15 new sites, stop and report the list
  instead of fixing them.

Baseline keys (F-008):

- Change the ambient-clock and ambient-environment encodings from
  `path:line:call` to `path:call` with an occurrence count, in
  `ArchitectureScanSupport` and `ArchitectureBaselineRecorder`.
- Re-record each baseline once and review the diff. The only row changes allowed
  are the format change and removals.

Redundant module guards (F-003):

- Delete `RuntimeGradleModuleLayeringTest.top level runtime modules do not depend
  upward`.
- Delete `ImplementationOwnershipArchitectureTest.infrastructure modules do not
  depend on adapters or runtime core`.
- Delete any other test that restates module edges outside `RuntimeModuleCatalog`.
- Keep `RuntimeCoreCompositionOnlyTest`, `RuntimeAdapterDependencyAllowlistTest`,
  and their rejection tests.

Prose, history, and count pins (F-007):

- Delete `RuntimeArchitectureDocumentationTest`.
- Delete every assertion elsewhere that reads `ARCHITECTURE.md` or
  `agent/*.md`, including the `runtime-core` ABI-closure prose checks in
  `RuntimeImplementationImportRules`.
- Delete tests whose subject is a retired, moved, legacy, flat, or superseded
  shape. The investigation lists known cases; apply the rule to every class.
- Delete source-text pins that assert named files exist or contain import
  substrings, for example `RuntimeArchitectureTest` "cli and mcp learning payloads
  use contract DTO mappers". If that test is already gone, skip it.
- Delete `FeatureTaskRuntimeParameterBagArchitectureTest` and
  `FeatureTaskRuntimeRunLoopContextExtensionCensusArchitectureTest` if they still
  pin the run loop's current procedural form.
- Delete count pins such as `inventory lists nineteen enforceable rules`.
- Delete support code no surviving test calls. Update
  `PrincipleEnforcementInventory.enforceableRules` and its test to the surviving
  rules.
- Repair `PortsDeclarationArchitectureTest` and `PortNullObjectAbsenceArchitectureTest`
  in this subtask if they still scan nothing. Do not leave a vacuous guard for
  another issue.
- Survival rule for anything not named: a test stays if it asserts an invariant
  that neither the compiler nor the `RuntimeModuleCatalog` topology enforces, and
  it is not prose, history, a count, or a restatement of source.

Documentation: rewrite `../../../runtime-kotlin/ARCHITECTURE.md` Architecture Guardrails
and the enforcement-status text as the surviving rules in current-state prose.
Update `AGENTS.md` and `docs/code-principles.md` where they name deleted rules.
Record the survival rule, iteration over the module list, and the baseline key
format in `runtime-kotlin/agent/decisions.md`.

## Acceptance Criteria

1. The ambient-clock, ambient-environment, and inject-default rules each report a
   violation placed in any module listed in `settings.gradle.kts`, shown by a
   rejection fixture in runtime-engine.
2. runtime-engine main has no ambient-clock, ambient-environment, or
   inject-default violation beyond the rows in the baselines committed before this
   bundle, and no baseline gains a row.
3. Baseline rows contain no line numbers, and moving a tolerated call to another
   line in the same file leaves every baseline unchanged.
4. No architecture test reads `ARCHITECTURE.md` or any `agent/*.md` file.
5. No architecture test's subject is a retired, moved, legacy, flat, or superseded
   shape, and none asserts a count of rules.
6. Module edges are checked only by tests that compare against
   `RuntimeModuleCatalog`, and their added, removed, and reclassified edge
   rejection tests still pass.
7. `PrincipleEnforcementInventory.enforceableRules` names only rules with a
   surviving test.
8. `ARCHITECTURE.md` Architecture Guardrails lists the surviving rules and contains
   no `SKILL-` reference.

## Non-goals

- Moving the suite or any test to another source set or module (subtask 3).
- Narrowing the ambient rules to inner layers, adopting a scanning library, or
  changing any surviving rule's meaning beyond iteration and the baseline key.
- Rewriting repaired scanners from SKILL-371 subtask 1.

## Dependency notes

Depends on subtask 1, which deletes the `@JvmSynthetic` rule and the composition guard exemption. Judge scanners on the current tree. If a scanner this subtask keeps or deletes still reads zero files, repair that scanner here before the survival decision. Do not wait for another issue.

## Validation strategy

- `cd runtime-kotlin && ./gradlew :runtime-core:test :runtime-engine:test check`.
- Record `@Test` totals for the suite before and after. The commit body carries a
  disposition table for every deleted test, with its reason: prose pin, history,
  count, duplicate of the catalog, graph-enforced, or support for a deleted rule.
- Regressions the surviving suite must still catch, each shown by its rejection
  fixture:
  - a `//` comment in main Kotlin;
  - an inline FQN;
  - a wire key restated as a literal;
  - an infrastructure module gaining `implementation(project(":runtime-core"))`;
  - `System.getenv` in runtime-application main;
  - `Clock.systemUTC()` as an `@Inject` default in runtime-engine.
- Run `bill-unit-test-value-check` on the changed architecture tests.

## Next path

Continue with subtask 3 (`spec_subtask_3_test-ownership-and-declared-inputs.md`).
