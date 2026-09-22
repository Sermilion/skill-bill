# SKILL-368 subtask 2 - Governed resources typed task and Java guard ownership

## Scope

Parent findings 1, 2, and 3. Rebuild the governed-resource convention around one
typed task and a defaults-first DSL, move the Java guard to the module that ships
it, and replace the nested-Gradle parity test with build-logic tests.

Governed resources (`GovernedResourcesConventionPlugin.kt`,
`GovernedResourcesExtension.kt`):

- Add a `GovernedResourceCopy : DefaultTask` with `@InputFiles source`
  (a file collection so Gradle does not pre-validate existence), `@Input owner`,
  `@OutputDirectory destination`, and an action that fails with
  `"<owner> is missing at <absolute source path>."` when the source is not a
  regular file, then copies through `FileSystemOperations`. One task per entry,
  no separate validate task.
- Extension: `sourceRoot: DirectoryProperty` (default repository root, the
  parent of `runtime-kotlin`), `destination: Property<String>` (shared default),
  and `copy(taskName, source, owner, destination = shared)` plus an optional
  `source` override that resolves against the repository root. Register tasks as
  entries are declared; no `afterEvaluate`, no `zip` correlation, no message
  template, no `$schemaPath`/`$guardPath`/`$contractPath` substitution, no
  `sourceFromRuntimeKotlinProject`, no `requireSourceIsFile`.
- `processResources` depends on every governed copy task; the generated root is
  added to the `main` resources source set. Drop the `includeInTestProcessResources`
  distinction unless a consumer outside `GovernedResourceCopyParityTest` depends
  on it; the investigation found none. If it is kept, name the consumer in
  `runtime-kotlin/agent/decisions.md`.
- Rewrite the three consumer blocks (`runtime-infra/{host,contracts,workflow}/build.gradle.kts`).
  In `contracts`, delete `GovernedResourceSpec`, `governedResourceSpec`, and the
  pipe-delimited list; declare `sourceRoot` as `orchestration/contracts` and
  `destination` as `skillbill/infrastructure/contracts` once, with per-entry
  overrides for `review-context-schema.yaml` (`skillbill/contracts`). Keep every
  existing task name so `tasks --all` output and any `dependsOn` by name remain
  valid. Owner strings keep their text; they are failure context, not messages
  the plugin authors.

Java guard ownership (`StartScriptJavaGuard.kt`, `RuntimeImageConventionPlugin.kt`,
`runtime-infra/host/build.gradle.kts`, `GateJvmGuardPackagingTest.kt`,
`install.sh`):

- Move `skill-bill-java-guard.sh` to
  `runtime-infra/host/src/main/resources/skillbill/infrastructure/host/jvm/skill-bill-java-guard.sh`.
  It becomes an ordinary resource; delete the `copyJavaGuard` governed entry and
  `GateJvmGuardPackagingTest` (there is no second copy left to compare).
- `StartScriptJavaGuard` reads the guard from a `RegularFileProperty` defaulted
  by the runtime-image plugin to that path under the root project, declared as an
  input of each `CreateStartScripts` task so a guard edit regenerates scripts.
  Keep the anchor, marker, and embedded-JRE checks and the loud failure.
- Update `BUILD_JVM_GUARD` in `install.sh` to the new path.
- Add a build-logic test that the start-script guard is inserted before the
  Gradle anchor and is idempotent on a second run.

Replace the parity test (`runtime-infra/contracts/src/test`):

- Delete `GovernedResourceCopyParityTest.kt` and
  `src/test/resources/governed-resource-manifest-main.json`.
- Add build-logic tests under `convention/src/test` using a synthetic project in
  a temp directory (Gradle TestKit with `GradleRunner`, or `ProjectBuilder` where
  no execution is needed): a missing source fails the copy task naming owner and
  path and writes nothing; a present source lands at
  `build/generated/<module>/<destination>/<file>` and `processResources` depends on
  the copy task; a second `processResources` is up to date. No test copies the
  repository tree or hashes real schemas.

Docs and decisions: record guard ownership and the test relocation in
`runtime-kotlin/agent/decisions.md`; update `docs/code-principles.md` if it names
moved paths; update the `.github/workflows/release.yml` comment only if a
referenced name changes.

## Acceptance Criteria

1. `GovernedResourcesConventionPlugin` registers exactly one `GovernedResourceCopy`
   task per entry, with annotated inputs and outputs, and no `afterEvaluate`,
   `zip`, or `validate*Source` task.
2. `GovernedResourcesExtension` has no `missingSourceMessageTemplate`, and no
   build-logic source substitutes `$schemaPath`, `$guardPath`, or `$contractPath`.
3. The three consumer `governedResources` blocks declare `sourceRoot` and
   `destination` once each; `runtime-infra/contracts/build.gradle.kts` contains no
   `GovernedResourceSpec`, `split("|")`, or `main-only` token.
4. Every task name listed in the deleted parity test's expectation list, except
   `copyJavaGuard`, still appears in `./gradlew :runtime-infra:<module>:tasks --all`.
5. `skill-bill-java-guard.sh` exists only under
   `runtime-infra/host/src/main/resources/skillbill/infrastructure/host/jvm/`;
   `install.sh` and `StartScriptJavaGuard` reference that path; `copyJavaGuard`
   and `GateJvmGuardPackagingTest` are gone.
6. Generated start scripts for `runtime-cli` and `runtime-mcp` contain
   `skill_bill_required_java_major=` exactly once after two consecutive
   `installDist` runs.
7. `GovernedResourceCopyParityTest.kt` and `governed-resource-manifest-main.json`
   are deleted; build-logic tests cover missing-source failure, copy placement,
   `processResources` wiring, and up-to-date behavior against a synthetic project.
8. No test under `runtime-kotlin/runtime-*` spawns `./gradlew`.

## Dependency notes

Depends on subtask 1 for the package layout and the typed-task pattern. The
runtime behavior of `GateJvmResolver` (classpath resource
`skillbill/infrastructure/host/jvm/skill-bill-java-guard.sh`) is unchanged; only
the authored location moves.

## Non-goals

- Changing guard script logic, `GateJvmResolver`, or the install flow beyond the
  path constant.
- Renaming governed copy tasks or changing generated resource destinations.
- Any runtime-image or quality change (subtask 1).

## Validation strategy

- `cd runtime-kotlin && ./gradlew check`, including `:runtime-infra:host:test`
  (`GateJvmResolver` still materializes the guard) and
  `:runtime-infra:contracts:test` now free of nested Gradle runs; note the
  wall-clock drop for that module in the commit body.
- `./gradlew :runtime-infra:contracts:processResources --rerun-tasks` then a
  second run reports the governed copy tasks up to date; generated files match
  their `orchestration/contracts` sources byte for byte (`cmp`).
- Temporarily rename one schema and run `processResources`: the failure names the
  owner and the absolute path and no output is written; restore the file.
- `./gradlew :runtime-cli:installDist :runtime-mcp:installDist` twice; grep the
  start scripts for the guard marker once.
- `./install.sh --from-source` on a machine with `JAVA_HOME` pointing at a Java 17
  install and `SKILL_BILL_JAVA_HOME` at Java 21 selects Java 21; the install smoke
  workflow passes.
- Bugs the new tests catch: a governed entry with an absent source copies nothing
  and the build stays green; a copy lands outside its destination;
  `processResources` runs without the copy; the guard is inserted twice or not at
  all after Gradle regenerates the launcher.

## Next path

Complete SKILL-368 through the normal goal flow.
