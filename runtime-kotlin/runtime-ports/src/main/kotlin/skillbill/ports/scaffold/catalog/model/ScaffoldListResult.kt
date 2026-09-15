package skillbill.ports.scaffold.catalog.model

import skillbill.ports.scaffold.model.ScaffoldSkillStatus

data class ScaffoldListResult(
  val repoRoot: String,
  val skillCount: Int,
  val skills: List<ScaffoldSkillStatus>,
)
