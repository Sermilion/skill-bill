package skillbill.review.model

enum class ReviewLaneReviewDisposition {
  COMPLETE,
  INCOMPLETE,
  ;

  val wireValue: String get() = name.lowercase()

  companion object {
    fun fromWire(value: String): ReviewLaneReviewDisposition? = entries.firstOrNull { it.wireValue == value }
  }
}
