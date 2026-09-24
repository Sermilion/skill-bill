package skillbill.error.core

open class ShellContentContractException(
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause)
