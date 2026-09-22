# SKILL-368 subtask 1 - Plugin wiring, classpath, and quality conventions

## Scope

Clean the build-logic wiring layer without changing what the build produces.
Parent findings 4 through 13 except the governed-resource and Java-guard items,
which belong to subtask 2.

Plugin classpath and module build (`build-logic/convention/build.gradle.kts`,
`runtime-kotlin/build.gradle.kts`):

- Make the Kotlin, Spotless, and Detekt Gradle plugins `implementation`
  dependencies beside Badass Runtime. Remove the four `alias(...) apply false`
  lines from the root build file; it applies `skillbill.version` and `base` only.
  Keep the version catalog as the single version source.
- Replace `sourceCompatibility`, `targetCompatibility`, and the manual `jvmTarget`
  block with `kotlin { jvmToolchain(21) }`, sharing the `JDK_VERSION` value the
  conventions already mandate. The Kotlin 2.4.0-Beta2 configuration-cache opt-out
  stays.
- Remove the manual `check -> spotlessCheck` wiring here and in `Quality.kt`.

Formatter and linter (`Quality.kt`, `runtime-kotlin/.editorconfig`):

- Delete both ktlint `editorConfigOverride` maps. Set `max_line_length = 120` and
  the trailing-comma keys (`ij_kotlin_allow_trailing_comma`,
  `ij_kotlin_allow_trailing_comma_on_call_site`) in `runtime-kotlin/.editorconfig`
  so ktlint, detekt `MaxLineLength`, and the IDE agree.
- Remove `ratchetFrom("origin/main")`. Run `spotlessApply` once over the tree and
  include any resulting formatting in this commit; if the diff is large, list the
  touched paths in the commit body.

Runtime image (`RuntimeImageConventionPlugin.kt`, `RuntimeImageExtension.kt`,
`Sha256Sidecar.kt`, `StartScriptJavaGuard.kt`):

- Remove `afterEvaluate`. Derive the zip name with `imageBaseName.zip(...)` over a
  `provider { version.toString() }` and set `imageZip` from that provider. Register
  tasks eagerly with lazy inputs.
- Delete `stageRuntimeLicense`. Add the repository `LICENSE` to
  `distributions.named("main").contents` so `installDist` and Badass Runtime's
  `runtime` task carry it. Keep `verifyRuntimeImageLicense` as a typed task
  (`@InputFile` source, `@InputFiles` candidates) that fails on byte drift and
  runs before `runtimeZip`.
- Make the SHA-256 sidecar a typed task with `@InputFile` archive and
  `@OutputFile` sidecar; keep `notCompatibleWithConfigurationCache` because it
  follows `runtimeZip`. Make `SHA256_BUFFER_BYTES` private.
- Reduce `RuntimeImageExtension` to `imageBaseName`. Resolve the host token inside
  the plugin. Remove the configuration-time lifecycle log; keep the `doFirst`
  failure on image tasks for unsupported hosts, worded without a ticket key and
  using `resolveHostRuntimeToken` inputs rather than a second `System.getProperty`
  read. Remove `AC2` and `SKILL-55` from descriptions and messages.

Version plugin (`SkillBillVersionConventionPlugin.kt`): replace
`catch (_: Exception)` with a warning log that names the failure and the
`0.0.0-SNAPSHOT` fallback, then return the empty list.

Packaging and repoTest (`*ConventionPlugin.kt`, `Jvm.kt`,
`RuntimeGradleModuleLayeringTest.kt`, six module build files):

- Move the five plugin classes into `dev.skillbill.runtime.buildlogic` and update
  `implementationClass` entries. Update `RuntimeGradleModuleLayeringTest` to assert
  the observable nested archive name (for example through a `ProjectBuilder`
  project or the built jar name) instead of a source substring.
- Extract `configureRepoTestSourceSet` into a `skillbill.repo-test` plugin. Apply
  it in `runtime-mcp`, `runtime-cli`, `runtime-contracts`, `runtime-application`,
  `runtime-infra:contracts`, and `runtime-infra:skills`. Fail loudly if the plugin
  is applied to a module without `src/repoTest/kotlin`.

Tests (`build-logic/convention/src/test`): add `ProjectBuilder` tests that the
runtime-image plugin registers `runtimeZip` depending on license verification
and finalized by the sidecar task, and a unit test that the sidecar line is
`"<hex>  <name>\n"`.

Docs: update `docs/code-principles.md` Build And Tooling reference paths and add
decisions for the plugin-classpath strategy and the removed ratchet in
`runtime-kotlin/agent/decisions.md`.

## Acceptance Criteria

1. `RuntimeImageConventionPlugin.kt` and every other build-logic source contain
   no `afterEvaluate`.
2. `build-logic:convention` declares Kotlin, Spotless, Detekt, and Badass Runtime
   as `implementation`; `runtime-kotlin/build.gradle.kts` has no `apply false`.
3. `convention/build.gradle.kts` configures the JVM through `jvmToolchain(21)` and
   has no `sourceCompatibility`, `targetCompatibility`, or manual `jvmTarget`.
4. No `editorConfigOverride`, `ratchetFrom`, or manual `check -> spotlessCheck`
   wiring exists under `runtime-kotlin`; `.editorconfig` declares
   `max_line_length = 120` and the two trailing-comma keys.
5. `stageRuntimeLicense` is gone; the `main` distribution contents include the
   repository `LICENSE`; `verifyRuntimeImageLicense` and the sidecar task are
   typed `DefaultTask` subclasses with annotated inputs and outputs.
6. `RuntimeImageExtension` declares `imageBaseName` only, and no build-logic
   message or description contains `SKILL-` or `AC2`.
7. `gitListedVersionTags` logs a warning naming the fallback instead of swallowing
   the exception silently.
8. Every build-logic Kotlin file declares `package dev.skillbill.runtime.buildlogic`
   or a subpackage; `RuntimeGradleModuleLayeringTest` reads no plugin source text.
9. `skillbill.repo-test` is registered and applied by exactly the six modules
   with a `src/repoTest` tree; `Jvm.kt` contains no `isDirectory` probe.
10. Build-logic tests cover runtime-image task wiring and the sidecar line format.

## Dependency notes

None on other subtasks. Subtask 2 builds on the package move and the typed-task
pattern established here. The JDK 21 toolchain and the 2026-05-29 Badass Runtime
decisions remain in force.

## Non-goals

- Governed-resource plugin, consumer DSL, `GovernedResourceCopyParityTest`, and
  the Java guard location (subtask 2).
- Version bumps, `IMAGE_MODULES`, the guard anchor mechanism, root aggregate
  tasks, or the `subprojects {}` block.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check`.
- `./gradlew :runtime-cli:runtimeZip :runtime-mcp:runtimeZip`: both
  `build/install/<name>/LICENSE` and `build/image/LICENSE` exist,
  `verifyRuntimeImageLicense` passes, and `shasum -a 256` on the zip matches the
  sidecar's first field.
- `./gradlew :runtime-ports:check --dry-run` lists `:runtime-ports:detekt` and
  `:runtime-ports:spotlessCheck`.
- `git worktree add /tmp/sb-wt HEAD && (cd /tmp/sb-wt/runtime-kotlin && ./gradlew spotlessCheck)`
  passes; remove the worktree afterwards.
- Bugs the new tests catch: `runtimeZip` runs without license verification; the
  sidecar is not produced after the zip; the sidecar line format drifts so
  `install.sh` cannot verify a release asset.

## Next path

Continue with subtask 2 through the normal goal flow.
