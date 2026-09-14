package skillbill.ports.idestatus

interface IdeStatusValidator {
  fun validate(snapshot: IdeStatusWireMap, sourceLabel: String)
}
