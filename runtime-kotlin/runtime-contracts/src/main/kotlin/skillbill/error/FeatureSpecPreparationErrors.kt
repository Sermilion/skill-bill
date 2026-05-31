package skillbill.error

/**
 * SKILL-59 Subtask 1: typed loud-fail error for the shared
 * feature-spec preparation intake contract.
 */
class InvalidFeatureSpecPreparationRequestError(
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(
  "Feature-spec preparation request is invalid at '${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)
