package skillbill.review.context.model.launch
import skillbill.review.context.model.packet.entries
import skillbill.review.context.model.packet.launch
import skillbill.review.context.model.packet.wireValue
import skillbill.review.context.model.review.launch
import skillbill.review.context.model.review.value

enum class CodeReviewExecutionMode(val wireValue: String) {
  AUTO("auto"),
  INLINE("inline"),
  DELEGATED("delegated"),
  ;

  companion object {
    val DEFAULT: CodeReviewExecutionMode = INLINE

    fun fromWire(value: String): CodeReviewExecutionMode = entries.firstOrNull { it.wireValue == value }
      ?: throw IllegalArgumentException(
        "Unknown code-review execution mode '$value'. Allowed: ${entries.joinToString { it.wireValue }}.",
      )
  }
}
