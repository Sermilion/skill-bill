package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator

object AcceptingFeatureTaskRuntimeWireArtifactValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String) = Unit
}
