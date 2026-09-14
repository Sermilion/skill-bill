package skillbill.review.context

fun interface ReviewContextEnvelopeValidator {
  fun validate(envelope: ReviewContextWireMap, sourceLabel: String)

  fun validateSpecIntentProjection(envelope: ReviewContextWireMap, sourceLabel: String) = Unit
}
