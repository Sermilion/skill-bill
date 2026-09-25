package skillbill.error.core

class InvalidFeatureSpecPreparationRequestError(
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(
    "Feature-spec preparation request is invalid at '${fieldPath.ifBlank { "<root>" }}': $reason",
    cause,
  )
