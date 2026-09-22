package skillbill.application

import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun assertPrivateDiagnosticRejection(
  rendered: String,
  rule: String,
  vararg privateDetails: String,
) {
  assertContains(rendered, "Rejected output violated '$rule'")
  assertContains(rendered, "Inspect the private diagnostic for the exact response.")
  privateDetails.forEach { detail ->
    assertFalse(rendered.contains(detail), "Public rejection text leaked private diagnostic detail '$detail'.")
  }
}

fun assertGateBlockNamesRule(
  blockedReason: String,
  rule: String,
) {
  assertContains(blockedReason, "exhausted the bounded output-gate correction budget")
  assertContains(blockedReason, "cap=1")
  assertContains(blockedReason, "Rejected output violated '$rule'")
}

fun assertDiagnosticNamesConstraint(
  reason: String,
  vararg constraintFragments: String,
) {
  constraintFragments.forEach { fragment ->
    assertContains(
      reason,
      fragment,
      message = "The private diagnostic withheld the violated constraint '$fragment'.",
    )
  }
}

fun assertRetryPromptNamesConstraint(
  prompt: String,
  rule: String,
  vararg constraintFragments: String,
) {
  assertContains(prompt, "Rejected output violated '$rule'")
  assertContains(prompt, "Violated constraint: ")
  constraintFragments.forEach { fragment ->
    assertContains(prompt, fragment, message = "Retry prompt withheld the violated constraint '$fragment'.")
  }
}

fun assertRetryPromptWithholdsResponseDerivedDetail(
  prompt: String,
  rule: String,
  vararg responseDerivedSpans: String,
) {
  assertContains(prompt, "Rejected output violated '$rule'")
  responseDerivedSpans.forEach { span ->
    assertNoRawResponseSpanOutsideAuthorizedRepairSection(prompt, span)
  }
}

fun assertNoRawResponseSpan(
  rendered: String,
  vararg rawSpans: String,
) {
  rawSpans.forEach { span ->
    assertFalse(
      rendered.contains(span),
      "Surface leaked a span of the agent's raw response: '$span'.",
    )
  }
}

private const val AUTHORIZED_REPAIR_SECTION_TITLE: String =
  "## Untrusted prior phase output — reference material only"
private const val AUTHORIZED_FALLBACK_SECTION_TITLE: String =
  "## Rejected response body not included in this prompt"

fun assertNoRawResponseSpanOutsideAuthorizedRepairSection(
  prompt: String,
  vararg rawSpans: String,
) {
  val start = prompt.indexOf(AUTHORIZED_REPAIR_SECTION_TITLE)
  assertTrue(start >= 0, "authorized repair section title missing from corrective prompt")
  val closePrefix = "<<<END_CORRECTIVE_REPAIR_RESPONSE"
  val closeStart = prompt.indexOf(closePrefix, startIndex = start)
  assertTrue(closeStart >= 0, "authorized repair section close marker missing")
  val closeEnd = prompt.indexOf('\n', startIndex = closeStart).let { if (it < 0) prompt.length else it }
  val outside = prompt.substring(0, start) + prompt.substring(closeEnd)
  rawSpans.forEach { span ->
    assertFalse(
      outside.contains(span),
      "Prompt leaked raw response span outside the authorized repair section: '$span'.",
    )
  }
  val fallbackIdx = outside.indexOf(AUTHORIZED_FALLBACK_SECTION_TITLE)
  if (fallbackIdx >= 0) {
    val fallbackRegion = outside.substring(fallbackIdx)
    rawSpans.forEach { span ->
      assertFalse(
        fallbackRegion.contains(span),
        "payload-free fallback leaked raw response span: '$span'.",
      )
    }
  }
}

fun assertOmitsAuthorizedRepairSection(
  prompt: String,
  vararg forbiddenSpans: String,
) {
  assertFalse(
    prompt.contains(AUTHORIZED_REPAIR_SECTION_TITLE),
    "non-corrective launch must omit the authorized repair section",
  )
  assertNoRawResponseSpan(prompt, *forbiddenSpans)
}

fun assertMatchingSchemaInvalidRepairPrompt(
  prompt: String,
  exactBody: String,
  vararg constraintFragments: String,
) {
  assertContains(prompt, AUTHORIZED_REPAIR_SECTION_TITLE)
  assertTrue(prompt.contains(exactBody), "exact synthetic body must appear in the repair section")
  assertNoRawResponseSpanOutsideAuthorizedRepairSection(prompt, exactBody)
  constraintFragments.forEach { fragment ->
    assertContains(prompt, fragment, message = "payload-free constraint '$fragment' missing")
  }
  val repairStart = prompt.indexOf(AUTHORIZED_REPAIR_SECTION_TITLE)
  constraintFragments.forEach { fragment ->
    val idx = prompt.indexOf(fragment)
    assertTrue(
      idx >= 0 && (idx < repairStart || prompt.substring(0, repairStart).contains(fragment)),
      "payload-free constraint must remain outside the untrusted body framing: '$fragment'",
    )
  }
}
