package skillbill.review.model
import skillbill.review.context.model.packet.ReviewLaneReviewDisposition
data class ReviewLaneAggregationInput(
  val lane: String,
  val commitSequenceDigest: String,
  val disposition: ReviewLaneReviewDisposition,
  val unreviewedUnits: List<String> = emptyList(),
)

data class ReviewCoverageReport(
  val cleanLanes: List<String>,
  val incompleteLanes: List<ReviewLaneAggregationInput>,
  val integrationCompleted: Boolean,

  val integrationNotApplicableReason: String? = null,
) {
  val isCleanCoverage: Boolean get() = incompleteLanes.isEmpty()

  fun render(): String = buildString {
    integrationNotApplicableReason?.let { reason ->
      appendLine("Commit-focused sequencing: not applicable — $reason. No integration pass was run.")
    }
    if (isCleanCoverage) {
      appendLine("Coverage: clean — every selected lane reviewed its full assigned bundle.")
      return@buildString
    }
    appendLine(
      "Coverage: NOT clean — ${incompleteLanes.size} lane(s) ended with incomplete coverage.",
    )
    incompleteLanes.sortedBy { it.lane }.forEach { lane ->
      appendLine("- ${lane.lane} left unreviewed: ${lane.unreviewedUnits.sorted().joinToString(", ")}")
    }
    if (integrationCompleted) {
      appendLine(
        "The integration pass completed over cross-commit behavior only. It did not review the " +
          "units named above and does not close this coverage gap.",
      )
    }
  }
}
