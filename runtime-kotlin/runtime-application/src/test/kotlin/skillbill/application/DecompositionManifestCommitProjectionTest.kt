package skillbill.application
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.application.testDecompositionManifestValidator
import skillbill.workflow.decomposition.encodeManifestWireMap

import skillbill.application.decomposition.DECOMPOSITION_RUNTIME_ARTIFACT_KEY
import skillbill.application.decomposition.loadDecompositionManifest
import skillbill.application.decomposition.model.DecompositionManifestRuntimeUpdate
import skillbill.application.decomposition.model.DecompositionManifestWriteRequest
import skillbill.application.decomposition.decompositionPlanningResult
import skillbill.application.decomposition.decompositionPlanningSubtask
import skillbill.contracts.JsonCodec
import skillbill.model.toPath
import skillbill.workflow.decomposition.model.DecompositionManifest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DecompositionManifestCommitProjectionTest {
  @Test
  fun `pre-commit projection writes completed manifest before runtime commit sha is known`() {
    val repoRoot = Files.createTempDirectory("skillbill-runtime-pre-commit-projection")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    val subtaskSpec = parentSpecPath.parent.resolve("spec_subtask_1_foundation.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")
    Files.writeString(subtaskSpec, "---\nstatus: In Progress\n---\n\n# Foundation\n")
    val initial = writeIfDecomposed(
      DecompositionManifestWriteRequest(
        repoRoot = repoRoot,
        parentSpecPath = parentSpecPath,
        planningResult = decompositionPlanningResult(
          parentSpecPath = parentSpecPath.toString(),
          subtasks = listOf(
            decompositionPlanningSubtask(id = 1, name = "foundation", specPath = subtaskSpec.toString()),
          ),
        ),
        baseBranch = "main",
        featureBranch = "feature/SKILL-51-decomposition",
      ),
    )
    assertNotNull(initial)

    val preCommit = writeFromWorkflowUpdate(
      repoRoot = repoRoot,
      existingArtifactsJson = durableRuntimeArtifactsJson(initial.manifest, subtaskSpec),
      artifactsPatch = WorkflowArtifactPatch.from(mapOf("commit_push_result" to mapOf("pre_commit_projection" to true))),
      runtimeUpdate = DecompositionManifestRuntimeUpdate(
        workflowId = "wfl-subtask-1",
        workflowStatus = "running",
        currentStepId = "commit_push",
        stepUpdates = WorkflowStepUpdates.from(listOf(mapOf("step_id" to "commit_push", "status" to "running", "attempt_count" to 1))),
      ),
    )

    assertNotNull(preCommit)
    val projectedBeforeCommit = preCommit.manifest.subtasks.single { it.id == 1 }
    assertEquals("complete", projectedBeforeCommit.status)
    assertEquals(null, projectedBeforeCommit.commitSha)
    assertContains(Files.readString(subtaskSpec), "status: In Progress")
    val manifestTextBeforeSha = Files.readString(preCommit.manifestPath.toPath())

    val final = writeFromWorkflowUpdate(
      repoRoot = repoRoot,
      existingArtifactsJson = durableRuntimeArtifactsJson(preCommit.manifest, subtaskSpec),
      artifactsPatch = WorkflowArtifactPatch.from(mapOf("commit_push_result" to mapOf("commit_sha" to "commit-subtask-1"))),
      runtimeUpdate = DecompositionManifestRuntimeUpdate(
        workflowId = "wfl-subtask-1",
        workflowStatus = "running",
        currentStepId = "commit_push",
        stepUpdates = WorkflowStepUpdates.from(listOf(mapOf("step_id" to "commit_push", "status" to "completed", "attempt_count" to 1))),
      ),
    )

    assertNotNull(final)
    assertEquals(manifestTextBeforeSha, Files.readString(final.manifestPath.toPath()))
    val projectedAfterSha = final.manifest.subtasks.single { it.id == 1 }
    assertEquals("complete", projectedAfterSha.status)
    assertEquals(null, projectedAfterSha.commitSha)
  }

  @Test
  fun `runtime update does not project commit push sha into decomposition manifest`() {
    val repoRoot = Files.createTempDirectory("skillbill-runtime-commit-sha-projection")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    val subtaskSpec = parentSpecPath.parent.resolve("spec_subtask_1_foundation.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")
    val initial = writeIfDecomposed(
      DecompositionManifestWriteRequest(
        repoRoot = repoRoot,
        parentSpecPath = parentSpecPath,
        planningResult = decompositionPlanningResult(
          parentSpecPath = parentSpecPath.toString(),
          subtasks = listOf(
            decompositionPlanningSubtask(id = 1, name = "foundation", specPath = subtaskSpec.toString()),
          ),
        ),
        baseBranch = "main",
        featureBranch = "feature/SKILL-51-decomposition",
      ),
    )
    assertNotNull(initial)

    val result = writeFromWorkflowUpdate(
      repoRoot = repoRoot,
      existingArtifactsJson = durableRuntimeArtifactsJson(initial.manifest, subtaskSpec),
      artifactsPatch = WorkflowArtifactPatch.from(mapOf("commit_push_result" to mapOf("commit_sha" to "commit-subtask-1"))),
      runtimeUpdate = DecompositionManifestRuntimeUpdate(
        workflowId = "wfl-subtask-1",
        workflowStatus = "completed",
        currentStepId = "finish",
        stepUpdates = WorkflowStepUpdates.from(listOf(mapOf("step_id" to "finish", "status" to "completed", "attempt_count" to 1))),
      ),
    )

    assertNotNull(result)
    val subtask = result.manifest.subtasks.single { it.id == 1 }
    assertEquals("complete", subtask.status)
    assertEquals(null, subtask.commitSha)
  }

  @Test
  fun `workflow-state projection keeps runtime commit sha out of git-tracked manifest`() {
    val repoRoot = Files.createTempDirectory("skillbill-runtime-commit-sha-file-projection")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    val subtaskSpec = parentSpecPath.parent.resolve("spec_subtask_1_foundation.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")
    val initial = writeIfDecomposed(
      DecompositionManifestWriteRequest(
        repoRoot = repoRoot,
        parentSpecPath = parentSpecPath,
        planningResult = decompositionPlanningResult(
          parentSpecPath = parentSpecPath.toString(),
          subtasks = listOf(
            decompositionPlanningSubtask(id = 1, name = "foundation", specPath = subtaskSpec.toString()),
          ),
        ),
        baseBranch = "main",
        featureBranch = "feature/SKILL-51-decomposition",
      ),
    )
    assertNotNull(initial)
    val runtimeManifest = initial.manifest.copy(
      subtasks = initial.manifest.subtasks.map { subtask ->
        subtask.copy(status = "complete", commitSha = "commit-subtask-1", workflowId = "wfl-subtask-1")
      },
    )

    val result = writeProjectionFromWorkflowState(
      repoRoot = repoRoot,
      artifactsJson = durableRuntimeArtifactsJson(runtimeManifest, subtaskSpec),
    )

    assertNotNull(result)
    val projected = result.manifest.subtasks.single { it.id == 1 }
    assertEquals("complete", projected.status)
    assertEquals(null, projected.commitSha)
    val loaded = loadDecompositionManifest(result.manifestPath.toPath())
    assertEquals(null, loaded.subtasks.single { it.id == 1 }.commitSha)
  }

  private fun durableRuntimeArtifactsJson(manifest: DecompositionManifest, subtaskSpec: Path): String =
    JsonCodec.mapToJsonString(
      mapOf(
        DECOMPOSITION_RUNTIME_ARTIFACT_KEY to testDecompositionManifestValidator.encodeManifestWireMap(manifest),
        "assessment" to mapOf("spec_path" to subtaskSpec.toString()),
        "goal_continuation" to mapOf(
          "issue_key" to "SKILL-51",
          "subtask_id" to 1,
          "suppress_pr" to true,
        ),
      ),
    )
}
