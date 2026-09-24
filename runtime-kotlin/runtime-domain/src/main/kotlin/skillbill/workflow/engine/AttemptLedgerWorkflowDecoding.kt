package skillbill.workflow.engine

import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowStepState
import skillbill.workflow.model.WorkflowStepStatus

fun WorkflowStateSnapshot.progressToken(): String =
  listOf(
    workflowId,
    workflowStatus.wireValue,
    currentStepId,
    steps.hashCode().toString(),
    artifacts.hashCode().toString(),
    updatedAt?.toString().orEmpty(),
    finishedAt?.toString().orEmpty(),
  ).joinToString("\n")

fun blockedStepId(
  record: WorkflowStateSnapshot,
  steps: List<WorkflowStepState>,
  requestedStepId: String,
  definitionStepIds: List<String>,
): String =
  requestedStepId.takeIf { stepId ->
    stepId.isNotBlank() &&
      steps.firstOrNull { step -> step.stepId == stepId }?.status == WorkflowStepStatus.RUNNING
  }
    ?: steps.firstOrNull { step -> step.status == WorkflowStepStatus.RUNNING }?.stepId
    ?: firstUnfinishedStepId(steps, definitionStepIds)
    ?: record.currentStepId.takeIf(String::isNotBlank)
    ?: requestedStepId.takeIf(String::isNotBlank)
    ?: "preplan"

fun firstUnfinishedStepId(
  steps: List<WorkflowStepState>,
  definitionStepIds: List<String>,
): String? {
  val statusByStepId = steps.associate { step -> step.stepId to step.status }
  return definitionStepIds.firstOrNull { stepId ->
    statusByStepId[stepId]?.let { status ->
      status != WorkflowStepStatus.COMPLETED && status != WorkflowStepStatus.SKIPPED
    } ?: true
  }
}
