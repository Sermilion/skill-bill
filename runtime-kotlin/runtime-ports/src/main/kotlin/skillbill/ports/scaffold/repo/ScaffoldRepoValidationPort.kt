package skillbill.ports.scaffold.repo

import skillbill.ports.scaffold.repo.model.ScaffoldAuthoringValidationRequest
import skillbill.ports.scaffold.repo.model.ScaffoldAuthoringValidationResult

fun interface ScaffoldRepoValidationPort {
  fun validateAuthoringTarget(request: ScaffoldAuthoringValidationRequest): ScaffoldAuthoringValidationResult
}
