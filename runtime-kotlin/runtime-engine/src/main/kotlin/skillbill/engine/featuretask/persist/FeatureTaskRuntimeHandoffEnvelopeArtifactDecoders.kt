package skillbill.engine.featuretask.persist

import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseLaunchBriefing
import skillbill.engine.featuretask.phase.core.decodeStrictKeyedArtifactMap
import skillbill.engine.featuretask.phase.core.schemaError
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePersistenceSchemaError
import skillbill.workflow.taskruntime.artifact.decodeDeliveredProjectionRecordFromArtifact
import skillbill.workflow.taskruntime.model.handoff.task.FEATURE_TASK_RUNTIME_INCOMPATIBLE_RECORD_GUIDANCE
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeDeliveredProjectionRecord
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

internal fun phaseBriefingsFrom(
  artifacts: Map<String, Any?>,
  validateEnvelope: (Map<String, Any?>) -> Unit = {},
): Map<String, FeatureTaskRuntimePhaseLaunchBriefing> =
  decodeStrictKeyedArtifactMap(
    artifacts,
    FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY,
    ignoreEntry = { it == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT },
  ) { _, briefingMap ->
    val briefing = FeatureTaskRuntimePhaseLaunchBriefing.fromBriefingArtifactWire(briefingMap)
    validateEnvelope(handoffEnvelopeWireMap(briefingMap))
    briefing
  }

private fun handoffEnvelopeWireMap(briefingMap: Map<String, Any?>): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(briefingMap["handoff_envelope"])
    ?: schemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY' entry must carry a " +
        "'handoff_envelope' object.",
    )

internal fun deliveredProjectionsFrom(
  artifacts: Map<String, Any?>,
  validateEnvelope: (Map<String, Any?>) -> Unit = {},
  validatePersistenceRecord: (Map<String, Any?>) -> Unit = {},
): Map<String, FeatureTaskRuntimeDeliveredProjectionRecord> =
  deliveredProjectionHistoryFrom(artifacts, validateEnvelope, validatePersistenceRecord)
    .values
    .groupBy(FeatureTaskRuntimeDeliveredProjectionRecord::consumerPhaseId)
    .mapValues { (_, records) -> records.maxBy(FeatureTaskRuntimeDeliveredProjectionRecord::iteration) }

internal fun deliveredProjectionHistoryFrom(
  artifacts: Map<String, Any?>,
  validateEnvelope: (Map<String, Any?>) -> Unit = {},
  validatePersistenceRecord: (Map<String, Any?>) -> Unit = {},
): Map<String, FeatureTaskRuntimeDeliveredProjectionRecord> =
  decodeStrictKeyedArtifactMap(
    artifacts,
    FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY,
    ignoreEntry = {
      it == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT ||
        it.split('|').getOrNull(1) == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
    },
  ) { key, recordMap ->
    try {
      validatePersistenceRecord(recordMap)
    } catch (error: InvalidFeatureTaskRuntimePersistenceSchemaError) {
      val consumerPhaseId = recordMap["consumer_phase_id"] as? String ?: "<unknown>"
      throw InvalidFeatureTaskRuntimePersistenceSchemaError(
        sourceLabel = "consumer-phase:$consumerPhaseId/delivered-projection:$key",
        reason = "${error.reason}; $FEATURE_TASK_RUNTIME_INCOMPATIBLE_RECORD_GUIDANCE.",
        cause = error,
      )
    }
    val delivered =
      decodeDeliveredProjectionRecordFromArtifact(recordMap)
        ?: schemaError(
          "Feature-task-runtime delivered projection '$key' is malformed.",
        )
    validateEnvelope(
      JsonCodec.anyToStringAnyMap(recordMap["handoff_envelope"])
        ?: schemaError(
          "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY' entry must " +
            "carry a 'handoff_envelope' object.",
        ),
    )
    delivered
  }
