package skillbill.engine.featuretask.model

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDiagnosticFailureClass

internal sealed class FeatureTaskRuntimeRejectedOutputWrite {
  data class Written(val identity: String) : FeatureTaskRuntimeRejectedOutputWrite()
  data class Degraded(
    val failureClass: FeatureTaskRuntimeDiagnosticFailureClass,
  ) : FeatureTaskRuntimeRejectedOutputWrite()
}
