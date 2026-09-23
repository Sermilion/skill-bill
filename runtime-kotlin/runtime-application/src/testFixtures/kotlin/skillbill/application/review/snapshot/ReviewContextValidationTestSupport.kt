package skillbill.application.review.snapshot

import skillbill.infrastructure.contracts.review.ReviewContextSchemaValidator

fun validateReviewContextPayload(
  payload: Map<String, Any?>,
  source: String,
) {
  ReviewContextSchemaValidator.validate(payload, source)
}
