package skillbill.application.diagnostics.model

import skillbill.error.core.SkillBillRuntimeException

class RejectedOutputDiagnosticAmbiguousSelectorError(
  val matchCount: Int,
) : SkillBillRuntimeException(
    "Rejected output diagnostic raw read requires exactly one matching diagnostic; $matchCount matched.",
  )
