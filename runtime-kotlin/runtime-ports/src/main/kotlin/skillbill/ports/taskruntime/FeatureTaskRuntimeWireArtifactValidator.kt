package skillbill.ports.taskruntime

import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind

interface FeatureTaskRuntimeWireArtifactValidator {
  fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: FeatureTaskRuntimeWorkflowArtifactMap,
    sourceLabel: String,
  )
}
