package skillbill.application.review.service
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.bundled.service
import skillbill.application.review.parallel.core.review.service
import skillbill.application.review.preparation.service
import skillbill.application.review.review.resolve
import skillbill.application.review.spec.resolve
import skillbill.application.review.verification.mode
import skillbill.review.context.ReviewExecutionModePolicy
import skillbill.review.context.model.launch.CodeReviewExecutionMode

object RequestedReviewMode {
  val defaultWireValue: String = CodeReviewExecutionMode.DEFAULT.wireValue

  fun parse(value: String): CodeReviewExecutionMode = validate(CodeReviewExecutionMode.fromWire(value))

  fun validate(mode: CodeReviewExecutionMode): CodeReviewExecutionMode = mode.also(ReviewExecutionModePolicy::resolve)
}
