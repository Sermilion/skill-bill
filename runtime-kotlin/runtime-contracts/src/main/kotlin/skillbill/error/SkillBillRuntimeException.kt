package skillbill.error

open class SkillBillRuntimeException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
