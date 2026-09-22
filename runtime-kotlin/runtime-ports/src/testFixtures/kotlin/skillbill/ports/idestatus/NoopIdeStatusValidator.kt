package skillbill.ports.idestatus

import skillbill.ports.idestatus.model.IdeStatusSnapshot

object NoopIdeStatusValidator : IdeStatusValidator {
  override fun validate(
    snapshot: IdeStatusSnapshot,
    sourceLabel: String,
  ) = Unit

  override fun toWireMap(snapshot: IdeStatusSnapshot): Map<String, Any?> = emptyMap()
}
