package skillbill.application.review.review
import skillbill.application.review.packet.payload
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.review.source
import skillbill.application.review.preparation.payload
import skillbill.application.review.service.review
import skillbill.application.review.service.validate
import skillbill.application.review.spec.payload
import skillbill.application.review.stats.payload
import skillbill.application.review.verification.payload
import skillbill.infrastructure.contracts.review.ReviewContextSchemaValidator

fun validateReviewContextPayload(payload: Map<String, Any?>, source: String) {
  ReviewContextSchemaValidator.validate(payload, source)
}
