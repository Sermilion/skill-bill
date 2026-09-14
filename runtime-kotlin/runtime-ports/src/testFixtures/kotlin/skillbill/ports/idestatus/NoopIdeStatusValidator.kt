package skillbill.ports.idestatus

object NoopIdeStatusValidator : IdeStatusValidator {
  override fun validate(snapshot: IdeStatusWireMap, sourceLabel: String) = Unit
}
