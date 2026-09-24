package skillbill.infrastructure.contracts

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.workflow.WorkflowWirePayloadKeys
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.model.WorkflowStatus

object WorkflowStateSnapshotWireMapper {
  fun wireMap(snapshot: WorkflowStateSnapshot): Map<String, Any?> =
    linkedMapOf<String, Any?>(
      SharedPayloadKeys.WORKFLOW_ID to snapshot.workflowId,
      WorkflowWirePayloadKeys.SESSION_ID to snapshot.sessionId.orEmpty(),
      WorkflowWirePayloadKeys.WORKFLOW_NAME to snapshot.workflowName,
      SharedPayloadKeys.CONTRACT_VERSION to snapshot.contractVersion,
      WorkflowWirePayloadKeys.WORKFLOW_STATUS to snapshot.workflowStatus.wireValue,
      WorkflowWirePayloadKeys.CURRENT_STEP_ID to snapshot.currentStepId,
      WorkflowWirePayloadKeys.STEPS to snapshot.steps.map { step ->
        linkedMapOf(
          SharedPayloadKeys.STEP_ID to step.stepId,
          SharedPayloadKeys.STATUS to step.status.wireValue,
          WorkflowWirePayloadKeys.ATTEMPT_COUNT to step.attemptCount,
        )
      },
      WorkflowWirePayloadKeys.ARTIFACTS to snapshot.artifacts,
      WorkflowWirePayloadKeys.STARTED_AT to snapshot.startedAt?.toString().orEmpty(),
      WorkflowWirePayloadKeys.UPDATED_AT to snapshot.updatedAt?.toString().orEmpty(),
      WorkflowWirePayloadKeys.FINISHED_AT to snapshot.finishedAt?.toString().orEmpty(),
    ).apply {
      snapshot.mode?.let { mode -> put(WorkflowWirePayloadKeys.MODE, mode.wireValue) }
    }
}
