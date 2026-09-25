package skillbill.infrastructure.sqlite.review.stats

import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.error.shellcontent.ShellContentContractException

internal fun embeddedReviewPayloads(row: Map<String, Any?>): List<ReviewHealthPayload> {
  val rawChildSteps = row.stringValue("child_steps_json")
  if (rawChildSteps.isBlank()) {
    return emptyList()
  }
  val parsed =
    try {
      JsonCodec.parseJsonArrayStrict(rawChildSteps.trim())
    } catch (_: ShellContentContractException) {
      return listOf(ReviewHealthPayload("malformed", emptyMap()))
    }
  return parsed.mapNotNull(::childStepToReviewPayload)
}

private fun childStepToReviewPayload(childStep: Any?): ReviewHealthPayload? {
  val payload = childStep as? Map<*, *> ?: return ReviewHealthPayload("malformed", emptyMap())
  val normalized = payload.toHealthStringAnyMap()
  return if (isReviewChildStep(normalized)) {
    ReviewHealthPayload("embedded", normalized)
  } else {
    null
  }
}

private fun isReviewChildStep(payload: Map<String, Any?>): Boolean {
  val skill = payload.stringHealthValue(LifecycleTelemetryPayloadKeys.SKILL)
  return skill.endsWith("code-review") || "-code-review-" in skill
}
