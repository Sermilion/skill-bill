package skillbill.ports.scaffold.catalog.model

import skillbill.ports.scaffold.model.ScaffoldSkillStatus

data class ScaffoldShowResult(
  val status: ScaffoldSkillStatus,
) {
  val skillName: String get() = status.skillName
}
