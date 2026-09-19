package skillbill.domain.skillremove

import skillbill.domain.skillremove.model.SkillRemovalRefusalReason
import skillbill.error.core.SkillBillRuntimeException
class SkillRemovalRefusedException(
  val refusalReason: SkillRemovalRefusalReason,
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause)
