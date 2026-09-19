package skillbill.error.shellcontent

import skillbill.error.core.SkillBillRuntimeException
import skillbill.error.core.error
import skillbill.error.core.message
open class ShellContentContractException(
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause)
