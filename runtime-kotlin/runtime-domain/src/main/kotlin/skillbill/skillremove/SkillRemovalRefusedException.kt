package skillbill.skillremove

import skillbill.error.core.SkillBillRuntimeException
import skillbill.skillremove.model.SkillRemovalRefusalReason

class SkillRemovalRefusedException(
  val refusalReason: SkillRemovalRefusalReason,
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause)
