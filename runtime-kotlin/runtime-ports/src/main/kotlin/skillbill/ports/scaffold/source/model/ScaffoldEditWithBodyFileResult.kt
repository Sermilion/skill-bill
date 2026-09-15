package skillbill.ports.scaffold.source.model

import skillbill.ports.scaffold.model.ScaffoldSkillStatus

data class ScaffoldEditWithBodyFileResult(
  val usedEditor: Boolean,
  val guidedSections: List<String>,
  val updatedSection: String?,
  val validatorRan: Boolean,
  val status: ScaffoldSkillStatus,
  val wrapperRegenerated: Boolean,
) {
  val skillName: String get() = status.skillName
}
