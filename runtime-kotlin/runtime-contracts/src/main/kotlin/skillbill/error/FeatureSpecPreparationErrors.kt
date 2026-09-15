package skillbill.error

class InvalidFeatureSpecPreparationRequestError(
  val fieldPath: String,
  val reason: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(
  "Feature-spec preparation request is invalid at '${fieldPath.ifBlank { "<root>" }}': $reason",
  cause,
)

class FeatureSpecPreparationModeConflictError(
  val issueKey: String,
  val requestedMode: String,
  val conflictingPath: String,
  val reason: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(
  "Feature-spec preparation mode conflict for '$issueKey' (${requestedMode.ifBlank { "unknown" }}): " +
    "$reason (path: $conflictingPath)",
  cause,
)
