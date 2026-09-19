package skillbill.review.context.model.execution
class GovernedReviewJsonRpcArguments private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  override fun equals(other: Any?): Boolean = other is GovernedReviewJsonRpcArguments && delegate == other.delegate

  override fun hashCode(): Int = delegate.hashCode()

  companion object {
    fun from(map: Map<String, Any?>): GovernedReviewJsonRpcArguments =
      GovernedReviewJsonRpcArguments(LinkedHashMap(map))
  }
}
