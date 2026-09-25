package skillbill.error.core

open class SkillBillRuntimeException(
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)

open class ShellContentContractException(
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause)
