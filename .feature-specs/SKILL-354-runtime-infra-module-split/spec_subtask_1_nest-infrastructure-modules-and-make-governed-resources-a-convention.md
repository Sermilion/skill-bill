# SKILL-354 Subtask 1 - Nest infrastructure modules and make governed resources a convention

Parent spec: [.feature-specs/SKILL-354-runtime-infra-module-split/spec.md](./spec.md)
Issue key: SKILL-354

## Scope

Resolve F-002, F-004, and F-007 in [investigation.md](investigation.md).

Own `runtime-kotlin/settings.gradle.kts`, the root `build.gradle.kts` aggregation, the directories `runtime-infra-fs`, `runtime-infra-http`, and `runtime-infra-sqlite`, every `project(":runtime-infra-...")` reference in `runtime-core`, `runtime-application`, `runtime-engine`, `runtime-cli`, and `runtime-mcp`, `build-logic/convention` (`JvmLibraryConventionPlugin` and a new `GovernedResourcesConventionPlugin` with its registration in `build-logic/convention/build.gradle.kts`), the governed-copy section of the fs build script, `GovernedResourceCopyParityTest` and `src/test/resources/governed-resource-manifest-main.json`, `RuntimeModuleCatalog`, `RuntimeArchitectureTestSupport`, `PrincipleEnforcementInventory.moduleArchitectureScanCase`, `RuntimeCoreCompositionOnlyTest`, `ArchitectureBaselineSupport` and the twelve infrastructure baseline files, every other `runtime-core` architecture test that spells an infrastructure module id or path, and the module names in `runtime-kotlin/ARCHITECTURE.md`, `docs/code-principles.md`, `docs/internal-skills-architecture.md`, `docs/skill-source-generation.md`, and `docs/agent/history.md`.

Move the three modules to `runtime-infra/fs`, `runtime-infra/http`, and `runtime-infra/sqlite` with `git mv`, include them as `runtime-infra:fs`, `runtime-infra:http`, and `runtime-infra:sqlite`, and rewrite every project reference. Give the empty `:runtime-infra` parent whatever root aggregation needs (a `plugins { base }` script or `findByName` aggregation) and nothing else. In `JvmLibraryConventionPlugin`, set the archive base name to `<parent>-<name>` for nested projects so jars are `runtime-infra-fs`, not `fs`. Teach the catalog one mapping from module id to directory (`:` to `/`) and to baseline file stem (`:` to `-`), and apply it wherever a test resolves `"$moduleName/..."` today; rename the baseline files accordingly. Add a `skillbill.governed-resources` convention plugin whose extension accepts entries of `(taskName, repoRelativeSource, destinationDir, owner)` plus the existing flags, registers the validate and copy tasks under `build/generated/<module>`, and wires `processResources` and `processTestResources`; replace the 300-line list in the fs script with plugin calls that carry the same 33 entries as data; point `GovernedResourceCopyParityTest` at the new generated root and regenerate the golden manifest, diffing it against the old one to show only the path prefix changed. Drop `kotlinx-serialization-json` from the fs script. Update the named documents to the new ids and paths.

## Acceptance Criteria

1. `settings.gradle.kts` includes `runtime-infra:fs`, `runtime-infra:http`, and `runtime-infra:sqlite`; the directories `runtime-infra/fs`, `runtime-infra/http`, and `runtime-infra/sqlite` exist and `runtime-infra-fs`, `runtime-infra-http`, and `runtime-infra-sqlite` do not; `git log --follow` on a moved file shows its history.
2. Every `project(":runtime-infra-...")` reference in `runtime-kotlin` is rewritten to the nested path; `./gradlew check` on runtime-kotlin passes with the `:runtime-infra` parent present, including `:convention:check`.
3. Archives of nested modules are named `runtime-infra-<name>-<version>.jar`; `:runtime-cli:installDist` and `:runtime-mcp:installDist` produce lib directories containing those names and no bare `fs`, `http`, or `sqlite` jar.
4. `RuntimeModuleCatalog.declaredGradleModules` lists the nested ids; every architecture test in `runtime-core` that resolves a module directory or baseline does so through the catalog's id mapping; the twelve infrastructure baseline files are renamed with `-` in place of `:` and their contents are unchanged; `RuntimeGradleModuleLayeringTest`, `RuntimeAdapterDependencyAllowlistTest`, `RuntimeCoreCompositionOnlyTest`, and `RuntimeArchitectureDocumentationTest` pass.
5. `build-logic/convention` registers `skillbill.governed-resources`; the fs build script declares its 33 governed copies as data through the plugin with one message template; the script is under 200 lines; the area source sets remain until subtask 2.
6. `GovernedResourceCopyParityTest` passes against the regenerated golden manifest; a diff of old and new manifests shows only the generated-root and module-name prefixes changed and every content hash is identical.
7. The fs build script declares no `kotlinx-serialization-json` dependency and the module compiles.
8. `runtime-kotlin/ARCHITECTURE.md`, `docs/code-principles.md`, `docs/internal-skills-architecture.md`, `docs/skill-source-generation.md`, and `docs/agent/history.md` name `runtime-infra/fs`, `runtime-infra/http`, `runtime-infra/sqlite` and the nested project ids; no `runtime-infra-fs`, `runtime-infra-http`, or `runtime-infra-sqlite` string remains outside `.feature-specs/done` and `agent/history.md` entries that record past work.

## Non-goals

No source file inside the three modules changes package or content. No module split, no visibility change, no test relocation. No change to what is copied or where readers find it on the classpath: the generated root moves, the classpath resource paths do not.

## Dependency notes

Depends on: SKILL-353 landing in full, because SKILL-353 edits the fs build script's test dependencies and many files under the directory this subtask moves. Starts from the branch head after that goal.

## Validation strategy

Name the regression before each test: a project path the layering test resolves to a missing directory, a baseline read from the old file name, the parent project breaking root `check`, a jar named `fs.jar` in the runtime image, a governed copy whose task name or flags changed under the plugin, a manifest regenerated with a changed hash. Rely on `./gradlew check` on runtime-kotlin including `:convention:check` and `repoTest`, the two `installDist` tasks, the catalog-driven architecture tests, `GovernedResourceCopyParityTest`, and a manual diff of the golden manifests. Apply bill-unit-test-value-check to any changed test.

## Next path

Subtask 2 splits `runtime-infra/fs` into the five modules once this subtask has landed.

## Spec Path

.feature-specs/SKILL-354-runtime-infra-module-split/spec_subtask_1_nest-infrastructure-modules-and-make-governed-resources-a-convention.md
