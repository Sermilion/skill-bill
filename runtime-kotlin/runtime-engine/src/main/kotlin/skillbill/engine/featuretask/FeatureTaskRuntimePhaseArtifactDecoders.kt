package skillbill.engine.featuretask

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.workflow.taskruntime.decodeDecomposeTerminalFromArtifact
import skillbill.workflow.taskruntime.decodeGoalContinuationFieldAdoptionFromArtifact
import skillbill.workflow.taskruntime.decodePhaseLedgerEntryFromArtifact
import skillbill.workflow.taskruntime.decodePhaseRecordFromArtifact
import skillbill.workflow.taskruntime.decodeResolvedBranchFromArtifact
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_DECOMPOSE_TERMINAL_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_FIELD_ADOPTION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_RESOLVED_BRANCH_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_REVIEW_GENERATION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationFieldAdoption
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeOperatorBlockRetry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch

internal fun schemaError(detail: String): Nothing = throw InvalidWorkflowStateSchemaError(detail)

internal fun <T> decodeStrictKeyedArtifactMap(
  artifacts: Map<String, Any?>,
  artifactKey: String,
  ignoreEntry: (String) -> Boolean = { false },
  decodeEntry: (String, Map<String, Any?>) -> T,
): Map<String, T> {
  val raw = artifacts[artifactKey] ?: return emptyMap()
  val rawMap = raw as? Map<*, *>
    ?: schemaError("Feature-task-runtime artifact '$artifactKey' must decode to a map.")
  return buildMap {
    rawMap.forEach { (key, value) ->
      val phaseId = key as? String
        ?: schemaError("Feature-task-runtime artifact '$artifactKey' must have string keys; found '$key'.")
      if (ignoreEntry(phaseId)) return@forEach
      val entryMap = JsonCodec.anyToStringAnyMap(value)
        ?: schemaError("Feature-task-runtime artifact '$artifactKey' entry for '$phaseId' must decode to a map.")
      put(phaseId, decodeEntry(phaseId, entryMap))
    }
  }
}

internal fun decodePhaseRecords(artifacts: Map<String, Any?>): Map<String, FeatureTaskRuntimePhaseRecord> =
  decodeStrictKeyedArtifactMap(artifacts, FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY) { phaseId, recordMap ->
    decodePhaseRecordFromArtifact(recordMap)
  }

internal fun resolvedBranchFromWorkflowArtifacts(artifacts: Map<String, Any?>): FeatureTaskRuntimeResolvedBranch? {
  val raw = artifacts[FEATURE_TASK_RUNTIME_RESOLVED_BRANCH_ARTIFACT_KEY] ?: return null
  val entryMap = JsonCodec.anyToStringAnyMap(raw)
    ?: schemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_RESOLVED_BRANCH_ARTIFACT_KEY' must decode to a map.",
    )
  return decodeResolvedBranchFromArtifact(entryMap)
}

internal fun reviewGenerationFrom(artifacts: Map<String, Any?>): Int {
  val raw = artifacts[FEATURE_TASK_RUNTIME_REVIEW_GENERATION_ARTIFACT_KEY] ?: return 0
  val ordinal = when (raw) {
    is Int -> raw
    is Long -> raw.toInt()
    is Number -> if (raw.toDouble() == raw.toLong().toDouble()) raw.toInt() else null
    else -> null
  }
  return ordinal?.takeIf { it >= 0 }
    ?: schemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_REVIEW_GENERATION_ARTIFACT_KEY' must decode to a " +
        "non-negative integer; found '$raw'.",
    )
}

internal fun operatorBlockRetryFromWorkflowArtifacts(
  artifacts: Map<String, Any?>,
): FeatureTaskRuntimeOperatorBlockRetry? {
  val raw = artifacts[FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_ARTIFACT_KEY] ?: return null
  val entryMap = JsonCodec.anyToStringAnyMap(raw)
    ?: schemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_ARTIFACT_KEY' must decode to a map.",
    )
  return FeatureTaskRuntimeOperatorBlockRetry(
    phaseId = entryMap.requiredOperatorRetryString(SharedPayloadKeys.PHASE_ID),
    reason = entryMap.requiredOperatorRetryString("reason"),
    retriedAt = entryMap.requiredOperatorRetryString("retried_at"),
  )
}

internal fun goalContinuationFieldAdoptionFromWorkflowArtifacts(
  artifacts: Map<String, Any?>,
): FeatureTaskRuntimeGoalContinuationFieldAdoption? {
  val raw = artifacts[FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_FIELD_ADOPTION_ARTIFACT_KEY] ?: return null
  val entryMap = JsonCodec.anyToStringAnyMap(raw)
    ?: schemaError(
      "Feature-task-runtime artifact " +
        "'$FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_FIELD_ADOPTION_ARTIFACT_KEY' must decode to a map.",
    )
  return decodeGoalContinuationFieldAdoptionFromArtifact(entryMap)
}

private fun Map<String, Any?>.requiredOperatorRetryString(field: String): String =
  (this[field] as? String)?.takeIf(String::isNotBlank)
    ?: schemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_OPERATOR_BLOCK_RETRY_ARTIFACT_KEY' field " +
        "'$field' must decode to a non-blank string.",
    )

internal fun decomposeTerminalFromWorkflowArtifacts(
  artifacts: Map<String, Any?>,
): FeatureTaskRuntimeDecomposeTerminal? {
  val raw = artifacts[FEATURE_TASK_RUNTIME_DECOMPOSE_TERMINAL_ARTIFACT_KEY] ?: return null
  val entryMap = JsonCodec.anyToStringAnyMap(raw)
    ?: schemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_DECOMPOSE_TERMINAL_ARTIFACT_KEY' must decode to a map.",
    )
  return decodeDecomposeTerminalFromArtifact(entryMap)
}

internal fun decodePhaseLedger(artifacts: Map<String, Any?>): List<FeatureTaskRuntimePhaseLedgerEntry> {
  val raw = artifacts[FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY] ?: return emptyList()
  val rawList = raw as? List<*>
    ?: schemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY' must decode to a list.",
    )
  return rawList.map { item ->
    val entryMap = JsonCodec.anyToStringAnyMap(item)
      ?: schemaError(
        "Feature-task-runtime phase ledger entry must decode to a string-keyed map.",
      )
    decodePhaseLedgerEntryFromArtifact(entryMap)
  }
}
