package skillbill.engine.featuretask

import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal class SupersededCheckpointPromoter(
  private val gitOperations: WorkflowGitOperations,
) {
  internal fun promote(
    repoRoot: Path,
    branch: String,
    decision: FeatureTaskRuntimeSubtaskCommitDecision,
    durableCommitSha: String?,
    sequenceNumber: Int,
    stageable: List<String>,
  ): FeatureTaskRuntimeSubtaskCommitDecision {
  val replacement = if (decision is FeatureTaskRuntimeSubtaskCommitCreate && stageable.isEmpty()) {
    durableCommitSha?.trim()?.takeIf(String::isNotBlank)?.let { durable ->
      gitOperations.headCommitSha(repoRoot)
        .takeIf { it is WorkflowGitOperationResult.Ok }
        ?.value
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.takeIf { headSha -> durable != headSha }
        ?.takeIf { headSha ->
          gitOperations.isCommitAncestor(repoRoot, durable, headSha).let { result ->
            result is WorkflowGitOperationResult.Ok && result.value == "true"
          }
        }
        ?.let { headSha ->
          val unpushed = gitOperations.localBranchHasUnpushedCommits(repoRoot, branch)
          val isUnpushed = unpushed is WorkflowGitOperationResult.Ok &&
            unpushed.value.orEmpty().trim().equals("true", ignoreCase = true)
          FeatureTaskRuntimeSubtaskCommitAmend(
            ownedHeadSha = headSha,
            sequenceNumber = sequenceNumber,
            recoveredFromTrailer = false,
            rewritesPublishedHistory = !isUnpushed,
          )
        }
    }
  } else {
    null
  }
  return replacement ?: decision
  }
}
