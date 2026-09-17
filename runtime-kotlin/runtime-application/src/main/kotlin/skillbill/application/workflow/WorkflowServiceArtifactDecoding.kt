package skillbill.application.workflow

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.taskruntime.decodePhaseLedgerEntryFromArtifact
import skillbill.workflow.taskruntime.decodePhaseRecordFromArtifact
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord

fun decodeWorkflowArtifacts(artifactsJson: String): DurableWorkflowArtifacts =
  DurableWorkflowArtifacts.fromJson(artifactsJson)

fun decodeFeatureTaskRuntimePhaseRecords(
  artifacts: DurableWorkflowArtifacts,
): Map<String, FeatureTaskRuntimePhaseRecord> {
  val raw = JsonCodec.anyToStringAnyMap(artifacts[FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY])
    ?: return emptyMap()
  return raw.mapValues { (_, value) ->
    val entry = JsonCodec.anyToStringAnyMap(value)
      ?: throw InvalidWorkflowStateSchemaError(
        "Feature-task-runtime phase record entry is malformed.",
      )
    requireNotNull(decodePhaseRecordFromArtifact(entry))
  }
}

object FeatureTaskRuntimePhaseLedgerDecoder {
  fun decode(artifacts: DurableWorkflowArtifacts): List<FeatureTaskRuntimePhaseLedgerEntry> {
    if (FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY !in artifacts) return emptyList()
    val raw = artifacts[FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY] as? List<*>
      ?: invalid("must decode to a JSON array")
    return raw.map { value ->
      val entry = JsonCodec.anyToStringAnyMap(value) ?: invalid("contains a malformed entry")
      requireNotNull(decodePhaseLedgerEntryFromArtifact(entry))
    }
  }

  private fun invalid(reason: String, cause: Throwable? = null): Nothing = throw InvalidWorkflowStateSchemaError(
    "Workflow artifact '$FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY' $reason.",
    cause,
  )
}
