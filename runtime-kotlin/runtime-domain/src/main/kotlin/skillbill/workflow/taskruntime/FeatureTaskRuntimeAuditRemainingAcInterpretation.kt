package skillbill.workflow.taskruntime

object FeatureTaskRuntimeAuditRemainingAcInterpretation {
  sealed interface Result {
    data object EmptyRemainingList : Result
    data class RemainingCriteriaText(val text: String) : Result
    data object WhitespaceOnlyFinalResponse : Result
    data object MissingFinalResponse : Result
  }

  private val MARKDOWN_FENCE: Regex =
    Regex("""^```[ \t]*[A-Za-z0-9_-]*\r?\n([\s\S]*?)```$""")

  fun interpret(finalResponse: String?): Result {
    if (finalResponse == null) return Result.MissingFinalResponse
    if (finalResponse.isBlank()) return Result.WhitespaceOnlyFinalResponse
    return if (isExplicitEmptyList(finalResponse)) {
      Result.EmptyRemainingList
    } else {
      Result.RemainingCriteriaText(finalResponse)
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
