# SKILL-368 - Build-logic architecture cleanup

## Mode

decomposed

## Intended outcome

`../../../runtime-kotlin/build-logic` is the convention-plugin layer of the runtime build:
five plugins (`skillbill.version`, `skillbill.jvm-library`, `skillbill.quality`,
`skillbill.runtime-image`, `skillbill.governed-resources`) consumed by fourteen
modules. The module is small (about 970 lines) and its pure helpers (version
derivation, host token, license copy, SHA-256) are already separated from Gradle
wiring. The 2026-09-22 investigation found the module shape is right and the
wiring layer carries avoidable complexity: eager `afterEvaluate` blocks, ad-hoc
`doLast` tasks that bypass Gradle's input/output model, a runtime asset owned by
build tooling, a governed-resource DSL verbose enough that one consumer wrote a
pipe-delimited mini-parser around it, formatter configuration declared three
times with disagreeing values, and a nested-Gradle golden test inside a runtime
module that pins schema hashes.

The outcome is a build-logic module where plugin classes are thin adapters over
typed tasks and lazy providers, build tooling consumes product assets instead of
owning them, one source declares each setting, and plugin behavior is tested
beside the plugins against a synthetic project. No new abstraction layer, no new
plugin framework, no change to what the build produces.

## Findings

Ordered by impact. Each names the file and the observed behavior.

1. `GovernedResourceCopyParityTest` (`runtime-infra/contracts/src/test`) copies
   the `runtime-kotlin` and `orchestration` trees into a temp directory,
   initializes a git repo with a fabricated `origin/main` to satisfy the Spotless
   ratchet, and spawns about nine nested `./gradlew` processes during
   `:runtime-infra:contracts:test`. Its golden fixture
   `governed-resource-manifest-main.json` pins the SHA-256 of every governed
   schema, so every schema edit regenerates the fixture (SKILL-366 did). One
   assertion checks for the ticket key `SKILL-52` in Gradle output. This is
   build-plugin behavior tested from the wrong module at the wrong cost.
2. `skill-bill-java-guard.sh` is a runtime asset: `GateJvmResolver` executes it
   from the host jar, `../../../install.sh` sources it, and the start scripts embed it.
   It lives in `build-logic/convention/src/main/resources`. Three consumers reach
   into the build-tooling tree by path: the `copyJavaGuard` governed entry in
   `runtime-infra/host/build.gradle.kts` (the only user of
   `sourceFromRuntimeKotlinProject`), `GateJvmGuardPackagingTest`, and
   `install.sh`. Dependency direction is inverted: build tooling owns a product
   artifact and the product copies it back.
3. `GovernedResourcesConventionPlugin` configures inside `afterEvaluate`,
   registers two tasks per entry (an untyped validate task with no declared
   inputs plus a `Copy`), correlates them by `zip` index, calls
   `registeredEntries()` twice, and requires `missingSourceMessageTemplate`
   although all three consumers set the identical string while the plugin still
   substitutes four legacy placeholders (`$schemaPath`, `$guardPath`,
   `$contractPath`, `$sourcePath`) with one value. `GovernedResourceEntry` takes
   eight parameters; `runtime-infra/contracts/build.gradle.kts` encodes 37
   entries as `"task|source|dest|owner|main-only"` strings and parses them with
   `split("|")`. `requireSourceIsFile` toggles per entry although every source
   is a file.
4. `RuntimeImageConventionPlugin` uses `afterEvaluate` for values providers can
   carry lazily. `stageRuntimeLicense` writes into `build/image` and
   `build/install/<name>`, output directories owned by Badass Runtime's
   `runtime` task and `installDist` (overlapping outputs defeat up-to-date checks
   and caching), where `distributions.main.contents { from(LICENSE) }` places the
   file in both trees through the plugins' own flow. The license path triplet is
   computed twice. `logUnsupportedHost` logs at lifecycle level on every
   configuration of an unsupported host even when no image task runs.
   `RuntimeImageExtension` exposes `runtimeTargetTokens` and `hostRuntimeToken`
   that nothing outside the plugin reads. Ticket keys (`SKILL-55`, `AC2`) appear
   in user-facing messages and task descriptions.
5. Two plugin-classpath strategies coexist: Kotlin, Spotless, and Detekt are
   `compileOnly` in `convention/build.gradle.kts` and reach the classpath through
   `alias(...) apply false` in the root build file, while Badass Runtime is
   `implementation`. Gradle's documented shape for included convention builds is
   `implementation` for every plugin the conventions apply.
6. Formatter configuration is declared three times and disagrees: `Quality.kt`
   and `convention/build.gradle.kts` each carry an identical ktlint
   `editorConfigOverride` map with `max_line_length` 120,
   `../../../runtime-kotlin/.editorconfig` says 100, and detekt `MaxLineLength` says 120.
   ktlint reads `.editorconfig` natively.
7. `ratchetFrom("origin/main")` in `Quality.kt` makes `spotlessCheck` depend on
   a remote-tracking ref: it fails in linked worktrees (jgit does not resolve
   gitdir files), fails in clones without that ref, and forced finding 1 to
   fabricate `origin/main`.
8. `check` is wired to `spotlessCheck` by hand in both `Quality.kt` and
   `convention/build.gradle.kts`; Spotless (`enforceCheck` default) and Detekt
   already attach to `check`.
9. `SkillBillVersionConventionPlugin` swallows every exception from
   `git tag -l` with `catch (_: Exception)` and returns an empty list silently,
   which violates the fallback-emits-a-record policy in
   `../../../docs/observability-policy.md`.
10. Packaging is split: the five plugin classes and `GovernedResourcesExtension`
    sit in the default package; helpers sit in `dev.skillbill.runtime.buildlogic`.
    `RuntimeGradleModuleLayeringTest` in runtime-core asserts a source-text
    substring of `JvmLibraryConventionPlugin.kt` instead of the observable jar
    name.
11. `configureRepoTestSourceSet` in `Jvm.kt` decides plugin behavior by probing
    `src/repoTest/kotlin` at configuration time; six modules rely on that
    implicit contract.
12. `convention/build.gradle.kts` sets `sourceCompatibility`,
    `targetCompatibility`, and `jvmTarget` by hand instead of the JDK 21 toolchain
    the conventions mandate for every other module; the foojay resolver is
    already applied in its settings.
13. Tests cover `resolveSkillBillVersion` and `RuntimeImageLicense` only. No
    plugin has a wiring test. The sidecar format `"<hex>  <name>\n"` written by
    `Sha256Sidecar.kt` is parsed by `../../../install.sh` and the release workflow and is
    untested. `SHA256_BUFFER_BYTES` is public with no reader.

What holds up and stays unchanged: the plugin split by capability; pure functions
isolated from Gradle types (`DevSnapshotVersion`, `RuntimeTargets`,
`RuntimeImageLicense`, `Sha256Sidecar`); `configureKotlinJvm` as the single owner
of compiler and test defaults, guarded by `ConventionReapplicationArchitectureTest`;
`allWarningsAsErrors`; strict `validatePlugins`; the additive `IMAGE_MODULES`
decision of 2026-05-29; per-task configuration-cache opt-outs for Badass Runtime;
the start-script guard anchor with a loud failure when Gradle's template changes.

## Target design

- Plugin classes register typed tasks and wire lazy providers. No `afterEvaluate`
  in build-logic.
- Each output-producing task is a `DefaultTask` subclass with annotated inputs and
  outputs (`GovernedResourceCopy`, `Sha256Sidecar`, `VerifyRuntimeImageLicense`)
  so strict `validatePlugins` and up-to-date checks apply. Lifecycle wiring stays
  in the plugin.
- One plugin-classpath strategy: `implementation` in build-logic for every plugin
  the conventions apply; the root build file applies `skillbill.version` and
  `base` only.
- One formatter configuration source: `../../../runtime-kotlin/.editorconfig` at 120
  columns, read by ktlint through Spotless and by the IDE. No
  `editorConfigOverride`, no ratchet.
- Build tooling consumes product assets and owns none. The Java guard is a plain
  resource of `runtime-infra/host`; build-logic reads it as a declared file input.
- Governed-resource DSL: the extension declares shared defaults once
  (`sourceRoot`, `destination`); each entry is `copy(taskName, source, owner)`
  with optional per-entry `destination` and `source` overrides resolved against the
  repository root.
- Every swallowed failure logs a warning that names the fallback taken.
- Plugin behavior tests live in `build-logic/convention/src/test` against a
  synthetic project (`ProjectBuilder` for wiring; Gradle TestKit only where task
  execution is asserted). Runtime modules do not spawn Gradle.

## Acceptance Criteria

1. `../../../runtime-kotlin/build-logic` contains no `afterEvaluate` call.
2. `GovernedResourcesConventionPlugin` registers one typed task per entry; a
   missing source fails that task with a message naming the entry owner and the
   resolved source path, before any output is written.
3. The three `governedResources` consumer blocks declare shared defaults once and
   contain no string encoding or `split` parsing; `runtime-infra/contracts/build.gradle.kts`
   has no `GovernedResourceSpec` type.
4. `skill-bill-java-guard.sh` exists once, under `runtime-infra/host/src/main/resources`,
   and `build-logic/convention/src/main/resources` no longer contains it; `../../../install.sh`,
   the start-script guard, and the host runtime read that one file.
5. `stageRuntimeLicense` no longer exists; `LICENSE` reaches `build/install/<name>/`
   and `build/image/` through the `main` distribution contents, and
   `verifyRuntimeImageLicense` still fails on byte drift.
6. `RuntimeImageExtension` exposes `imageBaseName` only; host-token resolution is
   internal to the plugin and no lifecycle log fires at configuration time on an
   unsupported host.
7. Kotlin, Spotless, Detekt, and Badass Runtime are all `implementation`
   dependencies of `build-logic:convention`; the root build file has no
   `apply false` plugin aliases.
8. ktlint line length comes from `../../../runtime-kotlin/.editorconfig` (120) and no
   `editorConfigOverride` map or `ratchetFrom` call remains in the repository.
9. Neither `Quality.kt` nor `convention/build.gradle.kts` wires `spotlessCheck`
   into `check` by hand.
10. A failed or absent `git` during version resolution logs a warning naming the
    `0.0.0-SNAPSHOT` fallback.
11. All build-logic sources live under `dev.skillbill.runtime.buildlogic` (or a
    subpackage); `RuntimeGradleModuleLayeringTest` asserts the nested archive base
    name without reading plugin source text.
12. `repoTest` wiring is applied through an explicit `skillbill.repo-test` plugin
    by the six modules that have a `src/repoTest` tree; `Jvm.kt` no longer probes
    the filesystem.
13. `GovernedResourceCopyParityTest` and `governed-resource-manifest-main.json`
    are deleted; build-logic tests cover missing-source failure, copy placement,
    and `processResources` wiring against a synthetic project, plus the sidecar
    line format.
14. No ticket key or acceptance-criterion label appears in a build-logic
    user-facing message or task description.

## Executable scope

Two subtasks. The split condition: subtask 2 changes consumer-facing contracts
(the `governedResources` DSL in three modules and the guard location read by
`../../../install.sh` and the runtime jar) and replaces a runtime-module test suite, so it
is verified by the install smoke path and host packaging tests; subtask 1 is
internal to build-logic plus root wiring and is verified by `./gradlew check` and
an image build. Each commit stands alone.

1. Plugin wiring, classpath, and quality conventions
   (`spec_subtask_1_plugin-wiring-and-quality-conventions.md`).
2. Governed resources typed task and Java guard ownership
   (`spec_subtask_2_governed-resources-and-java-guard-ownership.md`), after 1.

## Dependency notes

- Read `../../../runtime-kotlin/ARCHITECTURE.md` Design Principles and
  `docs/code-principles.md` (Build And Tooling, Imports And Simple Names,
  Comments And Interface KDoc) before changing build-logic. Its Kotlin is subject
  to `CommentAndInterfaceKdocArchitectureTest`, `InlineFqnArchitectureTest`, and
  the 500-line production ceiling.
- Decision 2026-05-29 (Badass Runtime, additive `IMAGE_MODULES`) and the JDK 21
  toolchain decision stay in force.
- Record new decisions in `../../../runtime-kotlin/agent/decisions.md`: guard ownership,
  no ratchet, single plugin-classpath strategy, plugin tests live in build-logic.
- `../../../install.sh` line 52 and `.github/workflows/release.yml` comments reference
  build-logic paths and names; update them where a path moves.

## Non-goals

- Upgrading Kotlin, Gradle, Spotless, Detekt, or Badass Runtime, or changing
  `IMAGE_MODULES`. The Kotlin 2.4.0-Beta2 configuration-cache opt-out stays until
  a stable compiler lands; that is a separate version decision.
- Replacing the start-script guard anchor with a custom launcher template.
- Changing the repository taxonomy list in `governedRepositorySources`, the root
  `subprojects {}` group and version propagation, or the aggregate `detekt` and
  `spotlessCheck` root tasks.
- Changing runtime behavior of `GateJvmResolver` or the guard script's logic.
- Trimming `RuntimeImageLicenseTest` or adding tests for pure functions that
  already have them.

## Validation strategy

- `cd runtime-kotlin && ./gradlew check` runs build-logic's own `check` through
  the included build plus every module gate, including the runtime-core
  architecture tests.
- `./gradlew :runtime-cli:runtimeZip :runtime-mcp:runtimeZip` on a supported host:
  `LICENSE` is present in `build/install/<name>/LICENSE` and `build/image/LICENSE`,
  `verifyRuntimeImageLicense` passes, and `shasum -a 256` of the zip equals the
  first field of the `.sha256` sidecar.
- `./gradlew :runtime-ports:check --dry-run` lists `detekt` and `spotlessCheck`
  after the manual wiring is removed.
- `spotlessCheck` passes from a linked worktree created with `git worktree add`.
- After the guard move: `:runtime-infra:host:test` passes (`GateJvmResolver`
  still materializes the guard from the classpath), `./install.sh --from-source`
  resolves the build JVM through the guard, and the install smoke workflow's
  `installDist` path produces start scripts containing
  `skill_bill_required_java_major=`.
- Build-logic tests name these bugs: a governed entry whose source is absent
  copies nothing and the build stays green; a governed copy lands outside the
  declared destination; `processResources` does not depend on a governed copy
  task; the sidecar line drifts from `"<hex>  <name>"` and `../../../install.sh`
  verification fails; the runtime-image plugin registers image tasks without the
  license verification dependency.

## References

- `../../../runtime-kotlin/build-logic/convention/build.gradle.kts`
- `runtime-kotlin/build-logic/convention/src/main/kotlin/*.kt`
- `runtime-kotlin/build-logic/convention/src/main/kotlin/dev/skillbill/runtime/buildlogic/*.kt`
- `../../../runtime-kotlin/build.gradle.kts`, `runtime-kotlin/.editorconfig`
- `runtime-kotlin/runtime-infra/{host,contracts,workflow}/build.gradle.kts`
- `runtime-kotlin/runtime-infra/contracts/src/test/kotlin/skillbill/infrastructure/contracts/GovernedResourceCopyParityTest.kt`
- `runtime-kotlin/runtime-infra/host/src/test/kotlin/skillbill/infrastructure/host/jvm/GateJvmGuardPackagingTest.kt`
- `../../../runtime-kotlin/runtime-core/src/test/kotlin/skillbill/architecture/RuntimeGradleModuleLayeringTest.kt`
- `../../../install.sh`, `.github/workflows/release.yml`
- `../../../runtime-kotlin/agent/decisions.md` (2026-05-29 Badass Runtime; JDK 21 toolchain)
- `../../../docs/code-principles.md`, `docs/observability-policy.md`

## Next path

Run `skill-bill goal SKILL-368`.
