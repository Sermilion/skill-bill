package skillbill.application.review.spec
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.verification.projection
import skillbill.application.review.service.review
import skillbill.contracts.JsonCodec
import skillbill.review.context.model.execution.SpecIntentProjection

class SpecIntentProjectionWireMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): SpecIntentProjectionWireMap = SpecIntentProjectionWireMap(map.toMap())
  }
}

internal fun specIntentProjectionUtf8Bytes(wire: SpecIntentProjectionWireMap): Int =
  JsonCodec.mapToJsonString(wire).toByteArray(Charsets.UTF_8).size

internal fun specIntentProjectionUtf8Bytes(projection: SpecIntentProjection): Int =
  specIntentProjectionUtf8Bytes(projection.toProjectionPayload())
