package skillbill.infrastructure.sqlite.workflow.decomposition
import skillbill.error.shellcontent.LegacyProseWorkflowError
import skillbill.infrastructure.sqlite.decomposition.asStringAnyMapOrNull
import skillbill.infrastructure.sqlite.decomposition.decodeArtifacts
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.ports.workflow.model.toSnapshot
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.decodeManifest
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionManifestWireMap
import skillbill.workflow.decomposition.runtime.DECOMPOSITION_RUNTIME_ARTIFACT_KEY
import skillbill.workflow.decomposition.runtime.isActiveGoalRuntime
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.model.FeatureTaskWorkflowMode
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.workflowStatus

internal fun WorkflowStateSnapshot.decompositionRuntime(
  validator: DecompositionManifestValidator,
): DecompositionManifest? =
  decodeArtifacts(artifactsJson)[DECOMPOSITION_RUNTIME_ARTIFACT_KEY].asStringAnyMapOrNull()
    ?.let {
      validator.decodeManifest(DecompositionManifestWireMap.from(it), DECOMPOSITION_RUNTIME_ARTIFACT_KEY)
    }

internal fun WorkflowStateSnapshot.hasDecompositionPlan(): Boolean =
  decodeArtifacts(artifactsJson)["plan"].asStringAnyMapOrNull()?.get("mode") == "decompose"

internal val IMPLEMENT_TERMINAL_STATUSES: Set<WorkflowStatus> = WorkflowStatus.terminalStatuses

internal fun WorkflowStateRepository.listFeatureTaskWorkflowsForParentDiscovery(): List<WorkflowStateRecord> {
  val byId = LinkedHashMap<String, WorkflowStateRecord>()
  listFeatureTaskWorkflows(FeatureTaskWorkflowMode.RUNTIME, Int.MAX_VALUE).forEach { row ->
    byId[row.workflowId] = row
  }
  listFeatureTaskWorkflows(FeatureTaskWorkflowMode.PROSE, Int.MAX_VALUE).forEach { row ->
    byId.putIfAbsent(row.workflowId, row)
  }
  return byId.values.toList()
}

internal fun WorkflowStateRecord.requireRuntimeModeForEngineWrite() {
  if (mode != FeatureTaskWorkflowMode.RUNTIME) {
    throw LegacyProseWorkflowError(workflowId, issueKey)
  }
}

internal fun WorkflowStateRepository.findDecomposedParentWorkflow(
  issueKey: String,
  validator: DecompositionManifestValidator,
  currentProjectedManifest: DecompositionManifest? = null,
): WorkflowStateRecord? {
  val normalizedIssueKey = issueKey.trim()
  val candidates =
    listFeatureTaskWorkflowsForParentDiscovery().mapNotNull { row ->
      val snapshot = row.toSnapshot()
      if (snapshot.isGoalContinuationChildWorkflow()) return@mapNotNull null
      val manifest = snapshot.decompositionRuntime(validator) ?: return@mapNotNull null
      if (
        (snapshot.hasDecompositionPlan() || row.issueKey?.trim() == normalizedIssueKey) &&
        manifest.issueKey == normalizedIssueKey
      ) {
        DecomposedParentLookupCandidate(row, manifest)
      } else {
        null
      }
    }.filterNot { candidate -> candidate.isStaleAbandonedLineage(currentProjectedManifest) }
  val activeCandidates = candidates.filter { candidate -> candidate.manifest.isActiveGoalRuntime() }
  if (activeCandidates.size > 1) {
    error(
      "Ambiguous decomposed parent workflows for '$normalizedIssueKey': " +
        activeCandidates.joinToString { candidate -> candidate.record.workflowId } +
        ". Pass an explicit workflow or manifest selector before continuing.",
    )
  }
  return activeCandidates.firstOrNull()?.record
    ?: candidates.firstOrNull()?.record
}

private data class DecomposedParentLookupCandidate(
  internal val record: WorkflowStateRecord,
  internal val manifest: DecompositionManifest,
)

private fun DecomposedParentLookupCandidate.isStaleAbandonedLineage(
  currentProjectedManifest: DecompositionManifest?,
): Boolean {
  if (currentProjectedManifest == null || record.workflowStatus.workflowStatus() != WorkflowStatus.ABANDONED) {
    return false
  }
  if (manifest.subtasks.any { subtask -> subtask.hasStarted() }) return false
  return manifest.subtasks.map { it.specPath } != currentProjectedManifest.subtasks.map { it.specPath }
}

internal fun WorkflowStateSnapshot.isGoalContinuationChildWorkflow(): Boolean {
  val goalContinuation = decodeArtifacts(artifactsJson)["goal_continuation"].asStringAnyMapOrNull() ?: return false
  return goalContinuation["enabled"] == true ||
    goalContinuation.containsKey("issue_key") ||
    goalContinuation.containsKey("subtask_id")
}
