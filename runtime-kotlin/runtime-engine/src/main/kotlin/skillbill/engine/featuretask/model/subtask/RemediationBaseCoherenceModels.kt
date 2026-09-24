package skillbill.engine.featuretask.model.subtask

import skillbill.workflow.model.goalreview.GoalSubtaskReviewState

sealed interface RemediationBaseCoherenceResult

data class RemediationBaseCoherent(val state: GoalSubtaskReviewState?) : RemediationBaseCoherenceResult

data class RemediationBaseBlocked(val operatorGuidance: String) : RemediationBaseCoherenceResult
