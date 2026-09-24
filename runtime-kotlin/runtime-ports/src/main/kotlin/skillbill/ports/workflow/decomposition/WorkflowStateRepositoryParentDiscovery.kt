package skillbill.ports.workflow.decomposition

import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.ports.workflow.model.toSnapshot
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.runtime.decompositionRuntime
import skillbill.workflow.decomposition.runtime.hasDecompositionRuntimeArtifact
import skillbill.workflow.decomposition.runtime.hasDecompositionPlan
import skillbill.workflow.decomposition.runtime.isActiveGoalRuntime
import skillbill.workflow.decomposition.runtime.isGoalContinuationChildWorkflow
import skillbill.workflow.model.FeatureTaskWorkflowMode
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.workflowStatus

fun WorkflowStateRepository.findDecomposedParentOrCorruptFallback(
  issueKey: String,
  validator: DecompositionManifestValidator,
  currentProjectedManifest: DecompositionManifest?,
): WorkflowStateRecord? {
  val normalizedIssueKey = issueKey.trim()
  val validCandidates = mutableListOf<DecomposedParentCandidate>()
  val corruptCandidates = mutableListOf<WorkflowStateRecord>()
  listFeatureTaskWorkflowsForParentDiscovery()
    .filter { row ->
      val snapshot = row.toSnapshot()
      !snapshot.isGoalContinuationChildWorkflow() &&
        row.issueKey == normalizedIssueKey &&
        (snapshot.hasDecompositionPlan() || snapshot.artifacts.hasDecompositionRuntimeArtifact())
    }
    .forEach { row ->
      val manifest = row.toSnapshot().artifacts.decompositionRuntime()
      val workflowStatus = row.workflowStatus.workflowStatus()
      when {
        manifest != null &&
          manifest.issueKey == normalizedIssueKey &&
          workflowStatus !in WorkflowStatus.terminalStatuses ->
          validCandidates += DecomposedParentCandidate(row, manifest)
        manifest == null && workflowStatus !in WorkflowStatus.terminalStatuses ->
          corruptCandidates += row
      }
    }
  val nonStale = validCandidates.filterNot { it.isStaleAbandonedLineage(currentProjectedManifest) }
  val active = nonStale.filter { it.manifest.isActiveGoalRuntime() }
  if (active.size > 1) {
    error(
      "Ambiguous decomposed parent workflows for '$normalizedIssueKey': " +
        active.joinToString { it.record.workflowId } +
        ". Pass an explicit workflow or manifest selector before continuing.",
    )
  }
  val validRecord = (active.firstOrNull() ?: nonStale.firstOrNull())?.record
  if (validRecord != null) return validRecord
  if (corruptCandidates.size > 1) {
    error(
      "Ambiguous corrupt-manifest parent rows for '$normalizedIssueKey': " +
        corruptCandidates.joinToString { it.workflowId } +
        ". Operator intervention is required to resolve the duplicate parent rows.",
    )
  }
  return corruptCandidates.firstOrNull()
}

fun WorkflowStateRepository.listFeatureTaskWorkflowsForParentDiscovery(): List<WorkflowStateRecord> {
  val byId = LinkedHashMap<String, WorkflowStateRecord>()
  listFeatureTaskWorkflows(FeatureTaskWorkflowMode.RUNTIME, Int.MAX_VALUE).forEach { row ->
    byId[row.workflowId] = row
  }
  listFeatureTaskWorkflows(FeatureTaskWorkflowMode.PROSE, Int.MAX_VALUE).forEach { row ->
    byId.putIfAbsent(row.workflowId, row)
  }
  return byId.values.toList()
}

fun WorkflowStateRepository.findDecomposedParentWorkflow(
  issueKey: String,
  validator: DecompositionManifestValidator,
  currentProjectedManifest: DecompositionManifest? = null,
): WorkflowStateRecord? {
  val normalizedIssueKey = issueKey.trim()
  val candidates =
    listFeatureTaskWorkflowsForParentDiscovery().mapNotNull { row ->
      val snapshot = row.toSnapshot()
      if (snapshot.isGoalContinuationChildWorkflow()) return@mapNotNull null
      val manifest = snapshot.artifacts.decompositionRuntime() ?: return@mapNotNull null
      if (
        (snapshot.hasDecompositionPlan() || row.issueKey?.trim() == normalizedIssueKey) &&
        manifest.issueKey == normalizedIssueKey
      ) {
        DecomposedParentCandidate(row, manifest)
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
  return activeCandidates.firstOrNull()?.record ?: candidates.firstOrNull()?.record
}

private data class DecomposedParentCandidate(
  val record: WorkflowStateRecord,
  val manifest: DecompositionManifest,
)

private fun DecomposedParentCandidate.isStaleAbandonedLineage(
  currentProjectedManifest: DecompositionManifest?,
): Boolean {
  if (currentProjectedManifest == null || record.workflowStatus.workflowStatus() != WorkflowStatus.ABANDONED) {
    return false
  }
  if (manifest.subtasks.any { subtask -> subtask.hasStarted() }) return false
  return manifest.subtasks.map { it.specPath } != currentProjectedManifest.subtasks.map { it.specPath }
}

private fun DecompositionManifest.sameRuntimeIdentity(other: DecompositionManifest): Boolean =
  issueKey == other.issueKey &&
    parentSpecPath == other.parentSpecPath &&
    subtasks.map { it.specPath } == other.subtasks.map { it.specPath }

fun WorkflowStateRepository.findDecomposedParentWorkflowForRuntime(
  manifest: DecompositionManifest,
  validator: DecompositionManifestValidator,
): WorkflowStateRecord? =
  listFeatureTaskWorkflows(FeatureTaskWorkflowMode.RUNTIME, Int.MAX_VALUE).firstOrNull { row ->
    val snapshot = row.toSnapshot()
    !snapshot.isGoalContinuationChildWorkflow() &&
      (snapshot.hasDecompositionPlan() || row.issueKey?.trim() == manifest.issueKey) &&
      snapshot.artifacts.decompositionRuntime()?.sameRuntimeIdentity(manifest) == true
  }
