package skillbill.infrastructure.sqlite.review.accounting

import skillbill.contracts.JsonCodec
import skillbill.contracts.review.ReviewAccountingPayloadKeys
import skillbill.review.context.model.accounting.ReviewAccountingSummary
import skillbill.workflow.goal.model.toReviewAccountingBoundedJson

fun ReviewAccountingSummary.toBoundedPayload(): Map<String, Any?> =
  requireNotNull(
    JsonCodec.parseObjectOrNull(toReviewAccountingBoundedJson())
      ?.let(JsonCodec::jsonElementToValue)
      ?.let(JsonCodec::anyToStringAnyMap),
  ).let(::normalizeReviewAccountingPayload)

private fun normalizeReviewAccountingPayload(payload: Map<String, Any?>): Map<String, Any?> =
  payload.mapValues { (key, value) ->
    if (key == ReviewAccountingPayloadKeys.MEASURED_BYTES && value is Number) {
      value.toLong()
    } else {
      normalizeReviewAccountingValue(value)
    }
  }

private fun normalizeReviewAccountingValue(value: Any?): Any? =
  when (value) {
    is Map<*, *> -> JsonCodec.anyToStringAnyMap(value)?.let(::normalizeReviewAccountingPayload) ?: value
    is List<*> -> value.map(::normalizeReviewAccountingValue)
    else -> value
  }
