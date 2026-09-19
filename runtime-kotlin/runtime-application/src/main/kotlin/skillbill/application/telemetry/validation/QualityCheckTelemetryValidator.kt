package skillbill.application.telemetry.validation
import skillbill.application.telemetry.lifecycle.result
import skillbill.application.telemetry.lifecycle.scopeType
import skillbill.application.telemetry.model.QualityCheckFinishedRequest
import skillbill.application.telemetry.model.QualityCheckStartedRequest
import skillbill.application.telemetry.service.result

val qualityCheckScopeTypes = listOf("files", "working_tree", "branch_diff", "repo")
val qualityCheckResults = listOf("pass", "fail", "skipped", "unsupported_stack")

fun validateQualityCheckStarted(request: QualityCheckStartedRequest): String? =
  validateEnum(request.scopeType, qualityCheckScopeTypes, "scope_type")

fun validateQualityCheckFinished(request: QualityCheckFinishedRequest): String? =
  validateEnum(request.result, qualityCheckResults, "result")
