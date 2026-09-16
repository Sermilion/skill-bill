package skillbill.review.model

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidReviewContextSchemaError
import skillbill.error.MalformedJsonTextError
import skillbill.review.context.model.ReviewLaneSegmentAccounting
import skillbill.workflow.taskruntime.model.asExactIntOrNull
import skillbill.workflow.taskruntime.model.asExactLongOrNull

object ReviewRunLaneSegmentAccountingJson {
  fun encode(segments: List<ReviewLaneSegmentAccounting>): String? {
    if (segments.isEmpty()) return null
    return JsonCodec.valueToJsonString(
      segments.map { segment ->
        linkedMapOf(
          "segment_id" to segment.segmentId,
          "measured_bytes" to segment.measuredBytes,
          "entry_count" to segment.entryCount,
          "composition_digest" to segment.compositionDigest,
        )
      },
    )
  }

  fun decode(raw: String?): List<ReviewLaneSegmentAccounting> {
    if (raw.isNullOrBlank()) return emptyList()
    val trimmed = raw.trim()
    if (trimmed == "[]") return emptyList()
    val elements = try {
      JsonCodec.parseJsonArrayStrict(trimmed)
    } catch (error: MalformedJsonTextError) {
      throw segmentAccountingSchemaError("Segment accounting JSON is malformed: ${error.message.orEmpty()}", error)
    } catch (error: Exception) {
      throw segmentAccountingSchemaError(error.message ?: "Segment accounting JSON is malformed.", error)
    }
    return elements.mapIndexed { index, element ->
      decodeSegment(element, index)
    }
  }

  private fun decodeSegment(element: Any?, index: Int): ReviewLaneSegmentAccounting {
    val map = element as? Map<*, *>
      ?: throw segmentAccountingSchemaError("Segment accounting entry [$index] must be an object.")
    val segmentId = map["segment_id"] as? String
      ?: throw segmentAccountingSchemaError("Segment accounting entry [$index] is missing segment_id.")
    val measuredBytes = map["measured_bytes"].asExactLongOrNull()
      ?: throw segmentAccountingSchemaError("Segment accounting entry [$index].measured_bytes must be an integer.")
    val entryCount = map["entry_count"].asExactIntOrNull()
      ?: throw segmentAccountingSchemaError("Segment accounting entry [$index].entry_count must be an integer.")
    val compositionDigest = map["composition_digest"] as? String
      ?: throw segmentAccountingSchemaError("Segment accounting entry [$index] is missing composition_digest.")
    return try {
      ReviewLaneSegmentAccounting(
        segmentId = segmentId,
        measuredBytes = measuredBytes,
        entryCount = entryCount,
        compositionDigest = compositionDigest,
      )
    } catch (error: IllegalArgumentException) {
      throw segmentAccountingSchemaError(
        "Segment accounting entry [$index] violates its value constraints.",
        error,
      )
    }
  }

  private fun segmentAccountingSchemaError(reason: String, cause: Throwable? = null): InvalidReviewContextSchemaError =
    InvalidReviewContextSchemaError(
      sourceLabel = "review_run_lane_segment_accounting",
      reason = reason,
      cause = cause,
    )
}

fun List<String>.toStoredSegmentIdList(): String = joinToString(",")

fun String.toStoredSegmentIdList(): List<String> = split(',').map { it.trim() }.filter { it.isNotEmpty() }
