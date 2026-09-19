package skillbill.workflow.engine

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.workflow.WorkflowWirePayloadKeys
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.workflow.engine.model.WorkflowDefinition
import skillbill.workflow.engine.model.WorkflowSnapshotView
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowStepState
import skillbill.workflow.model.WorkflowStepStatus
internal fun snapshotViewFrom(record: WorkflowStateSnapshot): WorkflowSnapshotView {
  val steps = decodeSteps(record.stepsJson).map(::workflowStepStateFrom)
  return WorkflowSnapshotView(
    workflowId = record.workflowId,
    sessionId = record.sessionId.orEmpty(),
    workflowName = record.workflowName,
    mode = record.mode,
    contractVersion = record.contractVersion,
    workflowStatus = record.workflowStatus,
    currentStepId = record.currentStepId.orEmpty(),
    steps = steps,
    artifacts = decodeObject(record.artifactsJson),
    startedAt = record.startedAt.orEmpty(),
    updatedAt = record.updatedAt.orEmpty(),
    finishedAt = record.finishedAt.orEmpty(),
  )
}

internal fun defaultSteps(definition: WorkflowDefinition, initialStepId: String): List<Map<String, Any?>> {
  var seenInitial = false
  return definition.stepIds.map { stepId ->
    when {
      stepId == initialStepId -> {
        seenInitial = true
        workflowStep(stepId, WorkflowStepStatus.RUNNING, 1)
      }
      definition.openPriorStepsCompleted && !seenInitial -> workflowStep(stepId, WorkflowStepStatus.COMPLETED, 1)
      else -> workflowStep(stepId, WorkflowStepStatus.PENDING, 0)
    }
  }
}

private fun workflowStepStateFrom(stepMap: Map<String, Any?>): WorkflowStepState {
  val statusWire = stepMap[SharedPayloadKeys.STATUS] as? String
    ?: invalidWorkflowStep("Workflow state step status must decode to a string.")
  val status = WorkflowStepStatus.fromWire(statusWire)
    ?: invalidWorkflowStep("Workflow state step status has unsupported value '$statusWire'.")
  val attemptCount = stepMap[WorkflowWirePayloadKeys.ATTEMPT_COUNT].asExactIntOrNull()
    ?: invalidWorkflowStep("Workflow state step attempt_count must decode to an integer.")
  return WorkflowStepState(
    stepId = stepMap[SharedPayloadKeys.STEP_ID] as String,
    status = status,
    attemptCount = attemptCount,
  )
}

internal fun mergeStepUpdates(
  definition: WorkflowDefinition,
  existingSteps: List<Map<String, Any?>>,
  stepUpdates: List<Map<String, Any?>>?,
): List<Map<String, Any?>> {
  if (stepUpdates == null) {
    return existingSteps
  }
  val byStepId = existingSteps.associateByTo(LinkedHashMap()) { it[SharedPayloadKeys.STEP_ID].toString() }
  stepUpdates.forEach { update ->
    val step = workflowStepFromUpdate(update)
    byStepId[step[SharedPayloadKeys.STEP_ID].toString()] = step
  }
  return definition.stepIds.mapNotNull(byStepId::get)
}

private fun workflowStepFromUpdate(update: Map<String, Any?>): Map<String, Any?> {
  val stepId = update[SharedPayloadKeys.STEP_ID].toString()
  val attemptCount = update[WorkflowWirePayloadKeys.ATTEMPT_COUNT].asExactIntOrNull()
    ?: invalidWorkflowStep("step_updates.attempt_count must be an integer >= 0.")
  if (attemptCount < 0) {
    invalidWorkflowStep("step_updates.attempt_count must be an integer >= 0.")
  }
  val statusWire = update[SharedPayloadKeys.STATUS]?.toString()
    ?: invalidWorkflowStep("step_updates.status must be a non-empty string.")
  val status = WorkflowStepStatus.fromWire(statusWire)
    ?: invalidWorkflowStep("step_updates.status has unsupported value '$statusWire'.")
  return workflowStep(stepId, status, attemptCount)
}

private fun invalidWorkflowStep(reason: String): Nothing = throw InvalidWorkflowStateSchemaError(reason)

internal fun workflowStep(stepId: String, status: WorkflowStepStatus, attemptCount: Int): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STEP_ID to stepId,
    SharedPayloadKeys.STATUS to status.wireValue,
    WorkflowWirePayloadKeys.ATTEMPT_COUNT to attemptCount,
  )

internal fun jsonString(value: Any?): String = JsonCodec.valueToJsonString(value)
