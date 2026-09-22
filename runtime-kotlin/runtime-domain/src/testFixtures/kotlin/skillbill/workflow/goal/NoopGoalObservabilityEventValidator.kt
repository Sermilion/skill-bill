package skillbill.workflow.goal
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind

object NoopGoalObservabilityEventValidator : GoalObservabilityEventValidator {
  override fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: Any,
    sourceLabel: String,
  ) {
  }
}
