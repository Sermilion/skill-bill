package skillbill.goalrunner.subtaskreview

import skillbill.goalrunner.subtaskreview.model.StructuredGoalReviewFinding
import skillbill.goalrunner.subtaskreview.model.UnaddressedFindingLedgerScope

internal class RejectedVerificationTruncationCollector {
  private val records = mutableListOf<String>()

  fun add(record: String) {
    records.add(record)
  }

  fun finish(): List<String> = records.toList()
}

internal data class RejectedVerificationFindingInput(
  val entry: Any?,
  val index: Int,
  val reviewRunId: String?,
  val reviewFindings: List<StructuredGoalReviewFinding>,
  val reviewById: Map<String, StructuredGoalReviewFinding>,
  val scope: UnaddressedFindingLedgerScope,
  val truncationCollector: RejectedVerificationTruncationCollector?,
)
