package skillbill.ports.idestatus.model

import skillbill.contracts.JsonPayloadContract

data class IdeStatusProblemDetails private constructor(
  private val entries: JsonPayloadContract,
) {
  fun asWirePayload(): JsonPayloadContract = entries

  companion object {
    fun from(payload: JsonPayloadContract?): IdeStatusProblemDetails? =
      payload?.takeIf { it.toPayload().isNotEmpty() }?.let(::IdeStatusProblemDetails)
  }
}
