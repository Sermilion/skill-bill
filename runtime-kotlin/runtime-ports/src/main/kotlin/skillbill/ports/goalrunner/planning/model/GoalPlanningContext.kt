package skillbill.ports.goalrunner.planning.model

data class GoalPlanningBoundaryHeading(
  val headingId: String,
  val sourcePath: String,
  val kind: GoalPlanningBoundaryHeadingKind,
  val heading: String,
)

enum class GoalPlanningBoundaryHeadingKind(val wireValue: String) {
  HISTORY("history"),
  DECISIONS("decisions"),
  ;

  companion object {
    fun fromWire(value: String): GoalPlanningBoundaryHeadingKind? = entries.firstOrNull { it.wireValue == value }
  }
}

data class GoalPlanningContext(
  val boundaryCatalog: List<GoalPlanningBoundaryHeading>,
  val boundaryCatalogTruncated: Boolean,
  val validationGuidance: String,
) {
  companion object {
    val KIND_HISTORY: GoalPlanningBoundaryHeadingKind = GoalPlanningBoundaryHeadingKind.HISTORY
    val KIND_DECISIONS: GoalPlanningBoundaryHeadingKind = GoalPlanningBoundaryHeadingKind.DECISIONS

    const val MAX_DISCOVERY_FILE_COUNT = 32
    const val MAX_HEADINGS_PER_FILE = 64
    const val MAX_CATALOG_HEADINGS = 256
    const val MAX_HEADING_TEXT_CHARS = 200
    const val MAX_VALIDATION_GUIDANCE_BYTES = 4_096

    const val MAX_BOUNDARY_FILE_BYTES = 128 * 1_024L

    const val MAX_SELECTED_BODIES = 24
    const val MAX_BODY_BYTES = 8_192
    const val MAX_TOTAL_BODY_BYTES = 64 * 1_024

    const val MAX_REPORTED_UNRESOLVED_IDS = 32
    const val MAX_REPORTED_UNRESOLVED_ID_CHARS = 200
  }
}

data class GoalPlanningBoundaryBody(
  val headingId: String,
  val sourcePath: String,
  val heading: String,
  val body: String,
)

data class GoalPlanningResolvedBoundaryBodies(
  val bodies: List<GoalPlanningBoundaryBody> = emptyList(),
  val unresolvedHeadingIds: List<String> = emptyList(),
  val truncated: Boolean = false,
)
