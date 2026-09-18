package skillbill.ports.idestatus

import skillbill.ports.idestatus.model.IdeStatusWireMap

interface IdeStatusValidator {
  fun validate(snapshot: IdeStatusWireMap, sourceLabel: String)
}
