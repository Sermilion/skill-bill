package skillbill.engine.featuretask.lifecycle.core

import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap

object AcceptingFeatureTaskRuntimeWireArtifactValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: FeatureTaskRuntimeWorkflowArtifactMap,
    sourceLabel: String,
  ) = Unit
}
