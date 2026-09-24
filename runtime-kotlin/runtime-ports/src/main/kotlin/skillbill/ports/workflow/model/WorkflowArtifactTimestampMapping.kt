package skillbill.ports.workflow.model

import skillbill.contracts.workflow.workflow.WorkflowTimestampPayloadKeys
import skillbill.contracts.workflow.workflow.WorkflowWirePayloadKeys
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.model.GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY
import skillbill.workflow.goal.model.GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY
import skillbill.workflow.goal.model.GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY
import skillbill.workflow.goal.model.GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY
import skillbill.workflow.goal.model.GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.time.parsePersistedInstant

private val timestampArtifactKeys = setOf(
  FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY,
  FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY,
  GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY,
  GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY,
  GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY,
  GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY,
  GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY,
)
private val timestampFieldKeys = setOf(
  WorkflowTimestampPayloadKeys.TIMESTAMP,
  WorkflowTimestampPayloadKeys.FIRST_STARTED_AT,
  WorkflowWirePayloadKeys.STARTED_AT,
  WorkflowWirePayloadKeys.FINISHED_AT,
)

internal fun preserveArtifactTimestampText(
  artifacts: Map<String, Any?>,
  source: Map<String, Any?>?,
): Map<String, Any?> = artifacts.mapValues { (key, value) ->
  if (key in timestampArtifactKeys) preserveTimestampFields(value, source?.get(key)) else value
}

private fun preserveTimestampFields(value: Any?, source: Any?): Any? = when {
  value is Map<*, *> && source is Map<*, *> -> value.entries.associateTo(linkedMapOf()) { (key, item) ->
    key to if (key in timestampFieldKeys && item is String && source[key] is String) {
      preserveTimestampText(item, source[key] as String)
    } else {
      preserveTimestampFields(item, source[key])
    }
  }
  value is List<*> && source is List<*> -> value.mapIndexed { index, item ->
    preserveTimestampFields(item, source.getOrNull(index))
  }
  else -> value
}

private fun preserveTimestampText(value: String, source: String): String {
  return try {
    if (parsePersistedInstant(value) == parsePersistedInstant(source)) source else value
  } catch (error: IllegalArgumentException) {
    throw InvalidWorkflowStateSchemaError("Workflow artifact contains an invalid timestamp.", error)
  }
}
