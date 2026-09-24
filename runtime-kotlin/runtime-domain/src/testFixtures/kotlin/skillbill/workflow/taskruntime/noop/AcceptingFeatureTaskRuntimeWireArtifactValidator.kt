package skillbill.workflow.taskruntime.noop

import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind

object AcceptingFeatureTaskRuntimeWireArtifactValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: FeatureTaskRuntimeWorkflowArtifactMap,
    sourceLabel: String,
  ) = Unit
}
