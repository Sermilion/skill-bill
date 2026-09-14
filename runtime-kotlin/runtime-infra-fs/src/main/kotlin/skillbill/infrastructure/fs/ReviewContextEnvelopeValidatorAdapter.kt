package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.review.ReviewContextSchemaValidator
import skillbill.review.context.ReviewContextEnvelopeValidator
import skillbill.workflow.engine.model.ReviewContextWireMap

@Inject
class ReviewContextEnvelopeValidatorAdapter : ReviewContextEnvelopeValidator {
  override fun validate(envelope: ReviewContextWireMap, sourceLabel: String) {
    ReviewContextSchemaValidator.validate(envelope, sourceLabel)
  }

  override fun validateSpecIntentProjection(envelope: ReviewContextWireMap, sourceLabel: String) {
    ReviewContextSchemaValidator.validateSpecIntentProjection(envelope, sourceLabel)
  }
}
