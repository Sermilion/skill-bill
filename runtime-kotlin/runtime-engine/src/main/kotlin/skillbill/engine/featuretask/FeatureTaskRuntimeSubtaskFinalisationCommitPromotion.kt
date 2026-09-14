package skillbill.engine.featuretask

import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal fun promoteSupersededCheckpointCreate(
  gitOperations: WorkflowGitOperations,
  repoRoot: Path,
  branch: String,
  decision: FeatureTaskRuntimeSubtaskCommitDecision,
  durableCommitSha: String?,
  sequenceNumber: Int,
  stageable: List<String>,
): FeatureTaskRuntimeSubtaskCommitDecision {
  if (decision !is FeatureTaskRuntimeSubtaskCommitCreate || stageable.isNotEmpty()) return decision
  val durable = durableCommitSha?.trim()?.takeIf(String::isNotBlank) ?: return decision
  val headSha = gitOperations.headCommitSha(repoRoot)
    .takeIf { it is WorkflowGitOperationResult.Ok }
    ?.value
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?: return decision
  if (durable == headSha) return decision
  val onBranch = gitOperations.isCommitAncestor(repoRoot, durable, headSha)
  if (onBranch !is WorkflowGitOperationResult.Ok || onBranch.value != "true") return decision
  val unpushed = gitOperations.localBranchHasUnpushedCommits(repoRoot, branch)
  val isUnpushed = unpushed is WorkflowGitOperationResult.Ok &&
    unpushed.value.orEmpty().trim().equals("true", ignoreCase = true)
  return FeatureTaskRuntimeSubtaskCommitAmend(
    ownedHeadSha = headSha,
    sequenceNumber = sequenceNumber,
    recoveredFromTrailer = false,
    rewritesPublishedHistory = !isUnpushed,
  )
}
