package skillbill.infrastructure.contracts

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovernedResourceCopyParityTest {
  private val governedResourceModules = listOf("host", "contracts", "workflow")
  private lateinit var fixtureRoot: Path

  @BeforeAll
  fun prepareIsolatedBuild() {
    fixtureRoot = Files.createTempDirectory("governed-resource-build")
    val sourceRoot = locateSourceRepoRoot()
    listOf("runtime-kotlin", "orchestration").forEach { directory ->
      sourceRoot.resolve(directory).toFile().walkTopDown()
        .onEnter { it.name !in setOf("build", ".gradle", ".kotlin") }
        .forEach { source ->
          val target = fixtureRoot.resolve(sourceRoot.relativize(source.toPath()))
          if (source.isDirectory) {
            Files.createDirectories(target)
          } else {
            Files.copy(source.toPath(), target, StandardCopyOption.COPY_ATTRIBUTES)
          }
        }
    }
    git(fixtureRoot, "init", "--initial-branch=main")
    git(fixtureRoot, "config", "commit.gpgsign", "false")
    git(
      fixtureRoot,
      "-c",
      "user.name=Resource fixture",
      "-c",
      "user.email=resource-fixture@test",
      "-c",
      "core.hooksPath=.git/hooks",
      "commit",
      "--allow-empty",
      "-m",
      "Initialize resource fixture",
    )
    git(fixtureRoot, "update-ref", "refs/remotes/origin/main", "HEAD")
    governedResourceModules.forEach { module ->
      runGradle(locateRuntimeKotlinRoot(), ":runtime-infra:$module:processResources")
    }
  }

  @AfterAll
  fun removeIsolatedBuild() {
    if (::fixtureRoot.isInitialized) fixtureRoot.toFile().deleteRecursively()
  }

  @Test
  fun `processResources output matches pre refactor golden manifest`() {
    val tree = resourceTreeHashes()
    assertEquals(loadGoldenManifest(), tree)
  }

  @Test
  fun `processTestResources alone skips main only schema copy tasks`() {
    val runtimeKotlin = locateRuntimeKotlinRoot()
    val mainOnly =
      setOf(
        "skillbill/infrastructure/contracts/rejected-output-diagnostic-schema.yaml",
        "skillbill/infrastructure/contracts/producer-output-evidence-schema.yaml",
      )
    generatedResourceRoots().forEach { it.toFile().deleteRecursively() }
    governedResourceModules.forEach { module ->
      runGradle(runtimeKotlin, ":runtime-infra:$module:processTestResources", "--rerun-tasks")
    }
    try {
      val tree = resourceTreeHashes()
      val expected = loadGoldenManifest().filterKeys { it !in mainOnly }
      assertEquals(expected, tree)
      mainOnly.forEach { path ->
        assertTrue(path !in tree.keys, "expected $path absent from test-only copy wiring")
      }
    } finally {
      governedResourceModules.forEach { module ->
        runGradle(runtimeKotlin, ":runtime-infra:$module:processResources", "--rerun-tasks")
      }
    }
  }

  @Test
  fun `incremental processResources keeps governed resource hashes`() {
    val runtimeKotlin = locateRuntimeKotlinRoot()
    generatedResourceRoots().forEach { it.toFile().deleteRecursively() }
    governedResourceModules.forEach { module ->
      runGradle(runtimeKotlin, ":runtime-infra:$module:processResources", "--rerun-tasks")
    }
    val afterFirst = resourceTreeHashes()
    assertEquals(loadGoldenManifest(), afterFirst)
    governedResourceModules.forEach { module ->
      runGradle(runtimeKotlin, ":runtime-infra:$module:processResources")
    }
    assertEquals(afterFirst, resourceTreeHashes())
  }

  @Test
  fun `missing workflow state schema fails with task attribution before copy output`() {
    val repoRoot = fixtureRoot
    val runtimeKotlin = locateRuntimeKotlinRoot()
    val schema = repoRoot.resolve("orchestration/contracts/workflow-state-schema.yaml")
    val backup =
      Files.createTempFile("workflow-state-schema", ".yaml").also {
        Files.delete(it)
      }
    Files.move(schema, backup)
    try {
      val result =
        runGradle(
          runtimeKotlin,
          ":runtime-infra:contracts:processResources",
          "--rerun-tasks",
          expectFailure = true,
        )
      assertNotEquals(0, result.exitCode)
      assertContains(result.output, "validateCopyWorkflowStateSchemaSource")
      assertContains(result.output, "SKILL-52")
      assertContains(result.output, schema.toString())
    } finally {
      Files.move(backup, schema)
    }
  }

  @Test
  fun `governed resource registration exposes every legacy copy task`() {
    val result =
      governedResourceModules
        .map { module ->
          runGradle(locateRuntimeKotlinRoot(), ":runtime-infra:$module:tasks", "--all").output
        }.joinToString("\n")
    val expected =
      listOf(
        "copyAgentAddonSchema",
        "copyJavaGuard",
        "copySpecialistContract",
        "copyReviewContextSchema",
        "copyPlatformPackSchema",
        "copyNativeAgentCompositionSchema",
        "copyNativeAgentLinkInventorySchema",
        "copyWorkflowStateSchema",
        "copyInstallPlanSchema",
        "copyDecompositionManifestSchema",
        "copyDecompositionManifestBundleJournalSchema",
        "copyGoalObservabilityEventSchema",
        "copyGoalProgressEventSchema",
        "copyIdeStatusSchema",
        "copyGoalSubtaskReviewStateSchema",
        "copyRejectedOutputDiagnosticSchema",
        "copyProducerOutputEvidenceSchema",
        "copyFeatureTaskRuntimeWorkerOwnershipSchema",
        "copyFeatureTaskExecutionIdentitySchema",
        "copyFeatureTaskRuntimePhaseOutputSchema",
        "copyFeatureTaskRuntimeHandoffEnvelopeSchema",
        "copyFeatureTaskRuntimePhaseLaunchBriefingSchema",
        "copyFeatureTaskRuntimePhaseHandoffSchema",
        "copyFeatureTaskRuntimePersistenceSchema",
        "copyFeatureTaskRuntimeProjectionMeasurementSchema",
        "copyFeatureTaskRuntimeSharedEvidenceProjectionSchema",
        "copyFeatureTaskRuntimeBuildReceiptSchema",
        "copyFeatureTaskRuntimeValidationEvidenceSchema",
        "copyGoalPlanningPreparationSchema",
        "copyFeatureTaskRuntimePlanningProjectionsSchema",
        "copyFeatureTaskRuntimeImplementationAttemptSchema",
        "copyFeatureTaskRuntimeCheckpointIdentitySchema",
        "copyFeatureTaskRuntimeQuarantineSchema",
        "copyExperimentDescriptorSchema",
        "copyExperimentPairSchema",
        "copyExperimentObservationSchema",
        "copyExperimentReportSchema",
      )
    expected.forEach { taskName ->
      assertContains(result, taskName)
    }
  }

  private data class GradleRunResult(val exitCode: Int, val output: String)

  private fun runGradle(
    runtimeKotlinRoot: Path,
    vararg tasks: String,
    expectFailure: Boolean = false,
  ): GradleRunResult {
    val process =
      ProcessBuilder(listOf("./gradlew") + tasks.toList())
        .directory(runtimeKotlinRoot.toFile())
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    if (!expectFailure) {
      assertEquals(0, exitCode, output)
    }
    return GradleRunResult(exitCode, output)
  }

  private fun generatedResourceRoots(): List<Path> = governedResourceModules.map { module ->
    locateRuntimeKotlinRoot().resolve("runtime-infra/$module/build/generated/$module")
  }

  private fun resourceTreeHashes(): Map<String, String> = generatedResourceRoots().flatMap { root ->
    if (!Files.isDirectory(root)) {
      error("Generated resource root missing at $root; run processResources first.")
    }
    Files.walk(root).use { paths ->
      paths
        .filter { Files.isRegularFile(it) }
        .map { file ->
          val relative = root.relativize(file).toString().replace('\\', '/')
          val hash =
            MessageDigest.getInstance("SHA-256")
              .digest(Files.readAllBytes(file))
              .joinToString("") { byte -> "%02x".format(byte) }
          relative to hash
        }
        .toList()
    }
  }.toMap()

  private fun loadGoldenManifest(): Map<String, String> {
    val stream =
      javaClass.getResourceAsStream("/governed-resource-manifest-main.json")
        ?: error("Golden manifest missing from test resources")
    val json = Json.parseToJsonElement(stream.bufferedReader().readText()) as JsonObject
    return json.mapValues { (_, value) -> value.jsonPrimitive.content }.toMap()
  }

  private fun locateRuntimeKotlinRoot(): Path = fixtureRoot.resolve("runtime-kotlin")

  private fun locateSourceRepoRoot(): Path {
    var current: Path? = Path.of("").toAbsolutePath().normalize()
    while (current != null) {
      if (Files.isRegularFile(current.resolve("runtime-kotlin/settings.gradle.kts"))) {
        return current
      }
      current = current.parent
    }
    error("Could not locate skill-bill repo root")
  }

  private fun git(repoRoot: Path, vararg args: String) {
    val process = ProcessBuilder(listOf("git", "-C", repoRoot.toString()) + args)
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "git ${args.joinToString(" ")} failed with $exitCode: $output" }
  }
}
