package skillbill.application
import skillbill.application.decomposition.DECOMPOSITION_RUNTIME_ARTIFACT_KEY
import skillbill.application.decomposition.DecompositionManifestWriter
import skillbill.application.decomposition.decompositionPlanningResult
import skillbill.application.decomposition.decompositionPlanningSubtask
import skillbill.application.decomposition.loadDecompositionManifest
import skillbill.application.decomposition.model.DecompositionManifestRuntimeUpdate
import skillbill.application.decomposition.model.DecompositionManifestWriteRequest
import skillbill.application.workflow.WorkflowService
import skillbill.application.workflow.model.WorkflowFamilyKind
import skillbill.application.workflow.model.WorkflowServiceOpenArgs
import skillbill.application.workflow.model.WorkflowUpdateRequest
import skillbill.contracts.JsonCodec
import skillbill.model.RepositoryRoot
import skillbill.model.toPath
import skillbill.ports.workflow.gitops.NoopWorkflowGitOperations
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.model.FeatureTaskExecutionIdentity
import skillbill.ports.workflow.model.FeatureTaskRouteScope
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.workflow.decomposition.runtime.DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY
import skillbill.workflow.decomposition.encodeManifestWireMap
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome
import skillbill.workflow.goal.NoopGoalObservabilityEventValidator
import skillbill.application.workflow.model.WorkflowOpenResult
import skillbill.application.workflow.model.WorkflowUpdateResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
      artifactsPatch = WorkflowArtifactPatch.from(
        mapOf("commit_push_result" to mapOf("pre_commit_projection" to true)),
      ),
      runtimeUpdate = DecompositionManifestRuntimeUpdate(
        workflowId = "wfl-subtask-1",
        workflowStatus = "running",
        currentStepId = "commit_push",
        stepUpdates = WorkflowStepUpdates.from(
          listOf(
            mapOf("step_id" to "commit_push", "status" to "running", "attempt_count" to 1),
          ),
        ),
      ),
    )

    assertNotNull(preCommit)
    val projectedBeforeCommit = preCommit.manifest.subtasks.single { it.id == 1 }
    assertEquals("complete", projectedBeforeCommit.status)
    assertEquals(null, projectedBeforeCommit.commitSha)
    assertContains(Files.readString(subtaskSpec), "status: In Progress")
    val manifestTextBeforeSha = Files.readString(preCommit.manifestPath.toPath())
    assertFinalCommitProjectionUnchanged(
      repoRoot,
      subtaskSpec,
      preCommit.manifest,
      manifestTextBeforeSha,
    )
  }

  private fun assertFinalCommitProjectionUnchanged(
    repoRoot: Path,
    subtaskSpec: Path,
    manifest: DecompositionManifest,
    manifestTextBeforeSha: String,
  ) {
    val final = writeFromWorkflowUpdate(
      repoRoot = repoRoot,
      existingArtifactsJson = durableRuntimeArtifactsJson(manifest, subtaskSpec),
      artifactsPatch = WorkflowArtifactPatch.from(
        mapOf("commit_push_result" to mapOf("commit_sha" to "commit-subtask-1")),
      ),
      runtimeUpdate = DecompositionManifestRuntimeUpdate(
        workflowId = "wfl-subtask-1",
        workflowStatus = "running",
        currentStepId = "commit_push",
        stepUpdates = WorkflowStepUpdates.from(
          listOf(
            mapOf("step_id" to "commit_push", "status" to "completed", "attempt_count" to 1),
          ),
        ),
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
      artifactsPatch = WorkflowArtifactPatch.from(
        mapOf("commit_push_result" to mapOf("commit_sha" to "commit-subtask-1")),
      ),
      runtimeUpdate = DecompositionManifestRuntimeUpdate(
        workflowId = "wfl-subtask-1",
        workflowStatus = "completed",
        currentStepId = "finish",
        stepUpdates = WorkflowStepUpdates.from(
          listOf(
            mapOf("step_id" to "finish", "status" to "completed", "attempt_count" to 1),
          ),
        ),
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

  @Test
  fun `projection failure after durable runtime update leaves existing manifest bytes intact`() {
    val repoRoot = Files.createTempDirectory("skillbill-runtime-projection-failure-intact")
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
    val manifestPath = initial.manifestPath.toPath()
    val manifestBefore = Files.readString(manifestPath)
    val failingStore = object : DecompositionManifestStore by TestDecompositionManifestStore {
      override fun writeTextAtomically(target: Path, content: String) {
        if (target == manifestPath) throw IOException("simulated projection write failure")
        TestDecompositionManifestStore.writeTextAtomically(target, content)
      }
    }
    val outcome = testDecompositionManifestWriter.writeProjectionFromWorkflowState(
      repoRoot = repoRoot,
      artifactsJson = durableRuntimeArtifactsJson(initial.manifest, subtaskSpec),
      validator = testDecompositionManifestValidator,
      fileStore = failingStore,
    )
    assertEquals(manifestBefore, Files.readString(manifestPath))
    assertIs<DecompositionManifestProjectionOutcome.Failed>(outcome)
    val recovered = testDecompositionManifestWriter.writeProjectionFromWorkflowState(
      repoRoot = repoRoot,
      artifactsJson = durableRuntimeArtifactsJson(initial.manifest, subtaskSpec),
      validator = testDecompositionManifestValidator,
      fileStore = TestDecompositionManifestStore,
    )
    assertIs<DecompositionManifestProjectionOutcome.Written>(recovered)
    loadDecompositionManifest(recovered.result.manifestPath.toPath())
  }

  @Test
  fun `projection retry preserves committed workflow state and child count`() {
    val repoRoot = Files.createTempDirectory("skillbill-runtime-projection-retry")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    val subtaskSpec = parentSpecPath.parent.resolve("spec_subtask_1_foundation.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")
    Files.writeString(subtaskSpec, "# Foundation\n")
    var failProjection = false
    val manifestPath = parentSpecPath.parent.resolve("decomposition-manifest.yaml")
    val fileStore = object : DecompositionManifestStore by TestDecompositionManifestStore {
      override fun writeTextAtomically(target: Path, content: String) {
        if (failProjection && target == manifestPath) throw IOException("simulated projection failure")
        TestDecompositionManifestStore.writeTextAtomically(target, content)
      }
    }
    val workflows = InMemoryWorkflowStates()
    val repositoryIdentity = "repo-root-realpath-v1:${repoRoot.toRealPath()}"
    workflows.saveFeatureTaskExecutionIdentity(
      FeatureTaskExecutionIdentity(
        workflowId = "child-existing",
        normalizedIssueKey = "SKILL-51",
        repositoryIdentity = repositoryIdentity,
        governedSpecPath = ".feature-specs/SKILL-51-decomposition/spec_subtask_1_foundation.md",
        mode = FeatureTaskWorkflowMode.RUNTIME,
        routeScope = FeatureTaskRouteScope.GOAL_CHILD,
      ),
    )
    val service = WorkflowService(
      database = FakeDatabaseSessionFactory(workflows),
      gitOperations = NoopWorkflowGitOperations,
      decompositionManifestStore = fileStore,
      workflowSnapshotValidator = testWorkflowSnapshotValidator,
      decompositionManifestValidator = testDecompositionManifestValidator,
      decompositionManifestWriter = DecompositionManifestWriter(),
      repositoryRoot = RepositoryRoot(repoRoot),
      goalObservabilityEventValidator = NoopGoalObservabilityEventValidator,
    )
    val opened = assertIs<WorkflowOpenResult.Ok>(
      service.open(
        WorkflowServiceOpenArgs(
          kind = WorkflowFamilyKind.TASK_RUNTIME,
          issueKey = "SKILL-51",
          repositoryIdentity = repositoryIdentity,
          governedSpecPath = ".feature-specs/SKILL-51-decomposition/spec.md",
        ),
      ),
    )
    val plan = decompositionPlanningResult(
      parentSpecPath = parentSpecPath.toString(),
      subtasks = listOf(
        decompositionPlanningSubtask(id = 1, name = "foundation", specPath = subtaskSpec.toString()),
      ),
    )
    service.update(
      WorkflowFamilyKind.TASK_RUNTIME,
      WorkflowUpdateRequest(
        workflowId = opened.workflowId,
        workflowStatus = "running",
        currentStepId = "plan",
        stepUpdates = WorkflowStepUpdates.from(
          listOf(mapOf("step_id" to "plan", "status" to "completed", "attempt_count" to 1)),
        ),
        artifactsPatch = WorkflowArtifactPatch.from(mapOf("plan" to plan.toPayload())),
        planningResult = plan,
      ),
    )
    val manifestBeforeFailure = Files.readString(manifestPath)
    val childrenBeforeFailure = workflows.countGoalChildIdentities("SKILL-51")
    failProjection = true

    val committedResult = service.update(
      WorkflowFamilyKind.TASK_RUNTIME,
      WorkflowUpdateRequest(
        workflowId = opened.workflowId,
        workflowStatus = "running",
        currentStepId = "implement",
        stepUpdates = WorkflowStepUpdates.from(
          listOf(mapOf("step_id" to "implement", "status" to "running", "attempt_count" to 1)),
        ),
        artifactsPatch = WorkflowArtifactPatch.from(
          mapOf("projection_probe" to mapOf("value" to "committed")),
        ),
      ),
    )
    assertIs<WorkflowUpdateResult.Ok>(committedResult)
    val afterFailure = requireNotNull(workflows.getFeatureTaskRuntimeWorkflow(opened.workflowId))
    assertContains(afterFailure.artifactsJson, "projection_probe")
    assertContains(afterFailure.artifactsJson, DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY)
    assertEquals("implement", afterFailure.currentStepId)
    assertEquals(manifestBeforeFailure, Files.readString(manifestPath))
    assertEquals(childrenBeforeFailure, workflows.countGoalChildIdentities("SKILL-51"))

    failProjection = false
    val retryOutcome = service.retryDecompositionManifestProjection(opened.workflowId)
    val written = assertIs<DecompositionManifestProjectionOutcome.Written>(retryOutcome)
    assertEquals(written.result.manifest, loadDecompositionManifest(manifestPath))
    val afterRetry = requireNotNull(workflows.getFeatureTaskRuntimeWorkflow(opened.workflowId))
    assertContains(afterRetry.artifactsJson, "projection_probe")
    assertTrue(DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY !in afterRetry.artifactsJson)
    assertEquals(afterFailure.currentStepId, afterRetry.currentStepId)
    assertEquals(childrenBeforeFailure, workflows.countGoalChildIdentities("SKILL-51"))
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
