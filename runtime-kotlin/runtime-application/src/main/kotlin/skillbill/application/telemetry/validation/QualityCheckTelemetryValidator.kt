package skillbill.application.telemetry.validation
import skillbill.application.telemetry.model.QualityCheckFinishedRequest
import skillbill.application.telemetry.model.QualityCheckStartedRequest

val qualityCheckScopeTypes = listOf("files", "working_tree", "branch_diff", "repo")
val qualityCheckResults = listOf("pass", "fail", "skipped", "unsupported_stack")

internal fun validateQualityCheckStarted(request: QualityCheckStartedRequest): String? =
  validateEnum(request.scopeType, qualityCheckScopeTypes, "scope_type")

internal fun validateQualityCheckFinished(request: QualityCheckFinishedRequest): String? =
  validateEnum(request.result, qualityCheckResults, "result")
