package skillbill.application.model

import skillbill.boundary.OpenBoundaryMap

data class WorkflowUpdateRequest(
  val workflowId: String,
  val workflowStatus: String,
  val currentStepId: String = "",
  @OpenBoundaryMap("Caller-supplied JSON patch for workflow step updates")
  val stepUpdates: List<Map<String, Any?>>? = null,
  @OpenBoundaryMap("Caller-supplied JSON patch for durable workflow artifacts")
  val artifactsPatch: Map<String, Any?>? = null,
  val sessionId: String = "",
)

enum class WorkflowFamilyKind {
  IMPLEMENT,
  VERIFY,

  // SKILL-65 Subtask 2: the experimental runtime-driven feature-task pipeline
  // (`FeatureTaskRuntimePhaseWorkflowDefinition`). Distinct from IMPLEMENT and
  // never altering IMPLEMENT/VERIFY storage.
  TASK_RUNTIME,
}
