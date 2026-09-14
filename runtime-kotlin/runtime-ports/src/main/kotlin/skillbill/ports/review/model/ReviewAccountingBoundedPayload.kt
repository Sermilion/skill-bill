package skillbill.ports.review.model

class ReviewAccountingBoundedPayload private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  fun asMap(): Map<String, Any?> = delegate
  companion object {
    fun from(map: Map<String, Any?>): ReviewAccountingBoundedPayload {
      requireBoundedAccountingPayload(map)
      return ReviewAccountingBoundedPayload(map.toMap())
    }
  }
}
