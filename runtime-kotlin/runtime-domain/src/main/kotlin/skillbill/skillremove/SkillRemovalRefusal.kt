package skillbill.skillremove

import skillbill.skillremove.model.SkillRemovalRefusalReason

fun refuseSkillRemoval(
  reason: SkillRemovalRefusalReason,
  message: String,
): Nothing {
  throw SkillRemovalRefusedException(reason, message)
}
