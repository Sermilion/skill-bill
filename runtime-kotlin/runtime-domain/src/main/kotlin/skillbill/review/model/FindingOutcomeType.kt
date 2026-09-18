package skillbill.review.model

enum class FindingOutcomeType(val wireValue: String) {
  FindingAccepted("finding_accepted"),
  FixApplied("fix_applied"),
  FindingEdited("finding_edited"),
  FixRejected("fix_rejected"),
  FalsePositive("false_positive"),
  ;

  companion object {
    fun fromWire(value: String): FindingOutcomeType? =
      entries.firstOrNull { it.wireValue == value }
  }
}
