package skillbill.ports.workflow.gitops.model

import skillbill.workflow.model.goalreview.GoalObservabilityChangedFileSummary
import skillbill.workflow.model.goalreview.GoalObservabilityDiffStat

data class WorkflowWorktreeActivityResult(
  val status: WorkflowGitOperationStatus,
  val changedFileSummary: GoalObservabilityChangedFileSummary? = null,
  val diffStat: GoalObservabilityDiffStat? = null,
  val error: String = "",
)
