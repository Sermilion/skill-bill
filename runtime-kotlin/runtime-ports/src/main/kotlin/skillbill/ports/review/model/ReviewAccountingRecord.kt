package skillbill.ports.review.model

import skillbill.review.context.model.accounting.ReviewAccountingSummary

data class ReviewAccountingRecord(
  val reviewId: String,
  val packetDigest: String,
  val summary: ReviewAccountingSummary,
) {
  init {
    require(reviewId.isNotBlank() && packetDigest.isNotBlank())
  }
}
