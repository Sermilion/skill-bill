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

  override fun equals(other: Any?): Boolean = other is ReviewAccountingBoundedPayload && delegate == other.delegate

  override fun hashCode(): Int = delegate.hashCode()

  override fun toString(): String = delegate.toString()
}
