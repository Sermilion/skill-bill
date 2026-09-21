package skillbill.ports.typesafe

import skillbill.ports.typesafe.model.SystemOneCredentials
import skillbill.ports.typesafe.model.SystemOneEvaluateRequest
import skillbill.ports.typesafe.model.SystemOneEvaluateResult

fun interface SystemOneEvaluationPort {
  fun evaluate(credentials: SystemOneCredentials, request: SystemOneEvaluateRequest): SystemOneEvaluateResult
}
