package skillbill.ports.idestatus

class IdeStatusWireMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): IdeStatusWireMap = IdeStatusWireMap(map.toMap())
  }
}
