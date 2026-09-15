package skillbill.ports.scaffold.catalog.model

data class ScaffoldExplainResult(
  val explanation: String,
  val editableSurface: List<String>,
  val generatedSurface: List<String>,
  val governedSidecars: List<String>,
  val normalWorkflow: List<String>,
  val notes: List<String>,
  val skill: ScaffoldExplainSkill? = null,
)

data class ScaffoldExplainSkill(
  val skillName: String,
  val contentFile: String,
  val renderCommand: String,
  val recommendedCommands: List<String>,
)
