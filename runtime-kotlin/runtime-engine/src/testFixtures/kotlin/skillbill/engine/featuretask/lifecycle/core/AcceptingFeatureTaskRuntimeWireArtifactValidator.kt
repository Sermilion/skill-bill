package skillbill.engine.featuretask.lifecycle.core
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator

object AcceptingFeatureTaskRuntimeWireArtifactValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: Any,
    sourceLabel: String,
  ) = Unit
}
