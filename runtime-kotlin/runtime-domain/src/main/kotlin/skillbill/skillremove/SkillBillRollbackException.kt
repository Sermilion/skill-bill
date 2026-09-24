package skillbill.skillremove

import skillbill.error.core.SkillBillRuntimeException

class SkillBillRollbackException(
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause)
