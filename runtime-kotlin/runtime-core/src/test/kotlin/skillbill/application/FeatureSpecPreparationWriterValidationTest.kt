package skillbill.application

import skillbill.application.model.GoalRunnerStatusRequest
import skillbill.di.RuntimeComponent
import skillbill.di.create
import skillbill.featurespec.model.FeatureSpecPreparationDecision
import skillbill.featurespec.model.FeatureSpecPreparationMode
import skillbill.featurespec.model.FeatureSpecSubtaskPreparation
import skillbill.featurespec.model.FeatureSpecWriteRequest
import skillbill.infrastructure.fs.DecompositionManifestValidatorAdapter
import skillbill.infrastructure.fs.FileSystemDecompositionManifestFileStore
import skillbill.model.RuntimeContext
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeatureSpecPreparationWriterValidationTest {
  private val validator = DecompositionManifestValidatorAdapter()
  private val fileStore = FileSystemDecompositionManifestFileStore()
  private val writer = FeatureSpecPreparationWriter(validator, fileStore)

  @Test
  fun `decomposed preparation writes schema valid manifest and is goal readable`() {
    val repoRoot = Files.createTempDirectory("skillbill-feature-spec-goal-readable")
    val dbPath = repoRoot.resolve("metrics.db")
    val component =
      RuntimeComponent::class.create(
        RuntimeContext(
          dbPathOverride = dbPath.toString(),
          environment = emptyMap(),
          userHome = repoRoot,
        ),
      )

    val result = writer.write(
      repoRoot = repoRoot,
      request = FeatureSpecWriteRequest(
        decision = FeatureSpecPreparationDecision(
          issueKey = "SKILL-59",
          intendedOutcome = "decomposed",
          acceptanceCriteria = listOf("Write parent and subtask specs."),
          constraints = listOf("Reuse decomposition writer/validator seams."),
          nonGoals = listOf("Do not run implementation."),
          mode = FeatureSpecPreparationMode.DECOMPOSED,
        ),
        featureName = "feature-spec-horizontal-skill",
        parentSpecOverview = "Prepare runtime-owned decomposition artifacts.",
        validationStrategy = "bill-quality-check",
        subtasks = listOf(
          FeatureSpecSubtaskPreparation(
            id = 1,
            name = "foundation",
            scope = "Prepare typed write contracts.",
            acceptanceCriteria = listOf("Shared writer models exist."),
            nonGoals = listOf("No skill wiring."),
            dependencyNotes = "No dependencies.",
            validationStrategy = "bill-quality-check",
            nextPath = "Run bill-feature-implement on spec_subtask_1_foundation.md.",
            dependsOn = emptyList(),
          ),
          FeatureSpecSubtaskPreparation(
            id = 2,
            name = "runtime",
            scope = "Write specs and schema-valid manifest.",
            acceptanceCriteria = listOf("Manifest loads through goal status import."),
            nonGoals = listOf("No final integration wiring."),
            dependencyNotes = "Depends on subtask 1 contracts.",
            validationStrategy = "bill-quality-check",
            nextPath = "Run bill-feature-implement on spec_subtask_2_runtime.md.",
            dependsOn = listOf(1),
          ),
        ),
      ),
    )

    val manifestPath = repoRoot.resolve(result.decompositionManifestPath!!)
    assertTrue(Files.isRegularFile(manifestPath))
    val manifest = loadDecompositionManifest(manifestPath, fileStore, validator)
    assertEquals("SKILL-59", manifest.issueKey)
    assertEquals(2, manifest.subtasks.size)

    val goalStatus = component.goalRunnerStatusService.status(
      GoalRunnerStatusRequest(
        issueKey = "SKILL-59",
        invokedAgentId = "codex",
        dbPathOverride = dbPath.toString(),
        repoRoot = repoRoot,
      ),
    )
    assertNotNull(goalStatus)
    assertEquals("SKILL-59", goalStatus.issueKey)
    assertEquals(0, goalStatus.completeCount)
    assertEquals(2, goalStatus.pendingCount)
    assertEquals(0, goalStatus.blockedCount)
    assertEquals(1, goalStatus.currentSubtaskId)
  }
}
