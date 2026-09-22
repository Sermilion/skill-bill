package skillbill.engine.featuretask.review.core

import skillbill.engine.featuretask.lifecycle.checkpoint.reviewUntrackedExclusions
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaseline
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.repositoryOwnedPaths
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeResolvedBranch
import java.nio.file.Path

private const val SCOPED_REVIEW_PATH_DELIMITER: Char = '\u0000'

object FeatureTaskRuntimeScopedReviewBaseline {
  fun untrackedExclusions(
    gitOperations: WorkflowGitOperations,
    repoRoot: Path,
    resolved: FeatureTaskRuntimeResolvedBranch,
  ): List<String> {
    val current = gitOperations.repositoryOwnedPaths(repoRoot)

    if (current !is WorkflowGitOperationResult.Ok) return resolved.baselineUntrackedPaths
    return reviewUntrackedExclusions(
      baselineUntrackedPaths = resolved.baselineUntrackedPaths,
      currentUntrackedPaths =
        current.value.orEmpty()
          .split(SCOPED_REVIEW_PATH_DELIMITER)
          .map(String::trim)
          .filter(String::isNotBlank),
      ownedPaths = resolved.workflowOwnedPaths,
    )
  }

  fun of(
    gitOperations: WorkflowGitOperations,
    repoRoot: Path,
    resolved: FeatureTaskRuntimeResolvedBranch,
    reviewBaseSha: String,
  ): GoalSubtaskReviewBaseline =
    GoalSubtaskReviewBaseline(
      reviewBaseSha,
      untrackedExclusions(gitOperations, repoRoot, resolved),
      resolved.workflowOwnedPaths,
    )
}
