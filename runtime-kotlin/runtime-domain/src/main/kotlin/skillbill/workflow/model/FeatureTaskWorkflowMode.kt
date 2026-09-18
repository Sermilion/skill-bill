package skillbill.workflow.model

enum class FeatureTaskWorkflowMode(val wireValue: String) {
  PROSE("prose"),
  RUNTIME("runtime"),
  ;

  companion object {
    fun fromWireValue(value: String): FeatureTaskWorkflowMode? = entries.firstOrNull { it.wireValue == value }
  }
}
