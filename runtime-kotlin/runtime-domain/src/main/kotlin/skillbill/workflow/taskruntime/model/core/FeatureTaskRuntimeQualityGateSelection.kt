package skillbill.workflow.taskruntime.model.core
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.handoff.task.value
import skillbill.workflow.taskruntime.model.handoff.task.wireValue
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.validation.wireValue

enum class FeatureTaskRuntimeQualityGateSelection(val wireValue: String) {
  BUILD("build"),
  VALIDATE("validate"),
  ;

  companion object {
    fun fromWire(value: String?): FeatureTaskRuntimeQualityGateSelection = when (value) {
      BUILD.wireValue -> BUILD
      null, VALIDATE.wireValue -> VALIDATE
      else -> VALIDATE
    }
  }
}

fun FeatureTaskRuntimeQualityGateSelection?.orLegacyValidate(): FeatureTaskRuntimeQualityGateSelection =
  this ?: FeatureTaskRuntimeQualityGateSelection.VALIDATE
