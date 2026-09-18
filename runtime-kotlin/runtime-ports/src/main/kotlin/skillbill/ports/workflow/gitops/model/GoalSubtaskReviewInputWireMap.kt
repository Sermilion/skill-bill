package skillbill.ports.workflow.gitops.model

data class GoalSubtaskReviewInputWireMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    internal fun from(map: Map<String, Any?>): GoalSubtaskReviewInputWireMap =
      GoalSubtaskReviewInputWireMap(map.toMap())
  }
}
