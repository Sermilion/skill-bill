package skillbill.workflow.taskruntime.model.repair.task
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_REASON_MAX_LENGTH
data class FeatureTaskRuntimeOperatorBlockRetry(
  val phaseId: String,
  val reason: String,
  val retriedAt: String,
) {
  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimeOperatorBlockRetry.phaseId must be non-blank." }
    require(reason.length in 1..FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_REASON_MAX_LENGTH && reason.isNotBlank()) {
      "FeatureTaskRuntimeOperatorBlockRetry.reason must contain " +
        "1..$FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_REASON_MAX_LENGTH characters."
    }
    require(retriedAt.isNotBlank()) { "FeatureTaskRuntimeOperatorBlockRetry.retriedAt must be non-blank." }
  }
}
