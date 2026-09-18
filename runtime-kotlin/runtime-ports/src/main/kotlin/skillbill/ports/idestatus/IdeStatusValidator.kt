package skillbill.ports.idestatus

import skillbill.ports.idestatus.model.IdeStatusSnapshot

interface IdeStatusValidator {
  fun validate(snapshot: IdeStatusSnapshot, sourceLabel: String)

  fun toWireMap(snapshot: IdeStatusSnapshot): Map<String, Any?>
}
