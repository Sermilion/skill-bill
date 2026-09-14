package skillbill.engine.featuretask

import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult

internal fun FeatureTaskRuntimeSubtaskFinalisation.promoteSupersededCheckpointCreate(
  branch: String,
  decision: FeatureTaskRuntimeSubtaskCommitDecision,
  durableCommitSha: String?,
  sequenceNumber: Int,
  stageable: List<String>,
): FeatureTaskRuntimeSubtaskCommitDecision {
  if (decision !is FeatureTaskRuntimeSubtaskCommitCreate || stageable.isNotEmpty()) return decision
  val durable = durableCommitSha?.trim()?.takeIf(String::isNotBlank) ?: return decision
  val headSha = supersededHeadSha(durable) ?: return decision
  return FeatureTaskRuntimeSubtaskCommitAmend(
    ownedHeadSha = headSha,
    sequenceNumber = sequenceNumber,
    recoveredFromTrailer = false,
    rewritesPublishedHistory = rewritesPublishedHistory(branch),
  )
}

private fun FeatureTaskRuntimeSubtaskFinalisation.supersededHeadSha(durableCommitSha: String): String? {
  val headSha = gitOperations.headCommitSha(repoRoot)
    .takeIf { it is WorkflowGitOperationResult.Ok }
    ?.value
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?: return null
  if (durableCommitSha == headSha) return null
  val onBranch = gitOperations.isCommitAncestor(repoRoot, durableCommitSha, headSha)
  if (onBranch !is WorkflowGitOperationResult.Ok || onBranch.value != "true") return null
  return headSha
}

private fun FeatureTaskRuntimeSubtaskFinalisation.rewritesPublishedHistory(branch: String): Boolean {
  val unpushed = gitOperations.localBranchHasUnpushedCommits(repoRoot, branch)
  val isUnpushed = unpushed is WorkflowGitOperationResult.Ok &&
    unpushed.value.orEmpty().trim().equals("true", ignoreCase = true)
  return !isUnpushed
}
