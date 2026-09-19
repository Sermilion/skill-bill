package skillbill.application.review.service
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.bundled.service
import skillbill.application.review.parallel.core.review.service
import skillbill.application.review.preparation.service
import skillbill.application.review.review.resolve
import skillbill.application.review.spec.resolve
import skillbill.application.review.verification.mode
import skillbill.review.context.ReviewExecutionModePolicy
import skillbill.review.context.model.execution.toCodeReviewExecutionMode
import skillbill.review.context.model.launch.CodeReviewExecutionMode

object RuntimeOwnedReviewMode {
  private val allowed: List<CodeReviewExecutionMode> = listOf(
    CodeReviewExecutionMode.AUTO,
    CodeReviewExecutionMode.INLINE,
  )

  fun parse(value: String): CodeReviewExecutionMode = allowed.firstOrNull { it.wireValue == value }
    ?: throw IllegalArgumentException(
      "Unknown code-review execution mode '$value'. Allowed: " +
        "${allowed.joinToString { it.wireValue }}.",
    )

  fun execute(mode: CodeReviewExecutionMode): CodeReviewExecutionMode = if (mode == CodeReviewExecutionMode.DELEGATED) {
    CodeReviewExecutionMode.INLINE
  } else {
    ReviewExecutionModePolicy.resolve(mode).toCodeReviewExecutionMode()
  }
}
