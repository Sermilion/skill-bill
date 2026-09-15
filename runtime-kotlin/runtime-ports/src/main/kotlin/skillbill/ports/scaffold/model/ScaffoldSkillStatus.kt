package skillbill.ports.scaffold.model

data class ScaffoldSkillStatus(
  val skillName: String,
  val packageName: String,
  val platform: String,
  val family: String,
  val area: String,
  val contentFile: String,
  val renderCommand: String,
  val completionStatus: ScaffoldCompletionStatus,
  val sectionCount: Int,
  val sections: List<ScaffoldSectionStatus>,
  val recommendedCommands: List<String>,
  val reviewComposition: ScaffoldReviewComposition? = null,
  val contentPreview: String? = null,
  val content: String? = null,
  val issues: List<String>? = null,
  val category: String = "skill",
  val slug: String? = null,
  val description: String? = null,
  val supportedAgents: List<String> = emptyList(),
  val consumers: List<String> = emptyList(),
  val manifestFile: String? = null,
)

data class ScaffoldSectionStatus(
  val heading: String,
  val status: ScaffoldSectionCompletionStatus,
  val lineCount: Int,
  val preview: String,
)

enum class ScaffoldCompletionStatus(val wireValue: String) {
  DRAFT("draft"),
  COMPLETE("complete"),
  AUTHORED("authored"),
  ;

  companion object {
    fun fromWire(value: String?): ScaffoldCompletionStatus? =
      value?.trim()?.let { candidate -> entries.firstOrNull { it.wireValue == candidate } }
  }
}

enum class ScaffoldSectionCompletionStatus(val wireValue: String) {
  EMPTY("empty"),
  DRAFT("draft"),
  TODO("todo"),
  FILLED("filled"),
  COMPLETE("complete"),
  ;

  companion object {
    fun fromWire(value: String?): ScaffoldSectionCompletionStatus? =
      value?.trim()?.let { candidate -> entries.firstOrNull { it.wireValue == candidate } }
  }
}

data class ScaffoldReviewComposition(
  val source: String,
  val summary: String,
  val baselineLayers: List<ScaffoldBaselineLayer>,
)

data class ScaffoldBaselineLayer(
  val platform: String,
  val skill: String,
  val scope: String,
  val required: Boolean,
  val mode: String,
)
