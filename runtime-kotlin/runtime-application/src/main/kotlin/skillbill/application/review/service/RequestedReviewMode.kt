package skillbill.application.review.service

import skillbill.review.context.ReviewExecutionModePolicy
import skillbill.review.context.model.launch.CodeReviewExecutionMode

object RequestedReviewMode {
  val defaultWireValue: String = CodeReviewExecutionMode.DEFAULT.wireValue

  fun parse(value: String): CodeReviewExecutionMode = validate(CodeReviewExecutionMode.fromWire(value))

  fun validate(mode: CodeReviewExecutionMode): CodeReviewExecutionMode = mode.also(ReviewExecutionModePolicy::resolve)
}
