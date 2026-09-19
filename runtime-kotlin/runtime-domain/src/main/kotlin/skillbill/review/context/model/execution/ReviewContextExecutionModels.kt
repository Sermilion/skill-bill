package skillbill.review.context.model.execution
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.review.context.model.launch.CodeReviewExecutionMode.DELEGATED
import skillbill.review.context.model.launch.CodeReviewExecutionMode.INLINE

enum class ResolvedReviewExecutionMode { INLINE, DELEGATED }

fun ResolvedReviewExecutionMode.toCodeReviewExecutionMode(): CodeReviewExecutionMode = when (this) {
  ResolvedReviewExecutionMode.INLINE -> INLINE
  ResolvedReviewExecutionMode.DELEGATED -> DELEGATED
}
data class ResolvedReviewDepth(
  val resolvedMode: ResolvedReviewExecutionMode,
  val decidingRule: String,
)
