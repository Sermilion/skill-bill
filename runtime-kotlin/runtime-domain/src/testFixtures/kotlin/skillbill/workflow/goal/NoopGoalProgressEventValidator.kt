package skillbill.workflow.goal

import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactKind

object NoopGoalProgressEventValidator : GoalProgressEventValidator {
  override fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String) {
  }
}
