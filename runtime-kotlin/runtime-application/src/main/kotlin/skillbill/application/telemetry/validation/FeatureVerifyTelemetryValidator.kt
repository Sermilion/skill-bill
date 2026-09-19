package skillbill.application.telemetry.validation
import skillbill.application.telemetry.model.FeatureVerifyFinishedRequest

val auditResults = listOf("all_pass", "had_gaps", "skipped")
val featureVerifyCompletionStatuses =
  listOf("completed", "abandoned_at_review", "abandoned_at_audit", "error")

fun validateFeatureVerifyFinished(request: FeatureVerifyFinishedRequest): String? = listOfNotNull(
  validateEnum(request.auditResult, auditResults, "audit_result"),
  validateEnum(request.completionStatus, featureVerifyCompletionStatuses, "completion_status"),
  validateEnum(request.historyRelevance, historySignalValues, "history_relevance"),
  validateEnum(request.historyHelpfulness, historySignalValues, "history_helpfulness"),
).firstOrNull()
