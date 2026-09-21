package skillbill.engine.goalrunner.experiment

import skillbill.engine.goalrunner.execution.support.toPullRequestRequest
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryRequest
import skillbill.ports.experiment.publication.ExperimentPublicationResult
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.model.GoalPullRequestResult
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult

class ExistingGoalRunnerParentDelivery(
  private val manifestStore: GoalRunnerManifestStore,
  private val pullRequestPort: GoalPullRequestPort,
  private val gitOperations: WorkflowGitOperations,
) : ExperimentParentDeliveryPort {
  override fun reconcile(
    pairId: String,
    controlWorkflowId: String,
    controlCommitSha: String?,
    controlCompleted: Boolean,
    request: ExperimentParentDeliveryRequest,
  ): ExperimentPublicationResult {
    if (!controlCompleted || controlCommitSha.isNullOrBlank()) {
      return ExperimentPublicationResult(
        published = false,
        reason = "control arm is not ready for parent delivery",
      )
    }
    val head = (gitOperations.headCommitSha(request.controlRepoRoot) as? WorkflowGitOperationResult.Ok)
      ?.value?.trim()
    if (head != controlCommitSha) {
      return ExperimentPublicationResult(
        published = false,
        reason = "control worktree changed after the reviewed commit was captured",
      )
    }
    val branch = (gitOperations.currentBranch(request.controlRepoRoot) as? WorkflowGitOperationResult.Ok)
      ?.value?.trim().orEmpty()
    if (branch.isBlank()) {
      return ExperimentPublicationResult(
        published = false,
        reason = "control worktree has no publishable branch",
      )
    }
    val state = manifestStore.loadByIssueKey(request.issueKey, request.controlRepoRoot)
      ?: return ExperimentPublicationResult(
        published = false,
        reason = "the control goal manifest is unavailable for parent delivery",
      )
    return when (
      val result = pullRequestPort.open(
        state.manifest.toPullRequestRequest(request.controlRepoRoot).copy(headBranch = branch),
      )
    ) {
      is GoalPullRequestResult.Opened -> ExperimentPublicationResult(published = true)
      is GoalPullRequestResult.Existing -> ExperimentPublicationResult(
        published = true,
        alreadyPublished = true,
      )
      is GoalPullRequestResult.Failed -> ExperimentPublicationResult(
        published = false,
        reason = result.reason,
      )
    }
  }
}
