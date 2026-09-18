package skillbill.application.review

import skillbill.infrastructure.contracts.review.ReviewContextSchemaValidator

fun validateReviewContextPayload(payload: Map<String, Any?>, source: String) {
  ReviewContextSchemaValidator.validate(payload, source)
}
