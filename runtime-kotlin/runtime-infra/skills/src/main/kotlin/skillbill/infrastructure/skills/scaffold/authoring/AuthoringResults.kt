package skillbill.infrastructure.skills.scaffold.authoring

import skillbill.ports.scaffold.model.ScaffoldSkillStatus

internal data class AuthoringListResult(
  val repoRoot: String,
  val skillCount: Int,
  val skills: List<ScaffoldSkillStatus>,
)

internal data class AuthoringMutationResult(
  val status: ScaffoldSkillStatus,
  val wrapperRegenerated: Boolean,
)

internal data class AuthoringFillResult(
  val mutation: AuthoringMutationResult,
  val updatedSection: String?,
  val validatorRan: Boolean,
)

internal data class AuthoringSaveExactContentResult(
  val mutation: AuthoringMutationResult,
  val validatorRan: Boolean,
)

internal data class AuthoringEditWithBodyFileResult(
  val usedEditor: Boolean,
  val guidedSections: List<String>,
  val updatedSection: String?,
  val validatorRan: Boolean,
  val mutation: AuthoringMutationResult,
)

internal data class AuthoringExplain(
  val explanation: String,
  val editableSurface: List<String>,
  val generatedSurface: List<String>,
  val governedSidecars: List<String>,
  val normalWorkflow: List<String>,
  val notes: List<String>,
  val skill: AuthoringExplainSkill? = null,
)

internal data class AuthoringExplainSkill(
  val skillName: String,
  val contentFile: String,
  val renderCommand: String,
  val recommendedCommands: List<String>,
)

internal data class AuthoringValidateResult(
  val repoRoot: String,
  val mode: String,
  val skillNames: List<String>?,
  val status: String,
  val issues: List<String>,
  val suggestedCommands: List<String>?,
)

internal data class AuthoringUpgradeResult(
  val repoRoot: String,
  val regeneratedCount: Int,
  val regeneratedFiles: List<String>,
  val contentMdTouched: Boolean,
  val shellCeremonyTouched: Boolean,
  val validatorRan: Boolean,
)
