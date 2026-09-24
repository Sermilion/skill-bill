package skillbill.contracts.workflow.session

import skillbill.contracts.JsonPayloadContract

data class WorkflowContinueSessionSummary(
  val sessionId: String? = null,
  val acceptanceCriteriaCount: Int = 0,
  val rolloutRelevant: Boolean = false,
  val specSummary: String = "",
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf<String, Any?>().apply {
      sessionId?.let { put(WorkflowContinueSessionSummaryPayloadKeys.SESSION_ID, it) }
      put(WorkflowContinueSessionSummaryPayloadKeys.ACCEPTANCE_CRITERIA_COUNT, acceptanceCriteriaCount)
      put(WorkflowContinueSessionSummaryPayloadKeys.ROLLOUT_RELEVANT, rolloutRelevant)
      put(WorkflowContinueSessionSummaryPayloadKeys.SPEC_SUMMARY, specSummary)
    }

  companion object {
    val EMPTY: WorkflowContinueSessionSummary = WorkflowContinueSessionSummary()
  }
}
