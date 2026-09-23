package skillbill.ports.idestatus

import skillbill.contracts.JsonPayloadContract
import skillbill.ports.idestatus.model.IdeStatusSnapshot

object NoopIdeStatusValidator : IdeStatusValidator {
  override fun validate(
    snapshot: IdeStatusSnapshot,
    sourceLabel: String,
  ) = Unit

  override fun toWirePayload(snapshot: IdeStatusSnapshot): JsonPayloadContract =
    object : JsonPayloadContract {
      override fun toPayload(): Map<String, Any?> = emptyMap()
    }
}
