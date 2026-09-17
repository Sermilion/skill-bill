package skillbill.workflow.engine

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.WorkflowWirePayloadKeys
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.workflow.engine.model.WorkflowDefinition
import skillbill.workflow.engine.model.WorkflowSnapshotView
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowStepState
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.WorkflowStepStatus

internal fun snapshotViewFrom(record: WorkflowStateSnapshot): WorkflowSnapshotView {
  val steps = decodeSteps(record.stepsJson).map { stepMap ->
    val statusWire = stepMap[SharedPayloadKeys.STATUS] as? String
      ?: throw InvalidWorkflowStateSchemaError("Workflow state step status must decode to a string.")
    WorkflowStepState(
      stepId = stepMap[SharedPayloadKeys.STEP_ID] as String,
      status = WorkflowStepStatus.fromWire(statusWire)
        ?: throw InvalidWorkflowStateSchemaError("Workflow state step status has unsupported value '$statusWire'."),
      attemptCount = stepMap[WorkflowWirePayloadKeys.ATTEMPT_COUNT].asExactIntOrNull()
        ?: throw InvalidWorkflowStateSchemaError(
          "Workflow state step attempt_count must decode to an integer.",
        ),
    )
  }
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
    val stepId = update[SharedPayloadKeys.STEP_ID].toString()
    val attemptCount = update[WorkflowWirePayloadKeys.ATTEMPT_COUNT].asExactIntOrNull()
      ?: throw InvalidWorkflowStateSchemaError(
        "step_updates.attempt_count must be an integer >= 0.",
      )
    if (attemptCount < 0) {
      throw InvalidWorkflowStateSchemaError(
        "step_updates.attempt_count must be an integer >= 0.",
      )
    }
    val statusWire = update[SharedPayloadKeys.STATUS]?.toString()
      ?: throw InvalidWorkflowStateSchemaError("step_updates.status must be a non-empty string.")
    val status = WorkflowStepStatus.fromWire(statusWire)
      ?: throw InvalidWorkflowStateSchemaError("step_updates.status has unsupported value '$statusWire'.")
    byStepId[stepId] = workflowStep(stepId, status, attemptCount)
  }
  return definition.stepIds.mapNotNull(byStepId::get)
}

internal fun workflowStep(stepId: String, status: WorkflowStepStatus, attemptCount: Int): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STEP_ID to stepId,
    SharedPayloadKeys.STATUS to status.wireValue,
    WorkflowWirePayloadKeys.ATTEMPT_COUNT to attemptCount,
  )

internal fun jsonString(value: Any?): String = skillbill.contracts.JsonCodec.valueToJsonString(value)
