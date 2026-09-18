package skillbill.ports.idestatus

import skillbill.ports.idestatus.model.IdeStatusWireMap

object NoopIdeStatusValidator : IdeStatusValidator {
  override fun validate(snapshot: IdeStatusWireMap, sourceLabel: String) = Unit
}
