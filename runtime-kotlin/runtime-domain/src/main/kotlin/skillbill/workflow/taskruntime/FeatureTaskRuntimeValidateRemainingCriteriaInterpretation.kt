package skillbill.workflow.taskruntime

import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeAuditRemainingAcResult
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidateRemainingCriteriaResult

object FeatureTaskRuntimeValidateRemainingCriteriaInterpretation {
  fun interpret(finalResponse: String?): FeatureTaskRuntimeValidateRemainingCriteriaResult =
    when (val audit = FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(finalResponse)) {
      FeatureTaskRuntimeAuditRemainingAcResult.MissingFinalResponse,
      FeatureTaskRuntimeAuditRemainingAcResult.WhitespaceOnlyFinalResponse,
      -> FeatureTaskRuntimeValidateRemainingCriteriaResult.MissingFinalResponse
      FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList ->
        FeatureTaskRuntimeValidateRemainingCriteriaResult.EmptyRemainingList
      is FeatureTaskRuntimeAuditRemainingAcResult.RemainingCriteriaText ->
        parseUnfixedCriteria(audit.text)
    }

  fun normalizedFingerprint(items: List<String>): Set<String> =
    items.map(::normalizeCriterion).filter(String::isNotBlank).toSet()

  private fun parseUnfixedCriteria(text: String): FeatureTaskRuntimeValidateRemainingCriteriaResult {
    val stripped = stripMarkdownFence(text.trim())
    if (stripped == "[]") return FeatureTaskRuntimeValidateRemainingCriteriaResult.EmptyRemainingList
    if (stripped.startsWith("[")) {
      val jsonItems = JsonCodec.parseArrayOrEmpty(stripped)
        .mapNotNull { element ->
          (element as? String)?.trim()?.takeIf(String::isNotBlank)
        }
      if (jsonItems.isEmpty()) {
        return FeatureTaskRuntimeValidateRemainingCriteriaResult.EmptyRemainingList
      }
      return FeatureTaskRuntimeValidateRemainingCriteriaResult.UnfixedCriteria(jsonItems)
    }
    val lines = stripped
      .lineSequence()
      .map { line ->
        line.trim()
          .removePrefix("-")
          .removePrefix("*")
          .trim()
          .removePrefix("•")
          .trim()
      }
      .filter(String::isNotBlank)
      .toList()
    if (lines.isEmpty()) {
      return FeatureTaskRuntimeValidateRemainingCriteriaResult.UnfixedCriteria(listOf(stripped))
    }
    return FeatureTaskRuntimeValidateRemainingCriteriaResult.UnfixedCriteria(lines)
  }

  private fun normalizeCriterion(raw: String): String = raw.trim().lowercase()

  private fun stripMarkdownFence(text: String): String {
    val match = MARKDOWN_FENCE.matchEntire(text) ?: return text
    return match.groupValues[1].trim()
  }

  private val MARKDOWN_FENCE: Regex =
    Regex("""^```[ \t]*[A-Za-z0-9_-]*\r?\n([\s\S]*?)```$""")
}
