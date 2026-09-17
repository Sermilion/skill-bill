package skillbill.workflow.taskruntime

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidFeatureTaskRuntimeRepairReceiptError
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCheckpointIdentity
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationFieldAdoption
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffEnvelope
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffProjection
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeOperatorBlockRetry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQuarantineEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairReceipt
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairReceiptDecodeObservations
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairReceiptDecoded
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateProgress
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.featureTaskRuntimeCheckpointIdentitiesFromArtifact
import skillbill.workflow.taskruntime.model.featureTaskRuntimeCheckpointIdentitiesToArtifact
import skillbill.workflow.taskruntime.model.featureTaskRuntimeQuarantineEntriesFromWire
import skillbill.workflow.taskruntime.model.featureTaskRuntimeQuarantineRecordToWire
import skillbill.workflow.taskruntime.model.toArtifactMap
import skillbill.workflow.taskruntime.phaseartifacts.decomposeTerminalFrom
import skillbill.workflow.taskruntime.phaseartifacts.goalContinuationFieldAdoptionFrom
import skillbill.workflow.taskruntime.phaseartifacts.operatorBlockRetryFrom
import skillbill.workflow.taskruntime.phaseartifacts.phaseLedgerFrom
import skillbill.workflow.taskruntime.phaseartifacts.phaseRecordsFrom
import skillbill.workflow.taskruntime.phaseartifacts.resolvedBranchFrom
private fun artifactsMap(artifacts: Any?): Map<String, Any?> = when {
  artifacts == null -> emptyMap()
  else -> JsonCodec.anyToStringAnyMap(artifacts)
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime workflow artifacts must decode to an object.",
    )
}

class FeatureTaskRuntimeWorkflowArtifactMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  internal companion object {
    fun from(raw: Any?): FeatureTaskRuntimeWorkflowArtifactMap = FeatureTaskRuntimeWorkflowArtifactMap(
      JsonCodec.anyToStringAnyMap(raw)
        ?: throw InvalidWorkflowStateSchemaError(
          "Feature-task-runtime workflow artifact entry must decode to an object.",
        ),
    )
  }
}

fun phaseRecordsFromWorkflowArtifacts(artifacts: Any?): Map<String, FeatureTaskRuntimePhaseRecord> =
  phaseRecordsFrom(artifactsMap(artifacts))

fun resolvedBranchFromWorkflowArtifacts(artifacts: Any?): FeatureTaskRuntimeResolvedBranch? =
  resolvedBranchFrom(artifactsMap(artifacts))

fun operatorBlockRetryFromWorkflowArtifacts(artifacts: Any?): FeatureTaskRuntimeOperatorBlockRetry? =
  operatorBlockRetryFrom(artifactsMap(artifacts))

fun goalContinuationFieldAdoptionFromWorkflowArtifacts(
  artifacts: Any?,
): FeatureTaskRuntimeGoalContinuationFieldAdoption? = goalContinuationFieldAdoptionFrom(artifactsMap(artifacts))

fun decomposeTerminalFromWorkflowArtifacts(artifacts: Any?): FeatureTaskRuntimeDecomposeTerminal? =
  decomposeTerminalFrom(artifactsMap(artifacts))

fun phaseLedgerFromWorkflowArtifacts(artifacts: Any?): List<FeatureTaskRuntimePhaseLedgerEntry> =
  phaseLedgerFrom(artifactsMap(artifacts))

fun FeatureTaskRuntimeValidationGateProgress.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun FeatureTaskRuntimeValidationGateRunRecord.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun FeatureTaskRuntimeValidationGateRunRecord.presentationWireMap(): FeatureTaskRuntimeWorkflowArtifactMap =
  FeatureTaskRuntimeWorkflowArtifactMap.from(asWorkflowArtifactEntry())

fun FeatureTaskRuntimeRepairLedgerEntry.projectionWireMap(): FeatureTaskRuntimeWorkflowArtifactMap =
  FeatureTaskRuntimeWorkflowArtifactMap.from(toProjectionMap())

fun decodeValidationGateProgressFromArtifact(raw: Any?): FeatureTaskRuntimeValidationGateProgress? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeValidationGateProgress::fromArtifactMap)

internal fun decodeValidationGateProgressFromArtifact(
  raw: Map<String, Any?>,
): FeatureTaskRuntimeValidationGateProgress = FeatureTaskRuntimeValidationGateProgress.fromArtifactMap(raw)

fun FeatureTaskRuntimeValidationGateExecutionEvidence.asWorkflowArtifactEntry(repositoryCheckpoint: String): Any =
  toArtifactMap(repositoryCheckpoint)

fun decodeValidationGateExecutionEvidenceFromArtifact(
  raw: Any?,
  sourceLabel: String,
): FeatureTaskRuntimeValidationGateExecutionEvidence? = JsonCodec.anyToStringAnyMap(raw)?.let {
  FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(it, sourceLabel)
}

internal fun decodeValidationGateExecutionEvidenceFromArtifact(
  raw: Map<String, Any?>,
  sourceLabel: String,
): FeatureTaskRuntimeValidationGateExecutionEvidence =
  FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(raw, sourceLabel)

fun FeatureTaskRuntimeValidationEvidence.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeValidationEvidenceFromArtifact(raw: Any?, sourceLabel: String): FeatureTaskRuntimeValidationEvidence? =
  JsonCodec.anyToStringAnyMap(raw)?.let { FeatureTaskRuntimeValidationEvidence.fromArtifactMap(it, sourceLabel) }

internal fun decodeValidationEvidenceFromArtifact(
  raw: Map<String, Any?>,
  sourceLabel: String,
): FeatureTaskRuntimeValidationEvidence = FeatureTaskRuntimeValidationEvidence.fromArtifactMap(raw, sourceLabel)

fun FeatureTaskRuntimeRepairReceipt.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodeRepairReceiptFromArtifact(raw: Any?, sourceLabel: String): FeatureTaskRuntimeRepairReceipt? =
  JsonCodec.anyToStringAnyMap(raw)?.let { FeatureTaskRuntimeRepairReceipt.fromArtifactMap(it, sourceLabel) }

internal fun decodeRepairReceiptFromArtifact(
  raw: Map<String, Any?>,
  sourceLabel: String,
): FeatureTaskRuntimeRepairReceipt = FeatureTaskRuntimeRepairReceipt.fromArtifactMap(raw, sourceLabel)

fun decodeRepairReceiptFromArtifactWithObservations(
  raw: Any?,
  sourceLabel: String,
): FeatureTaskRuntimeRepairReceiptDecoded? {
  val collector = FeatureTaskRuntimeRepairReceiptDecodeObservations.Collector()
  val receipt = JsonCodec.anyToStringAnyMap(raw)?.let {
    FeatureTaskRuntimeRepairReceipt.fromArtifactMap(it, sourceLabel, collector)
  } ?: return null
  return FeatureTaskRuntimeRepairReceiptDecoded(receipt, collector.finish())
}

internal fun decodeRepairReceiptFromArtifactWithObservations(
  raw: Map<String, Any?>,
  sourceLabel: String,
): FeatureTaskRuntimeRepairReceiptDecoded {
  val collector = FeatureTaskRuntimeRepairReceiptDecodeObservations.Collector()
  val receipt = FeatureTaskRuntimeRepairReceipt.fromArtifactMap(raw, sourceLabel, collector)
  return FeatureTaskRuntimeRepairReceiptDecoded(receipt, collector.finish())
}

fun validateRepairReceiptWireEntries(raw: Any, path: String) {
  FeatureTaskRuntimeRepairReceipt.validateEntries(
    JsonCodec.anyToStringAnyMap(raw)
      ?: throw InvalidFeatureTaskRuntimeRepairReceiptError(
        fieldPath = path,
        reason = "must be an object.",
        payloadFreeReason = "$path must be an object.",
      ),
    path,
  )
}

fun List<FeatureTaskRuntimeQuarantineEntry>.asQuarantineWorkflowArtifactEntry(): Any =
  featureTaskRuntimeQuarantineRecordToWire(this)

fun decodeQuarantineEntriesFromArtifact(raw: Any?): List<FeatureTaskRuntimeQuarantineEntry> =
  featureTaskRuntimeQuarantineEntriesFromWire(raw)

fun List<FeatureTaskRuntimeCheckpointIdentity>.asCheckpointIdentitiesArtifactEntry(): Any =
  featureTaskRuntimeCheckpointIdentitiesToArtifact(this)

fun decodeCheckpointIdentitiesFromArtifact(raw: Any?): List<FeatureTaskRuntimeCheckpointIdentity> =
  featureTaskRuntimeCheckpointIdentitiesFromArtifact(raw)

fun FeatureTaskRuntimeHandoffEnvelope.asWorkflowArtifactEntry(): Any = toEnvelopeMap()

fun decodeHandoffEnvelopeFromArtifact(raw: Any?): FeatureTaskRuntimeHandoffEnvelope? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimeHandoffEnvelope::fromEnvelopeMap)

internal fun decodeHandoffEnvelopeFromArtifact(raw: Map<String, Any?>): FeatureTaskRuntimeHandoffEnvelope =
  FeatureTaskRuntimeHandoffEnvelope.fromEnvelopeMap(raw)

fun FeatureTaskRuntimeHandoffProjection.asWorkflowArtifactEntry(): Any = toEnvelopeMap()

fun FeatureTaskRuntimeRepositoryCheckpoint.asWorkflowArtifactEntry(): Any = toEnvelopeMap()

fun FeatureTaskRuntimePhaseRecord.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseRecordFromArtifact(raw: Any?): FeatureTaskRuntimePhaseRecord? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimePhaseRecord::fromArtifactMap)

internal fun decodePhaseRecordFromArtifact(raw: Map<String, Any?>): FeatureTaskRuntimePhaseRecord =
  FeatureTaskRuntimePhaseRecord.fromArtifactMap(raw)

fun FeatureTaskRuntimePhaseLedgerEntry.asWorkflowArtifactEntry(): Any = toArtifactMap()

fun decodePhaseLedgerEntryFromArtifact(raw: Any?): FeatureTaskRuntimePhaseLedgerEntry? =
  JsonCodec.anyToStringAnyMap(raw)?.let(FeatureTaskRuntimePhaseLedgerEntry::fromArtifactMap)

internal fun decodePhaseLedgerEntryFromArtifact(raw: Map<String, Any?>): FeatureTaskRuntimePhaseLedgerEntry =
  FeatureTaskRuntimePhaseLedgerEntry.fromArtifactMap(raw)
