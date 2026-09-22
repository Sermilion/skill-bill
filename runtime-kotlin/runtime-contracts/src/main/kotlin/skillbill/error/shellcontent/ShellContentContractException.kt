package skillbill.error.shellcontent

import skillbill.error.core.SkillBillRuntimeException

open class ShellContentContractException(
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause)
