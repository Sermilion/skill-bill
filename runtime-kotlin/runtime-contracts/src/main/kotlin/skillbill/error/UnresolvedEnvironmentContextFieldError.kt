package skillbill.error

class UnresolvedEnvironmentContextFieldError(
  val fieldName: String,
) : SkillBillRuntimeException(
  "EnvironmentContext.$fieldName is unresolved; resolve it in the composition root before opening SQLite.",
)
