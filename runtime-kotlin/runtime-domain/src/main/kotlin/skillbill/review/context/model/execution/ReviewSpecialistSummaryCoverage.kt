package skillbill.review.context.model.execution

data class ReviewSpecialistSummaryCoverage(
  val assignedPaths: List<String>,
  val commitShas: List<String>,
  val findingCount: Int,
  val summary: String = "",
)
