package skillbill.workflow.decomposition

import skillbill.workflow.decomposition.model.CurrentSubtaskIntent
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.model.DecompositionStatus
import skillbill.workflow.model.decompositionStatus

fun intentFor(
  subtaskId: Int,
  status: String?,
): CurrentSubtaskIntent =
  when (status.decompositionStatus()) {
    DecompositionStatus.BLOCKED -> CurrentSubtaskIntent(subtaskId = subtaskId, action = "blocked")
    DecompositionStatus.COMPLETE, DecompositionStatus.SKIPPED ->
      CurrentSubtaskIntent(subtaskId = 0, action = "complete")
    DecompositionStatus.IN_PROGRESS -> CurrentSubtaskIntent(subtaskId = subtaskId, action = "resume")
    else -> CurrentSubtaskIntent(subtaskId = subtaskId, action = "start")
  }

fun DecompositionManifest.withParentStatus(): DecompositionManifest {
  val parentStatus =
    when {
      subtasks.all {
        it.status.decompositionStatus() in setOf(DecompositionStatus.COMPLETE, DecompositionStatus.SKIPPED)
      } -> DecompositionStatus.COMPLETE.wireValue
      subtasks.any { it.status.decompositionStatus() == DecompositionStatus.BLOCKED } ->
        DecompositionStatus.BLOCKED.wireValue
      subtasks.any {
        it.status.decompositionStatus() in
          setOf(
            DecompositionStatus.IN_PROGRESS,
            DecompositionStatus.COMPLETE,
            DecompositionStatus.SKIPPED,
          ) || it.hasStarted()
      } -> DecompositionStatus.IN_PROGRESS.wireValue
      else -> DecompositionStatus.PENDING.wireValue
    }
  return copy(status = parentStatus)
}

fun DecompositionManifest.withBlockedSubtask(
  subtaskId: Int,
  reason: String,
  lastResumableStep: String,
): DecompositionManifest =
  copy(
    status = DecompositionStatus.BLOCKED.wireValue,
    currentSubtaskIntent = CurrentSubtaskIntent(subtaskId = subtaskId, action = "resume"),
    subtasks =
      subtasks.map { subtask ->
        if (subtask.id == subtaskId) {
          subtask.copy(
            status = DecompositionStatus.BLOCKED.wireValue,
            blockedReason = reason.ifBlank { "Subtask $subtaskId is blocked." },
            lastResumableStep = lastResumableStep,
          )
        } else {
          subtask
        }
      },
  )

fun DecompositionManifest.withRetriedSubtask(
  subtaskId: Int,
  workflowId: String,
  lastResumableStep: String,
): DecompositionManifest {
  require(subtasks.any { it.id == subtaskId }) {
    "Cannot retry unknown decomposition subtask '$subtaskId'."
  }
  return copy(
    status = DecompositionStatus.IN_PROGRESS.wireValue,
    currentSubtaskIntent = CurrentSubtaskIntent(subtaskId = subtaskId, action = "resume"),
    subtasks =
      subtasks.map { subtask ->
        if (subtask.id == subtaskId) {
          subtask.copy(
            status = DecompositionStatus.IN_PROGRESS.wireValue,
            workflowId = workflowId,
            blockedReason = null,
            lastResumableStep = lastResumableStep,
          )
        } else {
          subtask
        }
      },
  )
}
