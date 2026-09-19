package skillbill.infrastructure.sqlite.review.stats.health
import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.error.shellcontent.ShellContentContractException
import skillbill.infrastructure.sqlite.review.accounting.List
import skillbill.infrastructure.sqlite.review.accounting.Map
import skillbill.infrastructure.sqlite.review.core.Map
import skillbill.infrastructure.sqlite.review.review.review
import skillbill.infrastructure.sqlite.review.stage.Map
import skillbill.infrastructure.sqlite.review.stage.and.review
import skillbill.infrastructure.sqlite.review.stage.finished.review
import skillbill.infrastructure.sqlite.review.stage.finished.stats
import skillbill.infrastructure.sqlite.review.stage.review
import skillbill.infrastructure.sqlite.review.stage.runtime.review
import skillbill.infrastructure.sqlite.review.stats.Map
import skillbill.infrastructure.sqlite.review.stats.health
import skillbill.infrastructure.sqlite.review.stats.recorded.review
import skillbill.infrastructure.sqlite.review.stats.recorded.stats
import skillbill.infrastructure.sqlite.review.stats.review
import skillbill.infrastructure.sqlite.review.stats.stats
import skillbill.infrastructure.sqlite.review.stats.stringValue
import skillbill.infrastructure.sqlite.review.stats.task.stats

internal fun embeddedReviewPayloads(row: Map<String, Any?>): List<ReviewHealthPayload> {
  val rawChildSteps = row.stringValue("child_steps_json")
  if (rawChildSteps.isBlank()) {
    return emptyList()
  }
  val parsed = try {
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
