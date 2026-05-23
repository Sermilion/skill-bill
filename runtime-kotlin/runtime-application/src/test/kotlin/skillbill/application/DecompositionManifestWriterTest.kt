package skillbill.application

import skillbill.application.model.DecompositionManifestWriteRequest
import skillbill.workflow.DecompositionManifestCodec
import skillbill.workflow.WorkflowEngine
import skillbill.workflow.implement.FeatureImplementWorkflowDefinition
import skillbill.workflow.model.WorkflowUpdateInput
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DecompositionManifestWriterTest {
  @Test
  fun `decomposition planning result writes validated same branch manifest beside parent spec`() {
    val repoRoot = Files.createTempDirectory("skillbill-decomposition-manifest")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")

    val result = DecompositionManifestWriter.writeIfDecomposed(
      DecompositionManifestWriteRequest(
        repoRoot = repoRoot,
        parentSpecPath = parentSpecPath,
        planningResult = decompositionPlan(),
        baseBranch = "main",
        featureBranch = "feature/SKILL-51-decomposition",
      ),
    )

    assertNotNull(result)
    assertTrue(Files.isRegularFile(result.manifestPath))
    assertEquals(parentSpecPath.parent.resolve("decomposition-manifest.yaml"), result.manifestPath)

    val loaded = DecompositionManifestCodec.load(result.manifestPath)
    assertEquals("same_branch_commit_per_subtask", loaded.executionModel.wireValue)
    assertEquals("feature/SKILL-51-decomposition", loaded.featureBranch)
    assertEquals(emptyList(), loaded.stackBranches)
    assertEquals(1, loaded.currentSubtaskIntent.subtaskId)
    assertEquals("start", loaded.currentSubtaskIntent.action)
  }

  @Test
  fun `workflow update writes stacked branch manifest without same branch feature branch`() {
    val repoRoot = Files.createTempDirectory("skillbill-stacked-decomposition")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")

    val result = DecompositionManifestWriter.writeFromWorkflowUpdate(
      repoRoot = repoRoot,
      existingArtifactsJson = "{}",
      artifactsPatch = mapOf("plan" to stackedDecompositionPlan()),
    )

    assertNotNull(result)
    assertEquals("stacked_branches", result.manifest.executionModel.wireValue)
    assertEquals(null, result.manifest.featureBranch)
    assertEquals(
      listOf("feature/SKILL-51-01-foundation", "feature/SKILL-51-02-runtime"),
      result.manifest.stackBranches.map { it.branch },
    )
  }

  @Test
  fun `single spec implement plan does not require or write decomposition manifest`() {
    val repoRoot = Files.createTempDirectory("skillbill-single-spec")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-single/spec.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")

    val result = DecompositionManifestWriter.writeIfDecomposed(
      DecompositionManifestWriteRequest(
        repoRoot = repoRoot,
        parentSpecPath = parentSpecPath,
        planningResult = mapOf("mode" to "implement", "task_count" to 1),
        baseBranch = "main",
        featureBranch = "feature/SKILL-51-single",
      ),
    )

    assertEquals(null, result)
    assertFalse(Files.exists(parentSpecPath.parent.resolve("decomposition-manifest.yaml")))

    val definition = FeatureImplementWorkflowDefinition.definition
    val opened = WorkflowEngine.openRecord(definition, "fis-compat-001", "session-compat", "plan")
    val updated = WorkflowEngine.updateRecord(
      definition,
      opened,
      WorkflowUpdateInput(
        workflowStatus = "running",
        currentStepId = "implement",
        stepUpdates =
        listOf(
          mapOf("step_id" to "plan", "status" to "completed", "attempt_count" to 1),
          mapOf("step_id" to "implement", "status" to "running", "attempt_count" to 1),
        ),
        artifactsPatch = mapOf("plan" to mapOf("mode" to "implement", "task_count" to 1)),
        sessionId = "session-compat",
      ),
    )

    val payload = WorkflowEngine.fullPayload(definition, updated)
    val artifacts = payload["artifacts"] as Map<*, *>
    assertEquals(mapOf("mode" to "implement", "task_count" to 1), artifacts["plan"])
  }

  private fun decompositionPlan(): Map<String, Any?> = linkedMapOf(
    "mode" to "decompose",
    "parent_spec_path" to ".feature-specs/SKILL-51-decomposition/spec.md",
    "recommended_first_subtask_id" to 1,
    "subtasks" to
      listOf(
        linkedMapOf(
          "id" to 1,
          "name" to "Foundation",
          "spec_path" to ".feature-specs/SKILL-51-decomposition/spec_subtask_1_foundation.md",
          "depends_on" to emptyList<Int>(),
          "scope" to "Create contract foundation",
        ),
        linkedMapOf(
          "id" to 2,
          "name" to "Runtime",
          "spec_path" to ".feature-specs/SKILL-51-decomposition/spec_subtask_2_runtime.md",
          "depends_on" to listOf(1),
          "scope" to "Wire runtime writer",
        ),
      ),
  )

  private fun stackedDecompositionPlan(): Map<String, Any?> = linkedMapOf(
    "mode" to "decompose",
    "parent_spec_path" to ".feature-specs/SKILL-51-decomposition/spec.md",
    "execution_model" to "stacked_branches",
    "recommended_first_subtask_id" to 1,
    "stack_branches" to
      listOf(
        linkedMapOf("subtask_id" to 1, "branch" to "feature/SKILL-51-01-foundation", "base_branch" to "main"),
        linkedMapOf(
          "subtask_id" to 2,
          "branch" to "feature/SKILL-51-02-runtime",
          "base_branch" to "feature/SKILL-51-01-foundation",
        ),
      ),
    "subtasks" to
      listOf(
        linkedMapOf(
          "id" to 1,
          "name" to "Foundation",
          "spec_path" to ".feature-specs/SKILL-51-decomposition/spec_subtask_1_foundation.md",
          "depends_on" to emptyList<Int>(),
        ),
        linkedMapOf(
          "id" to 2,
          "name" to "Runtime",
          "spec_path" to ".feature-specs/SKILL-51-decomposition/spec_subtask_2_runtime.md",
          "depends_on" to listOf(1),
        ),
      ),
  )
}
