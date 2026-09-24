package skillbill.workflow
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.artifact.FeatureTaskRuntimeWorkflowArtifactMap

object NoopGoalPlanningPreparationEnvelopeValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: FeatureTaskRuntimeWorkflowArtifactMap,
    sourceLabel: String,
  ) = Unit
}
