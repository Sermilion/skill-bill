package skillbill.infrastructure.launcher.review

import skillbill.contracts.JsonPayloadContract

class GovernedReviewWirePayload private constructor(
  private val delegate: Map<String, Any?>,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = LinkedHashMap(delegate)

  companion object {
    fun from(map: Map<String, Any?>): GovernedReviewWirePayload = GovernedReviewWirePayload(LinkedHashMap(map))
  }
}

class GovernedReviewToolSpecList private constructor(
  private val specs: List<GovernedReviewWirePayload>,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      GovernedReviewEvidencePayloadKeys.TOOLS to specs.map(GovernedReviewWirePayload::toPayload),
    )

  fun asToolPayloads(): List<Map<String, Any?>> = specs.map(GovernedReviewWirePayload::toPayload)

  companion object {
    fun from(specMaps: List<Map<String, Any?>>): GovernedReviewToolSpecList =
      GovernedReviewToolSpecList(specMaps.map(GovernedReviewWirePayload::from))
  }
}
