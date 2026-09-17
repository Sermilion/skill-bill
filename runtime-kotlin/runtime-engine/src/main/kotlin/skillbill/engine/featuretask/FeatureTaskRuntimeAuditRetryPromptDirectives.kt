package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseLaunchBriefing

fun auditRetryFocusDirective(focusHint: String?): String {
  if (focusHint.isNullOrBlank()) return ""
  return """
    ## Prior audit focus hint (remaining criteria only)
    Only the unresolved acceptance criteria below are in scope for this audit retry. Do not inspect,
    re-verify, or modify criteria that were already resolved in the preceding audit session. Repair
    these unresolved criteria in this same session and emit only the criteria that remain unresolved.
    $focusHint
  """.trimIndent()
}

internal fun FeatureTaskRuntimePhaseLaunchBriefing.forAuditRetry(focusHint: String): FeatureTaskRuntimePhaseLaunchBriefing {
  require(focusHint.isNotBlank()) { "Audit retry focus hint must be non-blank." }
  return copy(
    acceptanceCriteria = focusHint.lines().filter(String::isNotBlank),
    briefingText = briefingText.replaceAuditAcceptanceCriteria(focusHint),
  )
}

private fun String.replaceAuditAcceptanceCriteria(focusHint: String): String {
  val start = indexOf("acceptance_criteria:\n")
  require(start >= 0) { "Audit retry briefing is missing its acceptance_criteria section." }
  val contentStart = start + "acceptance_criteria:\n".length
  val end = indexOf("\nmandates_and_overrides:", contentStart)
  require(end >= 0) { "Audit retry briefing acceptance_criteria section has no closing boundary." }
  val replacement = focusHint.lines()
    .filter(String::isNotBlank)
    .joinToString("\n") { "  ${it.trim()}" }
  return replaceRange(contentStart, end + 1, "$replacement\n")
}
