package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeArchitectureDocumentationTest {
  private val runtimeRoot: Path = ArchitectureScanSupport.runtimeRoot

  private fun modulePath(
    moduleId: String,
    vararg segments: String,
  ): Path =
    segments.fold(
      runtimeRoot.resolve(RuntimeModuleCatalog.runtimeKotlinModuleDirectory(moduleId)),
    ) { path, segment -> path.resolve(segment) }

  @Test
  fun `architecture document declares package ownership and dependency direction`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))

    assertContains(architecture, "runtime-cli / runtime-mcp data gateways")
    assertContains(architecture, "-> runtime-application use cases")
    assertContains(architecture, "runtime-core")
    assertContains(architecture, "Package Ownership")
    assertContains(architecture, "Boundary Rules")
    assertContains(architecture, "Architecture Guardrails")
    assertContains(architecture, "MCP workflow calls must use application services")
    assertContains(architecture, "learning application use cases return typed results")
    assertContains(architecture, "repository and unit-of-work ports")
    assertContains(architecture, "LearningRecord is owned by the learnings domain")
    assertContains(architecture, "review parsing and triage decision normalization are pure surfaces")
    assertContains(architecture, "SQL-backed review persistence")
    assertContains(architecture, "TelemetrySettingsProvider")
    assertContains(architecture, "TelemetryConfigStore")
    assertContains(architecture, "TelemetryClient")
    assertContains(architecture, "telemetry proxy payload mapping belongs with the HTTP adapter")
    assertContains(architecture, "schema_migrations")
    assertContains(architecture, "versioned database migrations")
    assertContains(architecture, "contract DTOs")
    assertContains(architecture, "typed CLI presenter models")
    assertContains(architecture, "RuntimeContext")
    assertContains(architecture, "skillbill.model")
    assertContains(architecture, "`model` packages")
    assertContains(architecture, "runtime-ports")
    assertContains(architecture, "gradle-module-split-evaluation.md")
    assertContains(architecture, "Raw Map Boundary Rule")
    assertContains(architecture, "zero-tolerance")
    assertContains(architecture, "Destructive command failure policy")
    assertContains(architecture, "UninstallMutationRecorder")
    assertContains(architecture, "Port null-object classification")
    assertContains(architecture, "PortNullObjectAbsenceArchitectureTest")
    assertContains(architecture, "RuntimeContractModuleImportRulesTest")
    assertContains(architecture, "RuntimeCliAreaIsolationArchitectureTest")
    assertContains(architecture, "RuntimeCompositionGuardArchitectureTest")
    assertFalse(architecture.contains("compatibility umbrella"))
    assertFalse(architecture.contains("Near-Term Refactor Order"))
  }

  @Test
  fun `architecture document records the run loop boundary census`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))
    assertTrue(architecture.contains("The complete run-loop file census is pinned below."))
    assertTrue(
      Regex("""(?m)^\| Run-loop file \| current \| target \|$""").containsMatchIn(architecture),
    )
    assertEquals(
      23,
      Regex("""(?m)^\| `FeatureTaskRuntimeRunLoop[^`]*\.kt` \| \d+ \| \d+ \|$""")
        .findAll(architecture)
        .count(),
    )
  }

  @Test
  fun `architecture document declares the runtime contract and schema seams`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))

    assertContains(architecture, "Runtime Contract And Schema Seams")
    assertContains(architecture, "InstallPlanWireValidator")
    assertContains(architecture, "DecompositionManifestValidator")
    assertContains(architecture, "WorkflowSnapshotValidator")
    assertContains(architecture, "runtime-infra/contracts")
    assertContains(architecture, "skillbill.install.model.InstallPlanWireValidator")
    assertContains(architecture, "Decomposition-manifest schema validation is owned by")
    assertContains(architecture, "skillbill.workflow.decomposition.DecompositionManifestValidator")
    assertContains(architecture, "FeatureTaskRuntimeWireArtifactValidator")
    assertContains(architecture, "FeatureTaskRuntimeWireArtifactKind")
    assertContains(architecture, "type aliases to that same port")
    assertContains(architecture, "without default port bodies")
    assertContains(architecture, "FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys")
    assertContains(architecture, "WireVocabularyGovernedSeamInventory")
    assertContains(architecture, "decomposition bundle journal")
    assertContains(architecture, "governed markers")
    assertContains(architecture, "literal key instead of its owner")
    assertContains(architecture, "SkillBillVersion")
    assertContains(architecture, "getResourceAsStream")
    assertContains(architecture, "single documented")
    assertContains(architecture, "usesFeatureTaskRuntimeContinuation")
    assertContains(architecture, "skillbill.ports.workflow.decomposition.DecompositionManifestStore")
    assertContains(architecture, "FileSystemDecompositionManifestFileStore")
    assertContains(architecture, "Platform-pack manifest schema validation is owned by")
    assertContains(architecture, "Native-agent composition schema validation is owned by")
    assertContains(architecture, "Telemetry-event schema validation is owned by")
  }

  @Test
  fun `architecture document declares governed wire seams and current enforcement state`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))

    assertContains(architecture, "## Governed payload seams (mechanical scope)")
    assertContains(architecture, "Decomposition manifest")
    assertContains(architecture, "Workflow phase-output envelope")
    assertContains(architecture, "`produced_outputs` entry maps")
    assertContains(architecture, "does not prove every `String` in")
  }

  @Test
  fun `architecture document states the module scan-root convention and missing-root failure`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))

    assertContains(architecture, "ArchitectureScanSupport.runtimeRoot")
    assertContains(architecture, "RuntimeModuleCatalog.runtimeKotlinModuleDirectory")
    assertContains(architecture, "`runtime-kotlin/<module>/src/main/kotlin/`")
    assertContains(architecture, "Missing named roots fail the scan")
    listOf(
      "engineInboundApiViolations",
      "mainPackageRootsForModule",
      "ArchitectureScanSupport.kotlinFilesUnder",
      "ArchitectureScanSupport.authoredKotlinSourcesUnder",
      "ownership",
      "install-policy",
      "enforcement-hardening",
      "ports-declaration",
      "port-null-object",
      "contract-import",
      "skills-import",
    ).forEach { scanner -> assertContains(architecture, scanner) }
  }

  @Test
  fun `architecture document records the typed scaffold gateway and adapter map inventory`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))
    val scaffoldSection =
      architecture
        .substringAfter("## Scaffold Capability Ports And Pure-Policy Ownership")
        .substringBefore("## Architecture Guardrails")

    assertContains(scaffoldSection, "`ScaffoldGateway` in `skillbill.ports.scaffold` is the typed port")
    assertContains(scaffoldSection, "`runtime-cli` and `runtime-mcp` through `RuntimeComponent`")
    assertContains(scaffoldSection, "[2026-09-03]")
    assertContains(scaffoldSection, "SKILL-52.3 subtask 3 closed")

    listOf(
      "optionalBaselineLayers",
      "resolveAddonConsumerSkillDirs",
      "payload",
      "toRawScaffoldPayload",
      "appendAgentAddonFields",
      "appendHorizontalFields",
      "appendPlatformPackFields",
      "appendPlatformOverrideFields",
      "appendCodeReviewAreaFields",
      "appendAddOnFields",
      "validatePayloadVersion",
      "detectKind",
      "requireStringMap",
      "requireStringOrDefaultMap",
      "rejectBaselineLayersForNonPlatformPack",
      "resolvePlatformPackSelection",
      "rejectLegacyPlatformPackSelector",
      "resolvePlatformPackDefaults",
      "optionalSpecialistSubagents",
      "rejectLeafSubagentSpecialists",
      "validate",
      "assemblePlatformManifest",
      "extractCustomFields",
      "validatedCustomFields",
      "validateAgainstCanonicalSchema",
      "toPayload",
      "scaffoldWithAdapters",
      "resolveRepoRoot",
      "planScaffold",
      "planHorizontal",
      "planPlatformOverridePiloted",
      "planPlatformPack",
      "rejectPlatformPackSubagentOverrides",
      "planCodeReviewArea",
      "planAddOn",
      "planAgentAddon",
      "canonicalName",
      "optionalAddonLocationPath",
      "public`: `scaffold`",
    ).forEach { functionName ->
      assertTrue(
        scaffoldSection.contains(functionName),
        "Missing scaffold raw-map inventory entry: $functionName",
      )
    }
  }

  @Test
  fun `substance audit disposition keeps validation ownership after report task deletion`() {
    val skillsBuild = modulePath("runtime-infra:skills", "build.gradle.kts").readText()
    val decisions = Files.readString(runtimeRoot.resolve("runtime-kotlin/agent/decisions.md"))
    val substanceRoot =
      modulePath(
        "runtime-infra:skills",
        "src/main/kotlin/skillbill/infrastructure/skills/scaffold/substance",
      )
    val auditEntryPoint =
      modulePath(
        "runtime-infra:skills",
        "src/main/kotlin/skillbill/infrastructure/skills/scaffold/platformpack/substanceaudit/" +
          "PlatformPackSubstanceAudit.kt",
      )

    assertFalse(skillsBuild.contains("platformPackSubstanceReport"))
    assertFalse(Files.exists(substanceRoot))
    assertTrue(Files.isRegularFile(auditEntryPoint))
    assertContains(decisions, "`RepoValidationCollected` already calls `PlatformPackSubstanceAudit.audit`")
    assertContains(decisions, "Do not add a CLI report command")
  }

  @Test
  fun `infrastructure modules do not retain infra-fs area source sets or verification task`() {
    val infrastructureRoots =
      listOf(
        "runtime-infra:host",
        "runtime-infra:contracts",
        "runtime-infra:skills",
        "runtime-infra:launcher",
        "runtime-infra:workflow",
      )
    infrastructureRoots.forEach { moduleId ->
      val buildScript = modulePath(moduleId, "build.gradle.kts").readText()
      assertFalse(
        buildScript.contains("infraFs"),
        "$moduleId must not declare infra-fs area source sets.",
      )
      assertFalse(
        buildScript.contains("verifyInfraFsAreaCompile"),
        "$moduleId must not register verifyInfraFsAreaCompile.",
      )
      assertFalse(
        buildScript.contains("friendPaths"),
        "$moduleId must not declare friendPaths.",
      )
    }
  }

  @Test
  fun `boundary decisions record raw-map enforcement supersession`() {
    val decisions = Files.readString(runtimeRoot.resolve("runtime-kotlin/agent/decisions.md"))

    assertContains(decisions, "2026-09-14 — SKILL-52.5 subtask 7: zero-tolerance raw-map enforcement")
    assertContains(decisions, "Retire allow-list governance")
    assertContains(decisions, "Supersede 2026-05-29 — SKILL-52.3 subtask 4 item 2")
    assertContains(decisions, "Delete `@OpenBoundaryMap` from production")
  }

  @Test
  fun `package ownership matches runtime module catalog in both directions`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))
    val section = architecture.substringAfter("## Package Ownership").substringBefore("\n## ")
    val catalog = RuntimeModuleCatalog.declaredSubsystemPackages.toSet()
    val missingFromDoc = catalog.filter { pkg -> !ownershipSectionNamesPackage(section, pkg) }.toSet()
    val documentedRoots = catalog.filter { pkg -> ownershipSectionNamesPackage(section, pkg) }
    val extraDocRoots =
      section.lineSequence()
        .map { it.trim() }
        .filter { line -> line.startsWith("- `skillbill.") }
        .mapNotNull { line -> Regex("""`(skillbill(?:\.[a-z][a-z0-9]+)*)`""").find(line)?.groupValues?.get(1) }
        .map { pkg -> if (pkg.endsWith(".model")) pkg.removeSuffix(".model") else pkg }
        .filter { pkg ->
          catalog.none { catalogPkg ->
            pkg == catalogPkg ||
              pkg.startsWith("$catalogPkg.") ||
              catalogPkg.startsWith("$pkg.")
          }
        }
        .toSet()
    assertEquals(
      emptySet<String>(),
      missingFromDoc,
      "Packages in RuntimeModuleCatalog without Package Ownership entries: $missingFromDoc",
    )
    assertEquals(
      emptySet<String>(),
      extraDocRoots,
      "Package Ownership entries absent from RuntimeModuleCatalog: $extraDocRoots",
    )
    assertEquals(catalog, documentedRoots.toSet())
  }

  private fun ownershipSectionNamesPackage(
    section: String,
    pkg: String,
  ): Boolean = section.contains("- `$pkg`") || section.contains("- `$pkg.") || section.contains("- `$pkg and")

  @Test
  fun `runtime module declares final package boundaries`() {
    assertEquals(
      setOf(
        "skillbill.agent.model",
        "skillbill.agentaddon",
        "skillbill.application",
        "skillbill.cli",
        "skillbill.config",
        "skillbill.contracts",
        "skillbill.di",
        "skillbill.domain.skillremove",
        "skillbill.engine",
        "skillbill.error",
        "skillbill.experiment",
        "skillbill.experiment.model",
        "skillbill.featurespec",
        "skillbill.goalrunner",
        "skillbill.idestatus",
        "skillbill.infrastructure",
        "skillbill.install",
        "skillbill.learnings",
        "skillbill.mcp",
        "skillbill.model",
        "skillbill.ports",
        "skillbill.review",
        "skillbill.scaffold",
        "skillbill.telemetry",
        "skillbill.text",
        "skillbill.workflow",
        "skillbill.workflow.verify",
      ),
      RuntimeModuleCatalog.declaredSubsystemPackages.toSet(),
    )
  }

  @Test
  fun `architecture document settings and runtime module declare the same graph`() {
    val architecture = Files.readString(runtimeRoot.resolve("runtime-kotlin/ARCHITECTURE.md"))
    val settings = Files.readString(runtimeRoot.resolve("runtime-kotlin/settings.gradle.kts"))

    assertEquals(
      RuntimeModuleCatalog.declaredGradleModules,
      architecture.fencedTextListAfter("The Gradle module set is:"),
      "ARCHITECTURE.md Gradle module list must match RuntimeModuleCatalog.declaredGradleModules.",
    )
    assertEquals(
      RuntimeModuleCatalog.declaredGradleModules,
      settings.includedGradleModules(),
      "settings.gradle.kts include list must match RuntimeModuleCatalog.declaredGradleModules.",
    )
    assertEquals(
      RuntimeModuleCatalog.declaredSubsystemPackages.toSet(),
      architecture.fencedTextListAfter("The subsystem package set is:").toSet(),
      "ARCHITECTURE.md subsystem package list must match RuntimeModuleCatalog.declaredSubsystemPackages.",
    )
  }

  @Test
  fun `module split documentation uses nested infrastructure names`() {
    val repositoryRoot = ArchitectureScanSupport.runtimeRoot
    val documentationFiles =
      listOf(
        "runtime-kotlin/ARCHITECTURE.md",
        "docs/code-principles.md",
        "docs/internal-skills-architecture.md",
        "docs/skill-source-generation.md",
        "docs/agent/history.md",
      ).map { relativePath -> repositoryRoot.resolve(relativePath) }
    val directoryNames =
      listOf(
        "runtime-infra/host",
        "runtime-infra/contracts",
        "runtime-infra/skills",
        "runtime-infra/launcher",
        "runtime-infra/workflow",
        "runtime-infra/http",
        "runtime-infra/sqlite",
      )
    val projectIds =
      listOf(
        ":runtime-infra:host",
        ":runtime-infra:contracts",
        ":runtime-infra:skills",
        ":runtime-infra:launcher",
        ":runtime-infra:workflow",
        ":runtime-infra:http",
        ":runtime-infra:sqlite",
      )

    val documentation = documentationFiles.joinToString("\n", transform = Files::readString)
    directoryNames.forEach { directory -> assertContains(documentation, directory) }
    projectIds.forEach { projectId -> assertContains(documentation, projectId) }

    val legacyNames =
      listOf(
        "runtime-infra" + "-fs",
        "runtime-infra" + "-http",
        "runtime-infra" + "-sqlite",
      )
    val staleReferences =
      documentationFiles.filterNot { it.fileName.toString() == "history.md" }.flatMap { path ->
        val text = Files.readString(path)
        legacyNames
          .filter(text::contains)
          .map { name -> "${repositoryRoot.relativize(path)} contains $name" }
      }
    assertEquals(emptyList(), staleReferences)
  }

  private fun String.fencedTextListAfter(marker: String): List<String> {
    val markerIndex = indexOf(marker)
    require(markerIndex >= 0) { "Missing marker '$marker'." }
    val fenceStart = indexOf("```text", startIndex = markerIndex)
    require(fenceStart >= 0) { "Missing text fence after '$marker'." }
    val contentStart = indexOf('\n', startIndex = fenceStart) + 1
    val fenceEnd = indexOf("```", startIndex = contentStart)
    require(fenceEnd >= 0) { "Missing closing fence after '$marker'." }
    return substring(contentStart, fenceEnd)
      .lineSequence()
      .map(String::trim)
      .filter(String::isNotBlank)
      .toList()
  }

  private fun String.includedGradleModules(): List<String> {
    val includeBlocks =
      Regex("""include\((?<body>.*?)\)""", RegexOption.DOT_MATCHES_ALL)
        .findAll(this)
        .map { match -> match.groups["body"]?.value.orEmpty() }
        .toList()
    require(includeBlocks.isNotEmpty()) { "settings.gradle.kts must declare at least one include(...) block." }
    return Regex(""""([^"]+)"""")
      .findAll(includeBlocks.joinToString(separator = "\n"))
      .map { it.groupValues[1] }
      .toList()
  }
}
