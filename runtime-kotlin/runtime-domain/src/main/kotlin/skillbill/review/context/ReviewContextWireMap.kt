package skillbill.review.context

class ReviewContextWireMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  val kind: String get() = delegate["kind"] as? String ?: ""

  companion object {
    fun from(map: Map<String, Any?>): ReviewContextWireMap = ReviewContextWireMap(map.toMap())
  }
}
