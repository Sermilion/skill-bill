package skillbill.workflow.taskruntime.model.core

import skillbill.contracts.JsonCodec

class FeatureTaskRuntimeWorkflowArtifactMap private constructor(
  private val delegate: Map<String, Any?>,
  val isObject: Boolean,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(raw: Any?): FeatureTaskRuntimeWorkflowArtifactMap {
      val map = JsonCodec.anyToStringAnyMap(raw)
      return FeatureTaskRuntimeWorkflowArtifactMap(map ?: emptyMap(), map != null)
    }
  }
}
