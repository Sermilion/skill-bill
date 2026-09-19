package skillbill.workflow.taskruntime.model.review
import skillbill.error.shellcontent.InvalidGoalSubtaskReviewStateSchemaError
import skillbill.review.context.ReviewExecutionModePolicy
import skillbill.review.context.model.execution.toCodeReviewExecutionMode
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.audit.reason
import skillbill.workflow.taskruntime.model.core.fieldPath
import skillbill.workflow.taskruntime.model.core.reason
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.reason
import skillbill.workflow.taskruntime.model.persistence.task.runtime.prior.passNumber
import skillbill.workflow.taskruntime.model.phase.reason
import skillbill.workflow.taskruntime.model.phase.sourceLabel
import skillbill.workflow.taskruntime.model.repair.task.reason
import skillbill.workflow.taskruntime.model.validation.reason

object FeatureTaskRuntimeReviewPassSequence {
  fun modeForPass(pinnedMode: CodeReviewExecutionMode, passNumber: Int): CodeReviewExecutionMode =
    resolveForPass(pinnedMode, passNumber).resolvedTier

  fun resolveForPass(pinnedMode: CodeReviewExecutionMode, passNumber: Int): ReviewPassResolution {
    if (passNumber < 1) {
      throw InvalidGoalSubtaskReviewStateSchemaError(
        sourceLabel = GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY,
        fieldPath = "review_pass_number",
        reason = "must be a positive integer.",
      )
    }
    if (passNumber > ReviewExecutionModePolicy.FIRST_REVIEW_PASS) {
      throw InvalidGoalSubtaskReviewStateSchemaError(
        sourceLabel = GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY,
        fieldPath = "review_pass_number",
        reason = "review runs exactly once; pass $passNumber is not allowed.",
      )
    }
    val resolved = ReviewExecutionModePolicy.resolveWithRule(pinnedMode, passNumber)
    return ReviewPassResolution(
      resolvedTier = resolved.resolvedMode.toCodeReviewExecutionMode(),
      decidingRule = resolved.decidingRule,
    )
  }
}

data class ReviewPassResolution(
  val resolvedTier: CodeReviewExecutionMode,
  val decidingRule: String,
)
