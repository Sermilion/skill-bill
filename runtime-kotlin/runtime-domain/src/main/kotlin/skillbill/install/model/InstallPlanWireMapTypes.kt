package skillbill.install.model

class InstallPlanWireMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): InstallPlanWireMap = InstallPlanWireMap(LinkedHashMap(map))
  }
}
