package skillbill.ports.scaffold.source.model

import skillbill.ports.scaffold.model.ScaffoldSkillStatus

data class ScaffoldFillResult(
  val status: ScaffoldSkillStatus,
  val wrapperRegenerated: Boolean,
  val updatedSection: String?,
  val validatorRan: Boolean,
) {
  val skillName: String get() = status.skillName
}
