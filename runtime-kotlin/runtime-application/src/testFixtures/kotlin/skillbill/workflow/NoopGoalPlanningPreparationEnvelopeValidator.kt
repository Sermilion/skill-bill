package skillbill.workflow
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
object NoopGoalPlanningPreparationEnvelopeValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String) = Unit
}
