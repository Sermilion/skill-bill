package skillbill.workflow

import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator

object NoopGoalPlanningPreparationEnvelopeValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String) = Unit
}
