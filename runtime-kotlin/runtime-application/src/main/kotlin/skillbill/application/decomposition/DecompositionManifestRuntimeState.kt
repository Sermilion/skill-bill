package skillbill.application.decomposition

import skillbill.application.decomposition.model.DecompositionManifestFileCandidate
import skillbill.application.decomposition.model.DecompositionManifestRuntimeUpdate
import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys
import skillbill.contracts.decomposition.DecompositionPlanningResult
import skillbill.error.shellcontent.InvalidDecompositionManifestSchemaError
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.decomposition.runtime.invalidManifest
import skillbill.workflow.decomposition.runtime.isActiveGoalRuntime
import java.nio.file.NoSuchFileException
import java.nio.file.Path
fun archivedDecompositionManifest(repoRoot: Path, manifestPath: Path): Boolean {
  val relative = runCatching { repoRoot.normalize().relativize(manifestPath.normalize()).toString() }
    .getOrDefault(manifestPath.toString())
    .replace('\\', '/')
  return relative.startsWith(".feature-specs/done/")
}

fun loadManifestOrNull(
  path: Path,
  validator: DecompositionManifestValidator,
  fileStore: DecompositionManifestStore,
): DecompositionManifest? = try {
  loadDecompositionManifest(path, fileStore, validator)
} catch (_: NoSuchFileException) {
  null
}

fun findMatchingDecompositionManifests(
  repoRoot: Path,
  issueKey: String,
  fileStore: DecompositionManifestStore,
  validator: DecompositionManifestValidator,
  recoverPending: Boolean = true,
): List<DecompositionManifestFileCandidate> {
  val normalizedIssueKey = issueKey.trim().uppercase()
  val issueKeyInPath = Regex("(?<![A-Za-z0-9])${Regex.escape(normalizedIssueKey)}(?![A-Za-z0-9])")
  val manifestFiles = if (recoverPending) {
    fileStore.findDecompositionManifestFiles(repoRoot)
  } else {
    fileStore.findDecompositionManifestFilesWithoutRecovery(repoRoot)
  }
  return manifestFiles
    .asSequence()
    .sortedBy { path -> path.toString() }
    .filterNot { path -> archivedDecompositionManifest(repoRoot, path) }
    .filter { path ->
      val relativePath = runCatching { repoRoot.relativize(path).toString() }
        .getOrElse { path.toString() }
      issueKeyInPath.containsMatchIn(relativePath.uppercase())
    }
    .map { path ->
      val manifest = try {
        loadDecompositionManifest(path, fileStore, validator, recoverPending)
      } catch (error: NoSuchFileException) {
        throw InvalidDecompositionManifestSchemaError(
          sourceLabel = path.toString(),
          reason = "manifest disappeared during read; the decomposition bundle is incomplete.",
          failureCode = "incomplete_bundle",
          cause = error,
        )
      }
      if (manifest.issueKey != normalizedIssueKey) {
        throw InvalidDecompositionManifestSchemaError(
          sourceLabel = path.toString(),
          reason = "manifest issue_key '${manifest.issueKey}' does not match the requested issue key " +
            "'$normalizedIssueKey'.",
          failureCode = "issue_key_mismatch",
        )
      }
      DecompositionManifestFileCandidate(path, manifest)
    }
    .filterNotNull()
    .toList()
}

fun resolveDecompositionManifest(
  repoRoot: Path,
  issueKey: String,
  fileStore: DecompositionManifestStore,
  validator: DecompositionManifestValidator,
  recoverPending: Boolean = true,
): DecompositionManifest? {
  val candidates = findMatchingDecompositionManifests(
    repoRoot = repoRoot,
    issueKey = issueKey,
    fileStore = fileStore,
    validator = validator,
    recoverPending = recoverPending,
  )
  val activeCandidates = candidates.filter { candidate -> candidate.manifest.isActiveGoalRuntime() }
  if (activeCandidates.size > 1) {
    throw InvalidDecompositionManifestSchemaError(
      sourceLabel = issueKey,
      reason = "multiple active decomposition manifests match the requested issue key: " +
        activeCandidates.joinToString { candidate -> repoRoot.relativize(candidate.path).toString() } + ".",
      failureCode = "duplicate_active",
    )
  }
  return activeCandidates.firstOrNull()?.manifest ?: candidates.firstOrNull()?.manifest
}

internal fun manifestPathFromArtifacts(
  repoRoot: Path,
  artifactsPatch: Map<String, Any?>?,
  existingArtifacts: Map<String, Any?>,
  planningResult: DecompositionPlanningResult? = null,
): Path? {
  val merged = LinkedHashMap(existingArtifacts)
  artifactsPatch?.let(merged::putAll)
  val specPath = (merged["assessment"] as? Map<*, *>)?.get(
    DecompositionPlanningPayloadKeys.SPEC_PATH,
  )?.toString()?.takeIf(String::isNotBlank)
    ?: planningResult?.takeIf { it.isDecomposeMode() }?.parentSpecPath?.takeIf(String::isNotBlank)
  planningResult?.takeIf { it.isDecomposeMode() }?.let { plan ->
    return decompositionManifestPath(
      repoRoot,
      Path.of(parentSpecPath(plan)),
      plan.subtasks.map { it.specPath },
    )
  }
  return specPath?.let { resolvedParentSpecPath(repoRoot, Path.of(it)).parent.resolve(DECOMPOSITION_MANIFEST_FILENAME) }
}

fun DecompositionManifest.assertExecutionModelCanReplace(
  existing: DecompositionManifest?,
  manifestPath: Path,
): DecompositionManifest {
  if (existing != null && executionModel != existing.executionModel && existing.subtasks.any { it.hasStarted() }) {
    invalidManifest(
      manifestPath.toString(),
      "execution_model cannot change after decomposition execution has begun; manually migrate or reset the " +
        "decomposition manifest before changing execution_model.",
    )
  }
  return this
}

fun DecompositionManifest.withPreservedRuntimeState(existing: DecompositionManifest?): DecompositionManifest {
  if (existing == null) {
    return this
  }
  val existingById = existing.subtasks.associateBy(DecompositionSubtask::id)
  return copy(
    status = existing.status,
    subtasks = subtasks.map { planned ->
      val previous = existingById[planned.id]
      if (previous == null) {
        planned
      } else {
        planned.copy(
          status = previous.status,
          branch = previous.branch,
          commitSha = previous.commitSha,
          workflowId = previous.workflowId,
          blockedReason = previous.blockedReason,
          lastResumableStep = previous.lastResumableStep,
        )
      }
    },
    currentSubtaskIntent = existing.currentSubtaskIntent,
  )
}

fun DecompositionManifest.withRuntimeUpdate(
  repoRoot: Path,
  update: DecompositionManifestRuntimeUpdate,
): DecompositionManifest {
  val subtaskId = currentSubtaskIdForUpdate(repoRoot, update) ?: return this
  val status = statusFromUpdate(update)
  val updatedSubtasks = subtasks.map { subtask ->
    if (subtask.id == subtaskId) subtask.withRuntimeFields(this, update, status) else subtask
  }
  return copy(
    subtasks = updatedSubtasks,
    currentSubtaskIntent = intentFor(subtaskId, status),
  ).withParentStatus()
}
