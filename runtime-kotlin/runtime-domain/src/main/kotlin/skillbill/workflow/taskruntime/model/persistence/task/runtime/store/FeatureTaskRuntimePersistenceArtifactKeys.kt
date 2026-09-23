package skillbill.workflow.taskruntime.model.persistence.task.runtime.store

internal const val FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY: String = "feature_task_runtime_phase_records"
internal const val FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY: String = "feature_task_runtime_phase_ledger"
internal const val FEATURE_TASK_RUNTIME_GOAL_PLANNING_IMPORT_ARTIFACT_KEY: String = "goal_planning_import"
internal const val FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_ARTIFACT_KEY: String = "operator_block_retry"
internal const val FEATURE_TASK_RUNTIME_REVIEW_GENERATION_ARTIFACT_KEY: String =
  "feature_task_runtime_review_generation"
const val FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_REASON_MAX_LENGTH: Int = 1000
internal const val FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_REASON_KEY: String = "reason"
internal const val FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_RETRIED_AT_KEY: String = "retried_at"
internal const val FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_PREVIOUS_BLOCKED_REASON_KEY: String = "previous_blocked_reason"
internal const val FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_REOPENED_PHASE_IDS_KEY: String = "reopened_phase_ids"

internal const val FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_FIELD_ADOPTION_ARTIFACT_KEY: String =
  "goal_continuation_field_adoption"
const val FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT: Int = 200

internal const val FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPTS_ARTIFACT_KEY: String =
  "feature_task_runtime_implementation_attempts"

internal const val FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPTS_LIMIT: Int = 64

internal const val FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_CHECKPOINT_ARTIFACT_KEY: String =
  "feature_task_runtime_finding_verification_checkpoint"

internal const val FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_BOUNDARY_SELECTION_ARTIFACT_KEY: String =
  "feature_task_runtime_finding_verification_boundary_selection"

internal const val FEATURE_TASK_RUNTIME_FINDING_VERIFICATION_DISPOSITIONS_ARTIFACT_KEY: String =
  "feature_task_runtime_finding_verification_dispositions"

internal const val FEATURE_TASK_RUNTIME_RESOLVED_BRANCH_ARTIFACT_KEY: String = "feature_task_runtime_resolved_branch"
internal const val FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY: String = "goal_continuation"
internal const val FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_OUTCOME_ARTIFACT_KEY: String = "goal_continuation_outcome"

internal const val FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY: String =
  "feature_task_runtime_delivered_projections"

const val FEATURE_TASK_RUNTIME_PHASE_STATUS_BLOCKED: String = "blocked"

const val FEATURE_TASK_RUNTIME_PHASE_STATUS_PENDING: String = "pending"

const val FEATURE_TASK_RUNTIME_PHASE_STATUS_PAUSED: String = "paused"

internal const val FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY: String = "feature_task_runtime_phase_briefings"
