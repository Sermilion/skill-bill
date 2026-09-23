package skillbill.ports.idestatus

import skillbill.contracts.JsonPayloadContract
import skillbill.ports.idestatus.model.IdeStatusSnapshot

interface IdeStatusValidator {
  fun validate(
    snapshot: IdeStatusSnapshot,
    sourceLabel: String,
  )

  fun toWirePayload(snapshot: IdeStatusSnapshot): JsonPayloadContract
}
