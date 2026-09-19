package skillbill.workflow.taskruntime.feature
import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeAuditRemainingAcResult

object FeatureTaskRuntimeAuditRemainingAcInterpretation {
  private val MARKDOWN_FENCE: Regex =
    Regex("""^```[ \t]*[A-Za-z0-9_-]*\r?\n([\s\S]*?)```$""")

  fun interpret(finalResponse: String?): FeatureTaskRuntimeAuditRemainingAcResult {
    if (finalResponse == null) return FeatureTaskRuntimeAuditRemainingAcResult.MissingFinalResponse
    if (finalResponse.isBlank()) return FeatureTaskRuntimeAuditRemainingAcResult.WhitespaceOnlyFinalResponse
    return if (isExplicitEmptyList(finalResponse)) {
      FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList
    } else {
      FeatureTaskRuntimeAuditRemainingAcResult.RemainingCriteriaText(finalResponse)
    }
  }

  fun isExplicitEmptyList(text: String): Boolean {
    val candidate = stripMarkdownFence(text.trim())
    return candidate == "[]"
  }

  private fun stripMarkdownFence(text: String): String {
    val match = MARKDOWN_FENCE.matchEntire(text) ?: return text
    return match.groupValues[1].trim()
  }
}
