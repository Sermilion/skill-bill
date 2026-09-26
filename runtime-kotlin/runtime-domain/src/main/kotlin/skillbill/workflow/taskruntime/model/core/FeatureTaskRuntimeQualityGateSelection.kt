package skillbill.workflow.taskruntime.model.core

import skillbill.error.featuretask.UnknownQualityGateSelectionError

enum class FeatureTaskRuntimeQualityGateSelection(val wireValue: String) {
  BUILD("build"),
  VALIDATE("validate"),
  ;

  companion object {
    fun fromWire(value: String): FeatureTaskRuntimeQualityGateSelection =
      entries.firstOrNull { it.wireValue == value }
        ?: throw UnknownQualityGateSelectionError(value, entries.map(FeatureTaskRuntimeQualityGateSelection::wireValue))
  }
}
