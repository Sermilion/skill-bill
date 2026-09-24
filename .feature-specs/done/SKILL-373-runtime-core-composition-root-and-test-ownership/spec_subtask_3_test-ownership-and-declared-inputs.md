# SKILL-373 subtask 3 - Test ownership and declared inputs

Parent spec: [spec.md](spec.md). Findings F-002 and F-010 in [investigation.md](investigation.md).

## Scope

Mechanical moves and build wiring. Do not rewrite test bodies except for imports,
package declarations, and root resolution.

Declared inputs (F-002):

- Apply `skillbill.repo-test` to runtime-core.
- Move the architecture suite, its support code, and its baselines from
  `src/test/kotlin/skillbill/architecture/` to `src/repoTest/kotlin/skillbill/architecture/`.
- In `runtime-core/build.gradle.kts`, add the runtime-kotlin trees the suite reads
  as inputs of the `repoTest` task, with `PathSensitivity.RELATIVE`: every
  module's `src/**`, every `*.gradle.kts`, `settings.gradle.kts`, `ARCHITECTURE.md`,
  `agent/**`, `build-logic/convention/src/**`, `config/**`, and `.editorconfig`.
  Exclude `**/build/**` and `.gradle/**`.
- The plugin's `governedRepositorySources()` stays as is.
- Add no new module.

Installer and launcher shell tests (F-010):

- Move `InstallerShellDelegationTest`, `InstallerShellDelegationTestSupport`,
  `InstallerShellReconcileTest`, `InstallerShellReuseLastSelectionTest`, and
  `GoalRuntimeDelegationParityTest` to runtime-cli
  `src/repoTest/kotlin/skillbill/installer/`.
- Delete `installer delegates install application to durable installed runtime`,
  which asserts 29 substrings of `../../../install.sh` text. The executing tests cover the
  same behavior.

Single-subject tests (F-010):

- Move `DecompositionManifestValidationTest` and `SchemaValidatorPortLoudFailTest`
  to runtime-infra/contracts `src/test`, which already has the runtime-infra:workflow
  test dependency they need. `ReviewContextSchemaValidatorTest` stays in
  runtime-core: it validates application output against an infrastructure schema.
- Move `TelemetryReleaseAttributionTest` to runtime-infra/sqlite `src/test`.
- Move `GoalRunnerControlBindingArchitectureTest` to runtime-ports `src/test`, named
  for its subject.
- Move `InstallSelectionRuntimeBoundaryTest` to runtime-core
  `src/test/kotlin/skillbill/di/`.
- Add a test dependency to a receiving module only when a moved test needs it.
  Never add a production dependency, and never add an infrastructure test
  dependency to runtime-application, runtime-domain, or runtime-ports.

Packages (F-010):

- Every remaining runtime-core test moves under `skillbill.di.*`, grouped by area.
  This covers the multi-adapter integration tests currently in
  `skillbill.application`, `skillbill.telemetry`, `skillbill.contracts.review`,
  `skillbill.review.review`, `skillbill.di.absent`, and `skillbill.di.runtime`.
- Apply the placement rule to any test added since 2026-09-22: the subject's
  module owns the test, and runtime-core owns composition and multi-adapter
  integration tests.

Documentation:

- Replace every reference to
  `runtime-kotlin/runtime-core/src/test/kotlin/skillbill/architecture/` in
  `../../../AGENTS.md`, `docs/code-principles.md`, `runtime-kotlin/ARCHITECTURE.md`, and
  `runtime-kotlin/agent/history.md`. Some suite sources also embed that path;
  update those.
- State the test placement rule in `ARCHITECTURE.md`.

## Acceptance Criteria

1. runtime-core `src/test` has no `architecture` package, and runtime-core's
   `repoTest` task runs the architecture suite.
2. Changing any file under a runtime-kotlin module's `src`, any `*.gradle.kts`,
   `ARCHITECTURE.md`, or `agent/**` invalidates runtime-core's `repoTest` task.
3. Installer and launcher shell tests run in `:runtime-cli:repoTest`, and the
   `../../../install.sh` substring test is gone.
4. Each relocated single-subject test runs in the module listed in scope.
5. Every runtime-core `src/test` file is in a `skillbill.di.*` package and either
   constructs `RuntimeComponent` or uses two or more real adapters.
6. `settings.gradle.kts` lists the same modules as before this subtask.
7. `../../../AGENTS.md`, `docs/code-principles.md`, `ARCHITECTURE.md`, and
   `agent/history.md` reference only existing test paths.

## Non-goals

- Rule changes or test deletions beyond the one installer substring test
  (subtask 2).
- Rewriting multi-adapter integration tests with fakes, or moving them out of
  runtime-core.
- Changing `governedRepositorySources()` for other modules.

## Dependency notes

Depends on subtask 2. Move the suite from the path it has now, including guard edits that are already in `src/test`. Do not wait for another issue to finish editing those tests.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check`, which runs runtime-core and runtime-cli
  `repoTest`.
- Record `@Test` totals per module and source set before and after. The sums match
  except for the one deleted installer test.
- Cache invalidation:
  - Add a `//` comment to a runtime-application main file and run
    `./gradlew :runtime-core:repoTest`. The task executes and fails; revert.
  - Change whitespace in `../../../install.sh`; `:runtime-cli:repoTest` executes; revert.
  - Rerun without changes; both tasks report `UP-TO-DATE`.

## Next path

Run `skill-bill goal SKILL-373`; this is the last subtask.
