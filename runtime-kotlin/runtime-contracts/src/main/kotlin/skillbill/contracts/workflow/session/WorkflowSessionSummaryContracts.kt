package skillbill.contracts.workflow.session
import skillbill.contracts.JsonPayloadContract

object WorkflowSessionSummaryPayloadKeys {
  const val SESSION_ID: String = "session_id"
  const val ISSUE_KEY_PROVIDED: String = "issue_key_provided"
  const val ISSUE_KEY_TYPE: String = "issue_key_type"
  const val SPEC_INPUT_TYPES: String = "spec_input_types"
  const val SPEC_WORD_COUNT: String = "spec_word_count"
  const val FEATURE_SIZE: String = "feature_size"
  const val FEATURE_NAME: String = "feature_name"
  const val ROLLOUT_NEEDED: String = "rollout_needed"
  const val ACCEPTANCE_CRITERIA_COUNT: String = "acceptance_criteria_count"
  const val OPEN_QUESTIONS_COUNT: String = "open_questions_count"
  const val SPEC_SUMMARY: String = "spec_summary"
  const val ROLLOUT_RELEVANT: String = "rollout_relevant"
}

data class FeatureImplementSessionSummaryContract(
  val sessionId: String,
  val issueKeyProvided: Boolean,
  val issueKeyType: String,
  val specInputTypes: List<String>,
  val specWordCount: Int,
  val featureSize: String,
  val featureName: String,
  val rolloutNeeded: Boolean,
  val acceptanceCriteriaCount: Int,
  val openQuestionsCount: Int,
  val specSummary: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = linkedMapOf(
    WorkflowSessionSummaryPayloadKeys.SESSION_ID to sessionId,
    WorkflowSessionSummaryPayloadKeys.ISSUE_KEY_PROVIDED to issueKeyProvided,
    WorkflowSessionSummaryPayloadKeys.ISSUE_KEY_TYPE to issueKeyType,
    WorkflowSessionSummaryPayloadKeys.SPEC_INPUT_TYPES to specInputTypes,
    WorkflowSessionSummaryPayloadKeys.SPEC_WORD_COUNT to specWordCount,
    WorkflowSessionSummaryPayloadKeys.FEATURE_SIZE to featureSize,
    WorkflowSessionSummaryPayloadKeys.FEATURE_NAME to featureName,
    WorkflowSessionSummaryPayloadKeys.ROLLOUT_NEEDED to rolloutNeeded,
    WorkflowSessionSummaryPayloadKeys.ACCEPTANCE_CRITERIA_COUNT to acceptanceCriteriaCount,
    WorkflowSessionSummaryPayloadKeys.OPEN_QUESTIONS_COUNT to openQuestionsCount,
    WorkflowSessionSummaryPayloadKeys.SPEC_SUMMARY to specSummary,
  )
}

data class FeatureVerifySessionSummaryContract(
  val sessionId: String,
  val acceptanceCriteriaCount: Int,
  val rolloutRelevant: Boolean,
  val specSummary: String,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = linkedMapOf(
    WorkflowSessionSummaryPayloadKeys.SESSION_ID to sessionId,
    WorkflowSessionSummaryPayloadKeys.ACCEPTANCE_CRITERIA_COUNT to acceptanceCriteriaCount,
    WorkflowSessionSummaryPayloadKeys.ROLLOUT_RELEVANT to rolloutRelevant,
    WorkflowSessionSummaryPayloadKeys.SPEC_SUMMARY to specSummary,
  )
}
