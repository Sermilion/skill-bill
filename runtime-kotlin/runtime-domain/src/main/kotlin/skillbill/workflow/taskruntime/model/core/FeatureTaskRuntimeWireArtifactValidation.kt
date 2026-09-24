package skillbill.workflow.taskruntime.model.core

import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap

typealias FeatureTaskRuntimeWireArtifactValidation =
  (FeatureTaskRuntimeWireArtifactKind, FeatureTaskRuntimeWorkflowArtifactMap, String) -> Unit
