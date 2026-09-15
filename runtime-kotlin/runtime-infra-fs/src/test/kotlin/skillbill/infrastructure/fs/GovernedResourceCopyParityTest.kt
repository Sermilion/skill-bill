package skillbill.infrastructure.fs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Execution(ExecutionMode.SAME_THREAD)
class GovernedResourceCopyParityTest {
  @Test
  fun `processResources output matches pre refactor golden manifest`() {
    val tree = resourceTreeHashes(generatedResourceRoot())
    assertEquals(loadGoldenManifest(), tree)
  }

  @Test
  fun `processTestResources alone skips main only schema copy tasks`() {
    val runtimeKotlin = locateRuntimeKotlinRoot()
    val mainOnly =
      setOf(
        "skillbill/infrastructure/fs/contracts/rejected-output-diagnostic-schema.yaml",
        "skillbill/infrastructure/fs/contracts/producer-output-evidence-schema.yaml",
      )
    generatedResourceRoot().toFile().deleteRecursively()
    runGradle(runtimeKotlin, ":runtime-infra-fs:processTestResources", "--rerun-tasks")
    try {
      val tree = resourceTreeHashes(generatedResourceRoot())
      val expected = loadGoldenManifest().filterKeys { it !in mainOnly }
      assertEquals(expected, tree)
      mainOnly.forEach { path ->
        assertTrue(path !in tree.keys, "expected $path absent from test-only copy wiring")
      }
    } finally {
      runGradle(runtimeKotlin, ":runtime-infra-fs:processResources", "--rerun-tasks")
    }
  }

  @Test
  fun `incremental processResources keeps governed resource hashes`() {
    val runtimeKotlin = locateRuntimeKotlinRoot()
    generatedResourceRoot().toFile().deleteRecursively()
    runGradle(runtimeKotlin, ":runtime-infra-fs:processResources", "--rerun-tasks")
    val afterFirst = resourceTreeHashes(generatedResourceRoot())
    assertEquals(loadGoldenManifest(), afterFirst)
    runGradle(runtimeKotlin, ":runtime-infra-fs:processResources")
    assertEquals(afterFirst, resourceTreeHashes(generatedResourceRoot()))
  }

  @Test
  fun `missing workflow state schema fails with task attribution before copy output`() {
    val repoRoot = locateRepoRoot()
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
          ":runtime-infra-fs:processResources",
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
    val result = runGradle(locateRuntimeKotlinRoot(), ":runtime-infra-fs:tasks", "--all")
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
      )
    expected.forEach { taskName ->
      assertContains(result.output, taskName)
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

  private fun generatedResourceRoot(): Path = locateRuntimeKotlinRoot()
    .resolve("runtime-infra-fs/build/generated/skillbill-infrastructure-fs")

  private fun resourceTreeHashes(root: Path): Map<String, String> {
    if (!Files.isDirectory(root)) {
      error("Generated resource root missing at $root; run processResources first.")
    }
    return Files.walk(root).use { paths ->
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
        .toMap()
    }
  }

  private fun loadGoldenManifest(): Map<String, String> {
    val stream =
      javaClass.getResourceAsStream("/governed-resource-manifest-main.json")
        ?: error("Golden manifest missing from test resources")
    val json = Json.parseToJsonElement(stream.bufferedReader().readText()) as JsonObject
    return json.mapValues { (_, value) -> value.jsonPrimitive.content }.toMap()
  }

  private fun locateRuntimeKotlinRoot(): Path = locateRepoRoot().resolve("runtime-kotlin")

  private fun locateRepoRoot(): Path {
    var current: Path? = Path.of("").toAbsolutePath().normalize()
    while (current != null) {
      if (Files.isRegularFile(current.resolve("runtime-kotlin/settings.gradle.kts"))) {
        return current
      }
      current = current.parent
    }
    error("Could not locate skill-bill repo root")
  }
}
