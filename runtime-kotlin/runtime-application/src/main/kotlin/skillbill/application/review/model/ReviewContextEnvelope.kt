package skillbill.application.review.model

import skillbill.workflow.engine.model.ReviewContextWireMap

class ReviewContextEnvelope private constructor(
  private val wire: ReviewContextWireMap,
) {

  val kind: String get() = wire.kind

  fun asWireMap(): ReviewContextWireMap = wire

  companion object {
    internal fun from(fields: Map<String, Any?>): ReviewContextEnvelope =
      ReviewContextEnvelope(ReviewContextWireMap.from(fields))
  }

  override fun equals(other: Any?): Boolean = other is ReviewContextEnvelope && other.wire == wire

  override fun hashCode(): Int = wire.hashCode()

  override fun toString(): String = "ReviewContextEnvelope(kind=$kind)"
}
