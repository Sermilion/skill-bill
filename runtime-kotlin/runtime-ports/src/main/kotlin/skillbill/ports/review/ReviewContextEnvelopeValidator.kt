package skillbill.ports.review

import skillbill.review.context.ReviewContextWireMap

interface ReviewContextEnvelopeValidator {
  fun validate(
    envelope: ReviewContextWireMap,
    sourceLabel: String,
  )

  fun validateSpecIntentProjection(
    envelope: ReviewContextWireMap,
    sourceLabel: String,
  )
}
