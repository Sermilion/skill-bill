package skillbill.infrastructure.contracts

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.WorkflowWirePayloadKeys
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.error.MalformedJsonTextError
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.model.WorkflowStatus

object WorkflowStateSnapshotWireMapper {
  fun wireMap(snapshot: WorkflowStateSnapshot): Map<String, Any?> = linkedMapOf<String, Any?>(
    SharedPayloadKeys.WORKFLOW_ID to snapshot.workflowId,
    WorkflowWirePayloadKeys.SESSION_ID to snapshot.sessionId.orEmpty(),
    WorkflowWirePayloadKeys.WORKFLOW_NAME to snapshot.workflowName,
    SharedPayloadKeys.CONTRACT_VERSION to snapshot.contractVersion,
    WorkflowWirePayloadKeys.WORKFLOW_STATUS to snapshot.workflowStatus.wireValue,
    WorkflowWirePayloadKeys.CURRENT_STEP_ID to snapshot.currentStepId.orEmpty(),
    WorkflowWirePayloadKeys.STEPS to decodeArray(snapshot.stepsJson, "stepsJson"),
    WorkflowWirePayloadKeys.ARTIFACTS to decodeMap(snapshot.artifactsJson, "artifactsJson"),
    WorkflowWirePayloadKeys.STARTED_AT to snapshot.startedAt.orEmpty(),
    WorkflowWirePayloadKeys.UPDATED_AT to snapshot.updatedAt.orEmpty(),
    WorkflowWirePayloadKeys.FINISHED_AT to snapshot.finishedAt.orEmpty(),
  ).apply {
    snapshot.mode?.let { mode -> put(WorkflowWirePayloadKeys.MODE, mode) }
  }

  fun workflowStatusFromWire(raw: String, field: String): WorkflowStatus = WorkflowStatus.fromWire(raw)
    ?: throw InvalidWorkflowStateSchemaError("Workflow state $field has unsupported value '$raw'.")

  private fun decodeArray(rawValue: String, field: String): List<Map<String, Any?>> {
    val parsed = parse(rawValue, field) as? List<*>
      ?: throw InvalidWorkflowStateSchemaError("Workflow state $field must decode to a JSON array.")
    return parsed.mapIndexed { index, entry ->
      JsonCodec.anyToStringAnyMap(entry)
        ?: throw InvalidWorkflowStateSchemaError("Workflow state $field[$index] must decode to a JSON object.")
    }
  }

  private fun decodeMap(rawValue: String, field: String): Map<String, Any?> =
    JsonCodec.anyToStringAnyMap(parse(rawValue, field))
      ?: throw InvalidWorkflowStateSchemaError("Workflow state $field must decode to a JSON object.")

  private fun parse(rawValue: String, field: String): Any? = try {
    JsonCodec.parseValue(rawValue)
  } catch (error: MalformedJsonTextError) {
    throw InvalidWorkflowStateSchemaError("Workflow state $field contains malformed JSON.", error)
  }
}
