package skillbill.ports.scaffold.source.model

import skillbill.ports.scaffold.model.ScaffoldSkillStatus

data class ScaffoldSaveExactContentResult(
  val status: ScaffoldSkillStatus,
  val wrapperRegenerated: Boolean,
  val validatorRan: Boolean,
) {
  val skillName: String get() = status.skillName
  val updatedSection: String? get() = null
}
