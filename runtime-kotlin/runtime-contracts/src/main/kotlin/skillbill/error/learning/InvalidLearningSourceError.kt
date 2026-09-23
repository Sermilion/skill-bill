package skillbill.error.learning

import skillbill.error.core.SkillBillRuntimeException

enum class InvalidLearningSourceReason {
  UNKNOWN_FINDING,
  NOT_REJECTED,
}

class InvalidLearningSourceError(
  val reason: InvalidLearningSourceReason,
  val reviewRunId: String,
  val findingId: String,
) : SkillBillRuntimeException(invalidLearningSourceMessage(reason, reviewRunId, findingId))

private fun invalidLearningSourceMessage(
  reason: InvalidLearningSourceReason,
  reviewRunId: String,
  findingId: String,
): String =
  when (reason) {
    InvalidLearningSourceReason.UNKNOWN_FINDING ->
      "Unknown learning source '$reviewRunId:$findingId'. Import the review and finding first."
    InvalidLearningSourceReason.NOT_REJECTED ->
      "Finding '$findingId' in run '$reviewRunId' has no rejected outcome. " +
        "Learnings can only be created from findings the user rejected " +
        "(fix_rejected or false_positive)."
  }
