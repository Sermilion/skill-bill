package skillbill.application

import skillbill.application.model.DecompositionManifestWriteRequest
import skillbill.application.model.DecompositionManifestWriteResult
import skillbill.contracts.JsonSupport
import skillbill.workflow.DecompositionManifestCodec
import skillbill.workflow.model.CurrentSubtaskIntent
import skillbill.workflow.model.DecompositionExecutionModel
import skillbill.workflow.model.DecompositionManifest
import skillbill.workflow.writeDecompositionManifestText
import java.nio.file.Path

private const val DECOMPOSITION_MODE: String = "decompose"
private const val DECOMPOSITION_MANIFEST_FILENAME: String = "decomposition-manifest.yaml"

object DecompositionManifestWriter {
  fun writeFromWorkflowUpdate(
    repoRoot: Path,
    existingArtifactsJson: String,
    artifactsPatch: Map<String, Any?>?,
  ): DecompositionManifestWriteResult? {
    val plan = artifactsPatch?.get("plan").asStringAnyMapOrNull()
    return if (plan == null || plan["mode"] != DECOMPOSITION_MODE) {
      null
    } else {
      val existingArtifacts = JsonSupport.parseObjectOrNull(existingArtifactsJson)
        ?.let(JsonSupport::jsonElementToValue)
        ?.let(JsonSupport::anyToStringAnyMap)
        .orEmpty()
      val parentSpecPath = Path.of(parentSpecPath(plan))
      val branchName = branchName(artifactsPatch?.get("branch")).ifBlank { branchName(existingArtifacts["branch"]) }
      val executionModel = executionModel(plan)
      writeIfDecomposed(
        DecompositionManifestWriteRequest(
          repoRoot = repoRoot,
          parentSpecPath = parentSpecPath,
          planningResult = plan,
          baseBranch = plan["base_branch"]?.toString()?.takeIf(String::isNotBlank) ?: "main",
          featureBranch = when (executionModel) {
            DecompositionExecutionModel.SAME_BRANCH_COMMIT_PER_SUBTASK ->
              branchName.ifBlank { defaultFeatureBranch(parentSpecPath) }
            DecompositionExecutionModel.STACKED_BRANCHES -> null
          },
          executionModel = executionModel,
          stackBranches = parseStackBranches(plan),
        ),
      )
    }
  }

  fun maybeWriteFromWorkflowUpdate(
    repoRoot: Path,
    existingArtifactsJson: String,
    artifactsPatch: Map<String, Any?>?,
  ): Path? = writeFromWorkflowUpdate(repoRoot, existingArtifactsJson, artifactsPatch)?.manifestPath

  fun writeIfDecomposed(request: DecompositionManifestWriteRequest): DecompositionManifestWriteResult? {
    if (request.planningResult["mode"]?.toString().orEmpty() != DECOMPOSITION_MODE) {
      return null
    }
    return write(request)
  }

  fun write(request: DecompositionManifestWriteRequest): DecompositionManifestWriteResult {
    val manifestPath = resolvedParentSpecPath(request.repoRoot, request.parentSpecPath)
      .parent
      .resolve(DECOMPOSITION_MANIFEST_FILENAME)
    val manifest = request.toManifest()
    val yaml = DecompositionManifestCodec.encodeYaml(manifest)
    writeDecompositionManifestText(manifestPath, yaml)
    val loaded = DecompositionManifestCodec.load(manifestPath)
    return DecompositionManifestWriteResult(manifestPath = manifestPath, manifest = loaded)
  }

  private fun DecompositionManifestWriteRequest.toManifest(): DecompositionManifest {
    val subtasks = parseSubtasks(planningResult, parentSpecPath.toString())
    val currentId = currentSubtaskId
      ?: planningResult.intValueOrNull("current_subtask_id")
      ?: planningResult.intValueOrNull("recommended_first_subtask_id")
      ?: subtasks.first().id
    val currentSubtask = subtasks.firstOrNull { it.id == currentId }
      ?: invalidManifest(
        parentSpecPath.toString(),
        "current subtask id '$currentId' does not reference a planned subtask.",
      )
    val (issueKey, featureName) = issueAndFeature(
      resolvedParentSpecPath(repoRoot, parentSpecPath).parent.fileName.toString(),
    )
    return DecompositionManifest(
      issueKey = issueKey,
      featureName = featureName,
      parentSpecPath = repoRelativePath(repoRoot, parentSpecPath),
      executionModel = executionModel,
      baseBranch = baseBranch,
      featureBranch = featureBranch,
      stackBranches = stackBranches,
      currentSubtaskIntent = CurrentSubtaskIntent(subtaskId = currentSubtask.id, action = "start"),
      subtasks = subtasks,
    )
  }
}
